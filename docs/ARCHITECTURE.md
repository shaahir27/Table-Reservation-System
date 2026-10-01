# System Architecture & Design Specification

This document details the multi-tiered architecture, threading model, and design patterns utilized in the **Restaurant Table Reservation System (RTRS)**.

---

## 1. Multi-Tier Layer Architecture

The application strictly enforces a unidirectional data flow and clean separation of concerns:
$$\text{UI Layer} \longrightarrow \text{Service Layer} \longrightarrow \text{DAO Layer} \longrightarrow \text{Database Connection} \longrightarrow \text{Oracle Database}$$

```mermaid
graph TD
    subgraph Presentation ["Presentation Layer (Swing / FlatLaf)"]
        UI_MF[MainFrame & Sidebar]
        UI_P[DashboardPanel / NewReservationPanel / ReservationsPanel / CustomerPanel / TablePanel / ReportsPanel]
        UI_C[StatCard / TableCard / TableGridPanel / DateSelector / BarChartPanel / Toast]
    end

    subgraph Service ["Business & Transaction Layer"]
        SVC_R[ReservationService]
        SVC_C[CustomerService]
        SVC_T[TableService]
        SVC_REP[ReportService]
        VAL[ValidationUtil]
    end

    subgraph DataAccess ["Data Access Layer (JDBC / SQL)"]
        DAO_R[ReservationDAO]
        DAO_C[CustomerDAO]
        DAO_T[TableDAO]
        DAO_S[TimeSlotDAO]
        DAO_REP[ReportDAO]
    end

    subgraph Infrastructure ["Infrastructure & Utilities"]
        DB[DatabaseConnection]
        ERR[OracleErrorMapper]
        LOG[AppLog]
        ASYNC[Ui.async Worker]
        EXC[AppException Hierarchy]
    end

    subgraph Engine ["Database Engine (Oracle XE)"]
        ORACLE[(Oracle Database 11g/18c/21c)]
        IDX[Unique Index: UQ_ACTIVE_BOOKING]
        TRG[Trigger: TRG_CHECK_CAPACITY]
    end

    UI_P --> SVC_R & SVC_C & SVC_T & SVC_REP
    UI_P -.-> ASYNC
    SVC_R & SVC_C & SVC_T & SVC_REP --> VAL
    SVC_R & SVC_C & SVC_T & SVC_REP --> DAO_R & DAO_C & DAO_T & DAO_S & DAO_REP
    DAO_R & DAO_C & DAO_T & DAO_S & DAO_REP --> DB
    DAO_R & DAO_C & DAO_T & DAO_S & DAO_REP -.-> ERR
    DB --> ORACLE
    ORACLE --> IDX & TRG
```

---

## 2. Layer Descriptions

### 1. Presentation Layer (`ui`, `ui.components`)
The presentation layer provides the desktop user experience built purely with Java Swing, styled with the FlatLaf Light Look-and-Feel and the Inter typography family. It consists of `MainFrame` hosting a fixed-width left navigation sidebar and a `CardLayout` content area with 6 dedicated functional panels:
- `DashboardPanel`: Metric cards, quick booking CTA, and upcoming seatings feed.
- `NewReservationPanel`: 3-step streamlined booking flow with interactive visual table layout.
- `ReservationsPanel`: Multi-criteria search, cancellation, and completion operations.
- `CustomerPanel`: Master data management with debounced live search and inline field validation.
- `TablePanel`: Table inventory CRUD and visual spatial Floor View.
- `ReportsPanel`: Tabular business intelligence with custom `Graphics2D` bar chart visualization.

**Rule:** UI classes **never** contain SQL statements and **never** import `java.sql.*`. All asynchronous DB interactions are dispatched off the Swing Event Dispatch Thread (EDT) via `Ui.async`.

### 2. Business & Service Layer (`service`, `util.ValidationUtil`)
The service layer encapsulates business logic, data validation, party-to-table capacity algorithms, and transactional boundaries:
- `ReservationService`: Implements table recommendations (`recommendTable`), slot conflict pre-checks, pessimistic row locking (`SELECT ... FOR UPDATE`), and transactional atomic commits with rollback safety.
- `CustomerService` & `TableService`: Validate domain rules and manage entity lifecycles while enforcing foreign key delete protection.
- `ReportService`: Validates analytical date intervals and coordinates operational reporting queries.
- `ValidationUtil`: Pure, unit-tested static validators collecting field-level errors into domain exceptions.

### 3. Data Access Layer (`dao`)
The DAO layer is the exclusive location where SQL queries reside. Every interaction uses `PreparedStatement` with bound parameters to guarantee SQL injection immunity. DAOs implement the `CrudDAO<T, ID>` generic interface for entity persistence and map JDBC `ResultSet` records to clean domain POJOs.

### 4. Infrastructure & Database Connectivity (`db`, `util`, `exception`)
- `DatabaseConnection`: Thread-safe JDBC factory that reads credentials securely from externalized `.env` or `config.properties` files with a 5-second connection timeout.
- `OracleErrorMapper`: Translates vendor-specific `ORA-` error codes (`ORA-00001`, `ORA-02292`, `ORA-20001`, `ORA-12541`) into friendly domain exceptions (`BookingConflictException`, `ReferenceInUseException`, `DatabaseUnavailableException`).
- `AppLog`: Configured `java.util.logging` subsystem recording system events and stack traces to `logs/app.log`.

### 5. Database Engine Layer (Oracle)
Oracle Database provides the ultimate transactional consistency and double-booking prevention via the function-based unique index `UQ_ACTIVE_BOOKING` and table triggers.

---

## 3. Concurrency & Double-Booking Prevention Model

When two users concurrently attempt to book the exact same table, date, and seating slot:

```mermaid
sequenceDiagram
    autonumber
    actor User A as Cashier 1 (User A)
    actor User B as Cashier 2 (User B)
    participant Svc as ReservationService
    participant TableDAO as TableDAO
    participant ResDAO as ReservationDAO
    participant DB as Oracle Database Engine

    par Concurrent Booking Requests
        User A->>Svc: bookTable(Cust1, T05, Date, Slot)
        User B->>Svc: bookTable(Cust2, T05, Date, Slot)
    end

    Note over Svc,DB: Connection A & B start SERIALIZABLE transactions (autoCommit = false)

    Svc->>TableDAO: lockTable(ConA, TableId=5)
    TableDAO->>DB: SELECT table_id FROM restaurant_table WHERE table_id = 5 FOR UPDATE
    activate DB
    Note over DB: Lock granted to ConA. Row T05 is LOCKED.
    
    Svc->>TableDAO: lockTable(ConB, TableId=5)
    TableDAO->>DB: SELECT table_id FROM restaurant_table WHERE table_id = 5 FOR UPDATE
    Note over DB: ConB WAITS on table row lock held by ConA

    Svc->>ResDAO: isSlotTaken(ConA, T05, Date, Slot)
    ResDAO->>DB: Query existing active bookings
    DB-->>ResDAO: FALSE (Table is free)

    Svc->>ResDAO: createReservation(ConA, ResA)
    ResDAO->>DB: INSERT INTO reservation (...)
    DB-->>ResDAO: Success (Seq generated)
    Svc->>DB: commit() (ConA)
    deactivate DB
    Note over DB: ConA committed. Lock on T05 RELEASED.
    Svc-->>User A: Reservation RES1025 Confirmed!

    activate DB
    Note over DB: ConB lock granted. ConB resumes execution.
    Svc->>ResDAO: isSlotTaken(ConB, T05, Date, Slot)
    ResDAO->>DB: Query existing active bookings
    DB-->>ResDAO: TRUE (Slot was booked by ConA!)
    deactivate DB

    Svc->>DB: rollback() (ConB)
    Svc-->>User B: BookingConflictException: "Table T05 is already reserved..."
```

**Engine-Level Failsafe:** Even if application pre-checks or `FOR UPDATE` locks were bypassed (e.g., via direct raw SQL inserts), Oracle's `UQ_ACTIVE_BOOKING` unique index immediately raises `ORA-00001`, which `OracleErrorMapper` maps directly to `BookingConflictException`.
