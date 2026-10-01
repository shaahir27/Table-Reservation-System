# Restaurant Table Reservation System — Implementation Spec (Project 13)

> **Audience:** an AI coding agent. Follow this file literally. Where it conflicts with any earlier brief, **this file wins**.
> **Course:** Object-Oriented Programming Using Java and Database Technology Laboratory — standalone Java application with database connectivity.

---

## 0. Agent protocol

1. **This file is delivered whole, every time.** The user will tell you which phase to build, e.g. *"Complete Phase 2"*. Build **only that phase** (Section 12). Never start the next phase, and never "pre-build" later screens beyond the placeholder stubs Phase 1 creates.
   - Before coding, check that all earlier phases exist in the repo (files listed in Section 12). If something from an earlier phase is missing or broken, fix that first and say so.
   - The user is the only one who can run Oracle. After finishing, give a **Phase Report**: files created/changed, exact commands to verify the checkpoint, and anything the user must do manually (e.g. run a SQL script).
   - Then **stop**. Do not continue into the next phase.
2. After each phase, state what you built, how you verified it, and what you assumed. Do not ask questions unless you are blocked; record assumptions in `docs/ASSUMPTIONS.md`.
3. Never rewrite working code to "improve style" in a later phase. Add, don't churn.
4. If a Maven dependency version below does not resolve, use the latest stable version from Maven Central and note it.
5. Everything the user sees must feel **premium and calm**: consistent spacing, no raw exceptions, no unstyled default Swing widgets. See Section 9.

## 1. Problem statement (verbatim, Project 13)

> Build a restaurant reservation system that allows customers to book tables for specific time slots through a Java Swing/AWT interface. JDBC will manage customer, table, and reservation records, while Oracle Database will handle double-booking prevention. Prerequisite: Perform database normalization as necessary and justify.

**Non-negotiable stack:** Java 17, Java Swing (AWT classes such as `Graphics2D`, `Color`, events are used as needed), JDBC, Oracle Database (XE 18c/21c assumed; code must also work on 11g XE). No web app, no ORM, no MySQL, no Spring.

**Central idea:** time-slot-based table reservation. **Central database concept:** Oracle itself rejects double bookings, even if the Java code is bypassed.

## 2. Deliberate changes from the original brief (and why)

| # | Original brief | This spec | Reason |
|---|---|---|---|
| 1 | 14 overlapping 30-min slots (12:00, 12:30, 01:00…) | **5 fixed 90-minute, non-overlapping seatings** stored in a `TIME_SLOT` table | With 30-min labels the same table could be booked 7:00 and 7:30, which is physically impossible. An examiner can catch this. Seatings remove the overlap problem without needing complex range-overlap logic. Slots are **data, not code**: more can be added by an `INSERT`. |
| 2 | `time_slot VARCHAR2` inside `RESERVATION` | `slot_id` FK → `TIME_SLOT` | Referential integrity; slot times live in one place (cleaner 3NF story). |
| 3 | "COMPLETED must not block future use" | **CONFIRMED and COMPLETED both occupy the slot; only CANCELLED frees it** | A completed booking for date D / slot S still happened. Future dates differ anyway, so there is no real conflict to avoid. The brief's rule was ambiguous. |
| 4 | Separate "Reservations" and "Reservation History" screens | **One Reservations screen** (search, filters, cancel, mark completed) | They were the same screen described twice. Fewer screens = fewer bugs. |
| 5 | Separate "Visual Table Layout", "Table Availability" screen and a JTable list in New Reservation | **The visual table cards ARE the table picker** in New Reservation; the same component powers a "Floor view" tab in Tables | One reusable component instead of three features. Much better UX than a list plus a `Select:` dropdown. |
| 6 | Free-text date field `20-09-2026` | Quick date chips (Today / Tomorrow / +2…) + a `JSpinner` date picker | Free-text dates are error-prone and feel unpolished. No third-party date library needed. |
| 7 | Default Swing look | **FlatLaf + Inter font** (look-and-feel jars only; still 100 % Swing) | Biggest single "premium" upgrade. Default Metal L&F looks dated. |
| 8 | Raw `JOptionPane` for everything | Non-blocking **toast** for success, dialog only for booking confirmation and destructive confirmations | Brief itself says "avoid unnecessary popups". |
| 9 | Plain DB check + Java check | Adds `SELECT … FOR UPDATE` on the table row inside the booking transaction, plus a DB trigger for capacity | Shows real concurrency control. The **unique index is still the actual guarantee**; the re-check is only for a friendly error. |
| 10 | Delete customers/tables freely | Delete is **blocked with a friendly message if reservations exist** (FK). Tables can be set to `INACTIVE` instead | Prevents raw `ORA-02292`, preserves history. |
| 11 | "COMPLETED" status with no way to set it | **Mark Completed** button (only for CONFIRMED bookings whose slot has started) | Otherwise the status is dead code. |

## 3. Project layout

Maven project, Java 17.

```
restaurant-reservation/
├── pom.xml
├── config.properties.example        # committed
├── config.properties                # NOT committed (.gitignore)
├── .gitignore
├── README.md
├── run.bat / run.sh                 # java -jar target/rrs.jar
├── sql/
│   ├── 01_schema.sql
│   ├── 02_seed.sql
│   ├── 03_drop_all.sql
│   └── 04_double_booking_demo.sql   # raw-Oracle proof, two-session script
├── docs/
│   ├── NORMALIZATION.md  ER.md  ARCHITECTURE.md  TEST_CASES.md  ASSUMPTIONS.md
└── src/main/java/
    ├── app/Main.java
    ├── db/DatabaseConnection.java
    ├── model/        Customer, RestaurantTable, Reservation, ReservationDetail,
    │                 TimeSlot, TableAvailability, DashboardStats,
    │                 ReservationStatus (enum), TableStatus (enum), TableState (enum)
    ├── dao/          CustomerDAO, TableDAO, ReservationDAO, TimeSlotDAO, ReportDAO
    ├── service/      CustomerService, TableService, ReservationService, ReportService
    ├── exception/    AppException (base, extends RuntimeException)
    │                 ValidationException, BookingConflictException,
    │                 DatabaseUnavailableException, DataAccessException,
    │                 ReferenceInUseException
    ├── util/         ValidationUtil, OracleErrorMapper, Ui (async helper), AppLog
    └── ui/
        ├── MainFrame, Sidebar, Theme
        ├── DashboardPanel, CustomerPanel, TablePanel, NewReservationPanel,
        │   ReservationsPanel, ReportsPanel
        └── components/ StatCard, TableCard, TableGridPanel, WrapLayout,
                        StatusPillRenderer, Toast, DateSelector, SearchableComboBox,
                        BookingConfirmedDialog, AddCustomerDialog, BarChartPanel
```

**Dependencies (`pom.xml`):**
- `com.oracle.database.jdbc:ojdbc8` (21.x; works with 11g–21c)
- `com.formdev:flatlaf` and `com.formdev:flatlaf-fonts-inter`
- `org.junit.jupiter:junit-jupiter` (test scope)
- `maven-shade-plugin` → runnable `target/rrs.jar` (main class `app.Main`).

**Architecture rule:** `ui → service → dao → db → Oracle`. **UI classes never contain SQL and never import `java.sql`.** DAOs contain all SQL. Services own validation, business rules and transactions.

**OOP showcase (needed for viva — implement deliberately, don't force):**
- Encapsulation: private fields, getters/setters, `equals/hashCode/toString` on models.
- Enums: `ReservationStatus {CONFIRMED, CANCELLED, COMPLETED}`, `TableStatus {AVAILABLE, MAINTENANCE, INACTIVE}`, `TableState {AVAILABLE, RESERVED, MAINTENANCE}`.
- Interface: `Searchable<T>` or `CrudDAO<T, ID>` generic interface implemented by `CustomerDAO` and `TableDAO` (polymorphism + generics).
- Inheritance: `AppException` hierarchy; `BasePanel extends JPanel` (shared header + padding) for all screens.
- Custom exceptions + try-with-resources + checked→unchecked translation.
- Composition: `ReservationService` has-a `ReservationDAO`, `TableDAO`, `CustomerDAO`, `TimeSlotDAO`.

## 4. Database (Oracle)

### 4.1 `sql/01_schema.sql`

```sql
-- Sequences
CREATE SEQUENCE customer_seq    START WITH 1    INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE table_seq       START WITH 1    INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE reservation_seq START WITH 1001 INCREMENT BY 1 NOCACHE;   -- gives RES1001, RES1002...

CREATE TABLE customer (
    customer_id   NUMBER(10)    CONSTRAINT pk_customer PRIMARY KEY,
    customer_name VARCHAR2(100) NOT NULL,
    phone         VARCHAR2(15)  NOT NULL CONSTRAINT uq_customer_phone UNIQUE,
    email         VARCHAR2(100),
    CONSTRAINT ck_customer_email CHECK (email IS NULL OR email LIKE '%_@_%.__%')
);

CREATE TABLE restaurant_table (
    table_id     NUMBER(10)   CONSTRAINT pk_rtable PRIMARY KEY,
    table_number VARCHAR2(10) NOT NULL CONSTRAINT uq_table_number UNIQUE,
    capacity     NUMBER(3)    NOT NULL,
    location     VARCHAR2(50) NOT NULL,
    status       VARCHAR2(20) DEFAULT 'AVAILABLE' NOT NULL,
    CONSTRAINT ck_table_capacity CHECK (capacity BETWEEN 1 AND 20),
    CONSTRAINT ck_table_status   CHECK (status IN ('AVAILABLE','MAINTENANCE','INACTIVE'))
);

CREATE TABLE time_slot (
    slot_id    NUMBER(3)    CONSTRAINT pk_time_slot PRIMARY KEY,
    slot_label VARCHAR2(20) NOT NULL CONSTRAINT uq_slot_label UNIQUE,   -- '07:30 PM'
    start_time VARCHAR2(5)  NOT NULL,                                   -- '19:30' (24h, sortable)
    end_time   VARCHAR2(5)  NOT NULL                                    -- '21:00'
);

CREATE TABLE reservation (
    reservation_id   NUMBER(10)  CONSTRAINT pk_reservation PRIMARY KEY,
    customer_id      NUMBER(10)  NOT NULL,
    table_id         NUMBER(10)  NOT NULL,
    reservation_date DATE        NOT NULL,
    slot_id          NUMBER(3)   NOT NULL,
    guest_count      NUMBER(3)   NOT NULL,
    status           VARCHAR2(20) DEFAULT 'CONFIRMED' NOT NULL,
    created_at       TIMESTAMP   DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_res_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT fk_res_table    FOREIGN KEY (table_id)    REFERENCES restaurant_table(table_id),
    CONSTRAINT fk_res_slot     FOREIGN KEY (slot_id)     REFERENCES time_slot(slot_id),
    CONSTRAINT ck_res_guests   CHECK (guest_count > 0),
    CONSTRAINT ck_res_status   CHECK (status IN ('CONFIRMED','CANCELLED','COMPLETED')),
    -- Oracle DATE carries a time part; force midnight so the unique index below cannot be dodged
    CONSTRAINT ck_res_date_midnight CHECK (reservation_date = TRUNC(reservation_date))
);

CREATE INDEX idx_res_customer ON reservation(customer_id);
CREATE INDEX idx_res_date     ON reservation(reservation_date);

-- ============ DOUBLE-BOOKING PREVENTION (THE KEY FEATURE) ============
-- Function-based UNIQUE index. Rows with status CANCELLED produce NULL for all three
-- key parts; Oracle does not index all-NULL keys, so any number of cancelled rows may
-- coexist, while at most ONE active (CONFIRMED/COMPLETED) row can exist per
-- (table, date, slot). Enforced by the database engine, independent of Java.
CREATE UNIQUE INDEX uq_active_booking ON reservation (
    CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN table_id         END,
    CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN reservation_date END,
    CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN slot_id          END
);

-- Cross-table rule a CHECK constraint cannot express: guests <= capacity, table bookable
CREATE OR REPLACE TRIGGER trg_reservation_rules
BEFORE INSERT OR UPDATE OF table_id, guest_count ON reservation
FOR EACH ROW
DECLARE
    v_capacity NUMBER;
    v_status   VARCHAR2(20);
BEGIN
    SELECT capacity, status INTO v_capacity, v_status
      FROM restaurant_table WHERE table_id = :NEW.table_id;

    IF :NEW.guest_count > v_capacity THEN
        RAISE_APPLICATION_ERROR(-20001, 'Guest count exceeds table capacity');
    END IF;
    IF INSERTING AND v_status <> 'AVAILABLE' THEN
        RAISE_APPLICATION_ERROR(-20002, 'Table is not available for booking');
    END IF;
END;
/

INSERT INTO time_slot VALUES (1, '12:00 PM', '12:00', '13:30');
INSERT INTO time_slot VALUES (2, '01:30 PM', '13:30', '15:00');
INSERT INTO time_slot VALUES (3, '06:00 PM', '18:00', '19:30');
INSERT INTO time_slot VALUES (4, '07:30 PM', '19:30', '21:00');
INSERT INTO time_slot VALUES (5, '09:00 PM', '21:00', '22:30');
COMMIT;
```

### 4.2 `sql/02_seed.sql` (demo data; dates are relative so the demo always works)

- 6 customers (e.g. Arun, Priya, Rahul Kumar, Meena, Karthik, Divya) with distinct 10-digit phones (Indian mobile format, start 6–9).
- 8 tables: `T01`(2, Indoor), `T02`(4, Indoor), `T03`(4, Window), `T04`(6, Outdoor), `T05`(8, Hall), `T06`(2, Window), `T07`(4, Outdoor, status `MAINTENANCE`), `T08`(10, Hall).
- ~8 reservations using `TRUNC(SYSDATE)+n` dates, including: some for today, some tomorrow, one **CANCELLED**, one **CONFIRMED yesterday** (so "Mark Completed" can be demoed), one already COMPLETED.
- **Do NOT seed T05 on tomorrow at 07:30 PM** — that is the live demo slot.
- Use sub-selects for FKs (`(SELECT customer_id FROM customer WHERE phone='…')`), sequences for IDs.

### 4.3 `sql/04_double_booking_demo.sql`

Two-session script with comments: Session A inserts T05/tomorrow/07:30 PM and commits; Session B repeats the insert and gets `ORA-00001: unique constraint (…UQ_ACTIVE_BOOKING) violated`; then Session A sets `status='CANCELLED'`; Session B's insert now succeeds. This proves the rule works with no Java involved.

### 4.4 Display ID

Reservation display code = `"RES" + reservation_id` (formatted in Java, not stored). Sequence starts at 1001.

## 5. Configuration and connection

`config.properties` (read from the working directory, then fall back to classpath):

```properties
db.url=jdbc:oracle:thin:@//localhost:1521/XEPDB1
# 11g XE alternative: jdbc:oracle:thin:@localhost:1521:XE
db.username=rrs_user
db.password=change_me
```

`DatabaseConnection`:
- Loads properties once (static init). **No credentials in source.**
- `public static Connection getConnection()` returns a **new** connection each call (callers use try-with-resources; transactions are per-connection so never share one static connection).
- `public static void testConnection()` used at startup.
- Translates `SQLException` for connection failures (ORA-12541, 12514, 17002, 01017, 12170) into `DatabaseUnavailableException` with a friendly message.
- Set `DriverManager.setLoginTimeout(5)` so a dead DB doesn't hang the UI.

## 6. Domain model

Plain classes with private fields, no-arg + full constructors, getters/setters, `toString`.

- `Customer(int customerId, String name, String phone, String email)`
- `RestaurantTable(int tableId, String tableNumber, int capacity, String location, TableStatus status)`
- `TimeSlot(int slotId, String label, LocalTime start, LocalTime end)`; `toString()` returns label (used directly in combo boxes).
- `Reservation(int reservationId, int customerId, int tableId, LocalDate date, int slotId, int guestCount, ReservationStatus status, LocalDateTime createdAt)` + `getDisplayCode()`.
- `ReservationDetail` (read model for joined rows): reservationId, displayCode, customerName, phone, tableNumber, date, slotLabel, guestCount, status, createdAt.
- `TableAvailability`: `RestaurantTable table`, `TableState state`, `boolean recommended`.
- `DashboardStats`: todayCount, freeTablesNextSlot, nextSlotLabel, upcomingCount, cancelledCount, `List<ReservationDetail> upcoming`.

Use `java.time` everywhere in Java; convert with `java.sql.Date.valueOf(LocalDate)` at the DAO boundary only.

## 7. DAO layer (all SQL lives here; `PreparedStatement` only — zero string-concatenated user input)

Every DAO method has two forms where transactions matter: one that opens its own connection, and one taking a `Connection` (`createReservation(Connection, Reservation)`), so the service can run a multi-statement transaction.

**CustomerDAO:** `addCustomer`, `getCustomerById`, `getAllCustomers`, `searchCustomers(String q)`, `updateCustomer`, `deleteCustomer`, `existsByPhone`.
Search: `WHERE UPPER(customer_name) LIKE UPPER(?) OR phone LIKE ?` with `%q%` bound as a parameter. Use `prepareStatement(sql, new String[]{"CUSTOMER_ID"})` to get the generated id.

**TableDAO:** `addTable`, `getAllTables`, `getTableById`, `updateTable`, `deleteTable`, `getAvailableTables(date, slotId, guests)`, `getTableStates(date, slotId)`, `lockTable(Connection, tableId)`.

```sql
-- getAvailableTables  (capacity + status + no active booking)
SELECT t.table_id, t.table_number, t.capacity, t.location, t.status
FROM   restaurant_table t
WHERE  t.status = 'AVAILABLE'
AND    t.capacity >= ?
AND    NOT EXISTS (SELECT 1 FROM reservation r
                   WHERE  r.table_id = t.table_id
                   AND    r.reservation_date = ?
                   AND    r.slot_id = ?
                   AND    r.status IN ('CONFIRMED','COMPLETED'))
ORDER  BY t.capacity, t.table_number;

-- getTableStates (powers the visual floor; includes MAINTENANCE, excludes INACTIVE)
SELECT t.table_id, t.table_number, t.capacity, t.location, t.status,
       CASE WHEN r.reservation_id IS NULL THEN 0 ELSE 1 END AS is_reserved
FROM   restaurant_table t
LEFT   JOIN reservation r
       ON  r.table_id = t.table_id AND r.reservation_date = ? AND r.slot_id = ?
       AND r.status IN ('CONFIRMED','COMPLETED')
WHERE  t.status <> 'INACTIVE'
ORDER  BY t.table_number;

-- lockTable (serialises concurrent bookings of the same table)
SELECT table_id FROM restaurant_table WHERE table_id = ? FOR UPDATE;
```

**ReservationDAO:** `createReservation(Connection, Reservation)`, `getReservationById`, `searchReservations(ReservationFilter)`, `getAllReservations`, `isSlotTaken(Connection, tableId, date, slotId)`, `updateReservationStatus(id, status)`, `cancelReservation(id)`, dashboard queries.

```sql
-- INSERT
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (reservation_seq.NEXTVAL, ?, ?, ?, ?, ?, 'CONFIRMED');

-- Joined listing (the JOIN to demonstrate in viva)
SELECT r.reservation_id, c.customer_name, c.phone, t.table_number, r.reservation_date,
       s.slot_label, r.guest_count, r.status, r.created_at
FROM   reservation r
JOIN   customer c         ON c.customer_id = r.customer_id
JOIN   restaurant_table t ON t.table_id    = r.table_id
JOIN   time_slot s        ON s.slot_id     = r.slot_id
WHERE  (? IS NULL OR r.status = ?) ...
ORDER  BY r.reservation_date DESC, s.start_time DESC;
```

`searchReservations` takes a `ReservationFilter` object (text, date nullable, status nullable, tableNumber nullable) and builds the `WHERE` with **fixed SQL fragments + bound parameters** (e.g. append `AND r.status = ?` and add the param to a list — never append user text). Text search matches: numeric reservation id (strip optional `RES` prefix), customer name (`UPPER LIKE`), phone, table number.

Dashboard queries (SYSDATE is DB time; fine for this app):
- Today: `COUNT(*) … reservation_date = TRUNC(SYSDATE) AND status IN ('CONFIRMED','COMPLETED')`
- Upcoming: `status='CONFIRMED' AND (reservation_date > TRUNC(SYSDATE) OR (reservation_date = TRUNC(SYSDATE) AND s.end_time > TO_CHAR(SYSDATE,'HH24:MI')))`
- Cancelled: `status='CANCELLED' AND reservation_date >= TRUNC(SYSDATE)`
- Free tables: computed in the service as `getAvailableTables(date, nextSlot.id, 1).size()` where *next slot* = first slot today whose end time is still in the future, else first slot tomorrow. Card label: "Free at 07:30 PM".
- Upcoming list: top 8 upcoming joined rows ordered by date, `start_time`.

**TimeSlotDAO:** `getAllSlots()` ordered by `start_time`. Cache in the service after first load.

**ReportDAO** (each uses `GROUP BY`/`COUNT`/`ORDER BY`/JOIN):
1. Today's reservations
2. Reservations by date (date range)
3. Reservations by status (`GROUP BY status`)
4. Most frequently booked tables (`GROUP BY table_number ORDER BY COUNT(*) DESC`)
5. Reservation count by time slot (`GROUP BY slot_label ORDER BY start_time`)

Return generic `ReportResult { List<String> columns; List<Object[]> rows; }` so one `JTable` renders any report.

## 8. Service layer

**ReservationService** (the heart):

```java
List<TimeSlot> getTimeSlots();
List<TableAvailability> findAvailableTables(LocalDate date, TimeSlot slot, int guests); // validates, queries, flags recommended
TableAvailability recommendTable(List<RestaurantTable> candidates, int guests);        // pure function, unit-tested
Reservation bookTable(int customerId, int tableId, LocalDate date, TimeSlot slot, int guests);
void cancelReservation(int reservationId);
void markCompleted(int reservationId);
void validateReservation(...);                                                          // throws ValidationException
DashboardStats getDashboardStats();
```

**Recommendation rule:** among available tables with `capacity >= guests`, pick the **smallest capacity**; ties broken by lowest `table_number`. Mark exactly one as `recommended`. Reason text shown in UI: *"Best fit for N guests"*. If none fit: message *"Largest table seats X — please split the party or choose another slot."* (no table joining).

**Validation (`ValidationUtil`, pure static, unit-tested; collects all field errors into one `ValidationException` with a `Map<String,String>` field→message):**
- Customer: name required (2–100 chars, letters/spaces/.'-); phone required, normalised (strip spaces, dashes, leading `+91`/`0`) and must match `^[6-9]\d{9}$`; email optional but must match a sane regex.
- Table: number required (`^[A-Za-z0-9-]{1,10}$`), capacity 1–20, location required.
- Reservation: customer selected; date not null and `>= today`; slot not null; **if date is today, slot start time must be later than now**; guests ≥ 1; guests ≤ chosen table capacity.

**`bookTable` algorithm (implement exactly):**

```java
validateReservation(...);
try (Connection con = DatabaseConnection.getConnection()) {
    con.setAutoCommit(false);
    try {
        tableDAO.lockTable(con, tableId);                    // SELECT ... FOR UPDATE: second booker waits here
        RestaurantTable t = tableDAO.getTableById(con, tableId);
        if (t.getStatus() != TableStatus.AVAILABLE) throw new ValidationException(...);
        if (guests > t.getCapacity())               throw new ValidationException(...);
        if (reservationDAO.isSlotTaken(con, tableId, date, slotId))
            throw new BookingConflictException(tableNumber, date, slot.getLabel());   // friendly pre-check
        Reservation r = reservationDAO.createReservation(con, new Reservation(...)); // Oracle's unique index is the real guard
        con.commit();
        return r;
    } catch (SQLException e) {
        con.rollback();
        throw OracleErrorMapper.map(e, context);              // ORA-00001 -> BookingConflictException
    } catch (RuntimeException e) { con.rollback(); throw e; }
}
```

Explain in a code comment: the re-check gives a clean message; the `FOR UPDATE` serialises concurrent bookers of one table; **the unique index guarantees correctness even if both are removed**.

**`OracleErrorMapper`** (uses `SQLException.getErrorCode()`):
| Code | Meaning | Maps to |
|---|---|---|
| 1 | unique violated (`UQ_ACTIVE_BOOKING` → booking conflict; `UQ_CUSTOMER_PHONE` → "phone already registered"; `UQ_TABLE_NUMBER` → "table number exists") | `BookingConflictException` / `ValidationException` |
| 2292 | child record found | `ReferenceInUseException` ("has reservations; cancel/deactivate instead") |
| 20001 / 20002 | trigger rules | `ValidationException` |
| 12541, 12514, 17002, 1017, 12170 | connection/login | `DatabaseUnavailableException` |
| other | | `DataAccessException` (generic message; full stack to log) |

Conflict message (exact format): `Table T05 is already reserved for 20-09-2026 at 07:30 PM.`

**CustomerService / TableService:** thin; validate then delegate. `TableService.deleteTable` → if referenced, throw `ReferenceInUseException` suggesting "Set status to INACTIVE".

**Cancel:** only `CONFIRMED` → `CANCELLED`; use `UPDATE reservation SET status='CANCELLED' WHERE reservation_id=? AND status='CONFIRMED'` and check row count (0 rows ⇒ "already cancelled or changed"). Never delete rows.
**Mark completed:** only `CONFIRMED` whose date < today, or date = today and slot start ≤ now.

## 9. UI / UX design system ("premium" requirements)

**Look-and-feel bootstrap in `Main` (before any Swing component):**
```java
FlatInterFont.install();
UIManager.put("defaultFont", new Font(FlatInterFont.FAMILY, Font.PLAIN, 14));
FlatLightLaf.setup();
UIManager.put("Component.arc", 12);  UIManager.put("Button.arc", 12);
UIManager.put("TextComponent.arc", 10);  UIManager.put("ScrollBar.thumbArc", 999);
UIManager.put("Table.rowHeight", 38);  UIManager.put("Table.showHorizontalLines", true);
UIManager.put("Table.showVerticalLines", false);
UIManager.put("TableHeader.height", 40);
```
Launch inside `SwingUtilities.invokeLater`.

**`Theme` class (single source of truth for constants):**
| Token | Value |
|---|---|
| Sidebar bg | `#17181C` |
| Sidebar text / active | `#B9BCC4` / `#FFFFFF` with 4px gold bar |
| Accent (gold) | `#C8963E` |
| App background | `#F6F4F0` |
| Card | `#FFFFFF`, 14px radius, subtle 1px border `#E7E3DC` |
| Text primary / muted | `#1E2024` / `#6B7078` |
| Success / Danger / Warning | `#2E7D5B` / `#C0392B` / `#B7791F` |
| Maintenance grey | `#9AA0A6` |
Spacing scale 8 / 12 / 16 / 24 / 32. Page title 26 pt semibold, section titles 16 pt semibold, body 14 pt. Never hard-code colours or fonts outside `Theme`.

**Shell:** `MainFrame` (min 1200×760, centered) = left `Sidebar` (220 px: app name "Reserve", then Dashboard, New Reservation, Reservations, Customers, Tables, Reports) + `CardLayout` content area. Header strip in each panel (`BasePanel`): page title left, today's date right. "New Reservation" is visually emphasised (gold filled button) in the sidebar and on the dashboard.

**Components**
- `StatCard`: big number, label, muted subtext.
- `StatusPillRenderer`: table cell renderer painting rounded pills — CONFIRMED green, COMPLETED blue-grey, CANCELLED red-grey; same for table status.
- `Toast`: bottom-right, auto-dismiss 2.5 s, success/error variants, non-blocking (glass pane or undecorated `JWindow`).
- `TableCard` (custom painted, 150×120): rounded rect, table number large, "N seats", location small. States: **AVAILABLE** (white, green dot, hover lift), **RECOMMENDED** (gold outline + "RECOMMENDED" ribbon), **SELECTED** (gold fill tint + 2 px border), **RESERVED** (greyed, "Booked", not clickable), **MAINTENANCE** (hatched grey, "Maintenance", not clickable). Custom paint with `Graphics2D` + antialiasing.
- `TableGridPanel`: wraps `TableCard`s with `WrapLayout`; fires a selection event; supports `setData(List<TableAvailability>)`.
- `DateSelector`: chips for Today, Tomorrow, then next 4 days (e.g. "Sat 20"), plus a `JSpinner` (`SpinnerDateModel`, format `dd-MM-yyyy`, min = today) labelled "Other date". Exposes `getDate(): LocalDate`.
- `SearchableComboBox` for customers: shows "Name · phone", filters as the user types by name or phone. If this proves fragile, fall back to a plain `JComboBox` plus a search `JTextField` above it.
- Tables everywhere: zebra off, subtle row hover, right-aligned numbers, status pills, empty-state text ("No reservations match your filters") when the model is empty.

**Rules**
- Buttons: one primary (filled gold) per screen; secondary outlined; destructive in danger red. Disable buttons whenever the action is impossible (e.g. *Cancel* with no selection or non-CONFIRMED row).
- Inline validation: show the error message in small red text under the offending field (and a red outline via `FlatClientProperties.OUTLINE = "error"`), not in a popup.
- Destructive actions (delete, cancel) always confirm with a dialog stating exactly what will happen.
- **All DB calls off the EDT** via a tiny helper `Ui.async(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError)` built on `SwingWorker`. While running, disable the triggering button and show "Searching…" / "Booking…" text. Errors route to one central handler that shows a toast/dialog with the exception's friendly message and logs the stack trace.
- Every screen loads data on `componentShown` so counts are never stale.

## 10. Screens

### 10.1 Startup
`Main` shows a small splash/"Connecting to database…" state, runs `DatabaseConnection.testConnection()` async. On failure show a full-window friendly panel: "Can't reach the database", the friendly reason, config hint ("Check config.properties and that Oracle is running"), **Retry** button. No stack trace in UI.

### 10.2 Dashboard
Row of 4 `StatCard`s: **Today's Reservations**, **Free at <next slot>**, **Upcoming Bookings**, **Cancelled**. Below: "Upcoming reservations" table (ID `RES1002`, customer, table, date, slot, guests, status pill). Primary button "+ New Reservation" top right. Refresh after every booking/cancel.

### 10.3 New Reservation (most important screen — must be effortless)
Single scrolling page of three numbered cards, top to bottom:

1. **Who** — `SearchableComboBox` customer + secondary button "+ New customer" → `AddCustomerDialog` (Name, Phone, Email, Save/Cancel); on save the new customer is auto-selected and a toast shows.
2. **When & how many** — `DateSelector`; time slot as a row of **toggle buttons** (one per `TimeSlot`; past slots for today are disabled); guests as a `JSpinner` (1–20) with − / + feel; optional location filter chips (Any / Indoor / Window / Outdoor / Hall, built from distinct DB locations). Primary button **Find available tables**.
3. **Choose a table** — `TableGridPanel` of `TableCard`s (recommended one pre-selected & marked). Summary strip beneath: "T05 · 8 seats · Hall · 20-09-2026 · 07:30 PM · 4 guests". Primary button **Confirm reservation** — disabled until a table is selected. If no tables: friendly empty state suggesting another slot/date.

Rules: step 3 is hidden/dimmed until a search succeeds; **changing any input in step 1 or 2 clears step 3** (prevents booking against stale results). On confirm → `ReservationService.bookTable`. On success open **`BookingConfirmedDialog`** (ticket-style card):

```
RESERVATION CONFIRMED
Reservation ID : RES1025      Customer : Rahul Kumar
Table          : T05          Date     : 20-09-2026
Time           : 07:30 PM     Guests   : 4
Status         : CONFIRMED
[ Done ]   [ Book another ]
```
Then reset the form, refresh dashboard. On `BookingConflictException` show the exact message `Table T05 is already reserved for 20-09-2026 at 07:30 PM.`, **auto-refresh the table grid** so the taken table shows as Booked, and keep the user's other inputs.

### 10.4 Reservations (search, history, cancel)
Filter bar: search box (placeholder "Search by ID, name, phone or table"), optional date (checkbox "Any date" + spinner), status combo (ALL / CONFIRMED / COMPLETED / CANCELLED), table combo (All + table numbers), **Search** and **Reset**. Search also fires on Enter. Results `JTable`: ID, Customer, Phone, Table, Date, Time, Guests, Status (pill). Bottom action bar: **Cancel reservation** (danger), **Mark completed**; enabled only when the selected row is eligible. Confirm cancel with: "Are you sure you want to cancel RES1025 (T05, 20-09-2026, 07:30 PM)? The table will become available again." After cancel: toast + refresh. Footer count: "12 reservations".

### 10.5 Customers
Left: form (Name, Phone, Email) with **Add / Update / Delete / Clear** (Add is primary; Update/Delete disabled until a row is selected). Right: search field (name or phone, live filter with ~300 ms debounce) + `JTable` (ID, Name, Phone, Email). Selecting a row fills the form. Delete blocked with friendly message if the customer has reservations.

### 10.6 Tables
Two tabs:
- **Manage** — form (Table No, Capacity spinner, Location combo-editable, Status combo) + Add/Update/Delete/Clear + `JTable` (ID, Table No, Capacity, Location, Status pill).
- **Floor view** — `DateSelector` + slot toggles → `TableGridPanel` showing every non-inactive table as AVAILABLE / RESERVED / MAINTENANCE for that date+slot. Clicking a table shows a details card (capacity, location, date, slot, and, if reserved, who booked it). This reuses the same `TableCard` component.

### 10.7 Reports
Report picker combo (the 5 reports), optional date range where relevant, **Run** button → `JTable`. For "Reservations by time slot" and "by status" also show a simple custom-painted `BarChartPanel` (Graphics2D, theme colours, value labels). No chart library.

## 11. Cross-cutting

- **Logging:** `java.util.logging` to `logs/app.log` via `AppLog`; log every caught exception with stack trace and context. Users only see friendly messages.
- **Security/data quality:** `PreparedStatement` everywhere; no SQL string-concatenation of user input (reviewers will grep for `+` inside SQL); credentials only in `config.properties`; `.gitignore` excludes `config.properties`, `logs/`, `target/`.
- **Resources:** try-with-resources for `Connection`, `PreparedStatement`, `ResultSet`.
- **Keyboard:** Enter triggers the primary action in forms; tab order is logical; `Esc` closes dialogs.
- **Resizing:** layouts must survive resizing to 1100×700 minimum without clipping (use `GridBagLayout`/`BorderLayout`/`MigLayout-free` plain Swing layouts; no absolute positioning).

## 12. Build phases (5 phases — the user will say "Complete Phase N")

General rules for every phase: obey Sections 3, 9, 11 and 16; keep the layer rule `ui → service → dao → db`; commit-ready code that compiles with `mvn package` at the end of the phase; add JUnit tests only where stated.

### Phase 1 — Foundation, database and application shell
**Build**
- Maven project, `pom.xml`, `.gitignore`, `config.properties.example`, folder structure from Section 3.
- `sql/01_schema.sql`, `sql/02_seed.sql`, `sql/03_drop_all.sql`, `sql/04_double_booking_demo.sql` (Section 4, complete and runnable).
- `db/DatabaseConnection`, `util/AppLog`, `util/OracleErrorMapper`, all `exception/*` classes.
- All `model/*` classes and enums.
- `ui/Theme`, FlatLaf + Inter bootstrap in `app/Main`, `MainFrame`, `Sidebar`, `BasePanel`, `Toast`, `Ui.async`.
- **Placeholder panels** (title + "Coming in a later phase") for Dashboard, New Reservation, Reservations, Customers, Tables, Reports, wired into `CardLayout` and the sidebar.
- Startup DB check with the friendly error/Retry screen (Section 10.1).

**Checkpoint (user verifies):** SQL scripts run cleanly in Oracle; `04_double_booking_demo.sql` shows `ORA-00001` on the second insert and succeeds after cancel; app launches styled; sidebar switches panels; stopping Oracle shows the friendly error screen (Test 12).

### Phase 2 — Customers and Tables (master data)
**Build**
- `util/ValidationUtil` (customer + table rules) with JUnit tests.
- `CustomerDAO`, `CustomerService`, `CustomerPanel` (Section 10.5).
- `TableDAO` (CRUD parts only), `TableService`, `TimeSlotDAO`, `TablePanel` **Manage tab only** (Section 10.6).
- Generic `CrudDAO<T, ID>` interface implemented by both DAOs; `StatusPillRenderer`; inline field validation.
- Friendly handling of duplicate phone / table number and delete-when-in-use (`ReferenceInUseException`).

**Checkpoint:** Tests 1, 2, 10, 14 pass; search by name/phone works; invalid input shows inline red messages; `mvn test` green.

### Phase 3 — Reservation workflow and double-booking prevention (the core)
**Build**
- `TableDAO.getAvailableTables / getTableStates / lockTable`, `ReservationDAO` (create, getById, isSlotTaken, status updates), `ReservationService` (validate, find, recommend, `bookTable` exactly as in Section 8, cancel, markCompleted).
- `NewReservationPanel` (Section 10.3) with `DateSelector`, slot toggle buttons, guest spinner, location chips, `SearchableComboBox`, `TableCard`, `TableGridPanel`, `WrapLayout`, `AddCustomerDialog`, `BookingConfirmedDialog`.
- Conflict handling: exact message, auto-refresh of the grid, form state preserved.
- JUnit tests for `recommendTable` and reservation validation.

**Checkpoint:** Tests 3, 4, 5, 6, 9, 11 and 13 pass. **Run two app instances: booking the same table/date/slot from both gives exactly one success and one friendly conflict message.** Reservation appears in Oracle (`SELECT * FROM reservation`).

### Phase 4 — Operations: reservations list, cancellation, dashboard, reports
**Build**
- `ReservationDAO.searchReservations(ReservationFilter)` and joined listing queries; `ReservationsPanel` (Section 10.4) with filters, status pills, **Cancel** and **Mark Completed** with confirmation dialogs.
- Dashboard queries and `DashboardPanel` with `StatCard`s and the upcoming table (Section 10.2); refresh after every booking/cancel.
- `ReportDAO`, `ReportService`, `ReportsPanel` with all 5 reports rendered in one `JTable` (**no chart yet**).

**Checkpoint:** Tests 7 and 8 pass (cancel → rebook same slot works; cancelled row still in DB); dashboard numbers equal the corresponding SQL counts; search by ID/name/phone/table/date and status filter all work.

### Phase 5 — Polish, Floor view, documentation and delivery
**Build**
- Tables → **Floor view** tab using `TableGridPanel` (Section 10.6); `BarChartPanel` in Reports for slot and status reports.
- Polish pass: empty states, hover/focus states, keyboard (Enter/Esc), resize to 1100×700 without clipping, consistent spacing via `Theme`.
- Final code audit: no SQL string concatenation of user input, no SQL or `java.sql` imports in `ui/`, no `printStackTrace`/`System.out`, all JDBC on background threads.
- Documents: `README.md` (Oracle user creation, running scripts, configuring, build, run), `docs/NORMALIZATION.md`, `docs/ER.md`, `docs/ARCHITECTURE.md`, `docs/TEST_CASES.md`, `docs/ASSUMPTIONS.md` (Section 14), `run.bat`/`run.sh`.

**Checkpoint:** Test 15 passes; all 15 tests recorded in `docs/TEST_CASES.md`; a fresh clone following the README runs; the Definition of Done (Section 15) is fully ticked.

**If time is short:** Phases 1–4 already satisfy every requirement in the problem statement. Phase 5 is polish plus documentation.

## 13. Test cases (record results in `docs/TEST_CASES.md` with expected/actual/pass columns)

| # | Scenario | Expected |
|---|---|---|
| 1 | Create customer | Inserted; appears in list and in Oracle |
| 2 | Create restaurant table | Appears in table list |
| 3 | Valid reservation | Confirmed; dialog with RES id; appears in Reservations |
| 4 | Same table + date + slot again | Rejected: `Table T05 is already reserved for … at …` (also at raw SQL level) |
| 5 | Same table, different slot | Allowed |
| 6 | Same table, different date | Allowed |
| 7 | Cancel reservation | Status `CANCELLED`, row still present |
| 8 | Rebook the cancelled slot | Allowed |
| 9 | Guests > capacity | Rejected (UI never offers the table; service + trigger also reject) |
| 10 | Invalid customer data (empty name, bad phone, bad email) | Inline validation messages |
| 11 | Past date / past slot today | Validation message |
| 12 | Database unavailable | Friendly connection error screen, app does not crash |
| 13 | Concurrent booking from two app instances | One success, one friendly conflict |
| 14 | Delete customer/table having reservations | Friendly "in use" message |
| 15 | MAINTENANCE table | Never offered in Find; shown grey in Floor view |

JUnit (no DB): `ValidationUtil` (phone/email/guests), `recommendTable` (3 guests → 4-seater; ties; none fit).

## 14. Documents the agent must generate

- `docs/NORMALIZATION.md` — use the derivation below as the source of truth.
- `docs/ER.md` — Mermaid `erDiagram` that **exactly matches** `01_schema.sql` (CUSTOMER 1—N RESERVATION N—1 RESTAURANT_TABLE; TIME_SLOT 1—N RESERVATION).
- `docs/ARCHITECTURE.md` — layer diagram (Mermaid) + one paragraph per layer.
- `README.md`, `docs/TEST_CASES.md`, `docs/ASSUMPTIONS.md`.

### Normalization derivation (write this up properly, with examples)

**Unnormalised design (the "bad" table):**
`RESERVATION_FLAT(reservation_id, customer_name, phone, email, table_number, capacity, location, table_status, reservation_date, slot, guests, status)`

**Functional dependencies:**
- `reservation_id → all attributes`
- `phone → customer_name, email` (phone unique per customer)
- `table_number → capacity, location, table_status`
- `slot_label → start_time, end_time`
- Natural candidate key for an active booking: `(table_number, reservation_date, slot)`

**1NF:** all values atomic, no repeating groups (a customer's multiple bookings are separate rows, not a list column; one phone per customer — multiple phones would need their own table). Primary key chosen.

**2NF:** Using the composite key `(phone, table_number, reservation_date, slot)` for illustration, `customer_name` and `email` depend only on `phone`, and `capacity`/`location` only on `table_number` → **partial dependencies**. Decompose into `CUSTOMER`, `RESTAURANT_TABLE`, `RESERVATION`. (Note honestly: with a surrogate single-column key `reservation_id` the flat table is trivially 2NF, so the composite-key view is used to demonstrate the issue.)

**3NF:** Remaining **transitive** dependencies: `reservation_id → table_number → capacity, location`; `reservation_id → slot → start_time, end_time`. Remove by keeping table attributes in `RESTAURANT_TABLE` and slot times in `TIME_SLOT`; `RESERVATION` keeps only foreign keys + facts about the booking itself (date, guests, status, created_at). Every determinant is now a candidate key, so the schema is also in **BCNF**.

**Anomalies removed (give concrete examples):**
- *Update:* changing a customer's phone in the flat table touches N rows; now 1 row.
- *Insertion:* can't record a new table/customer until a reservation exists; now independent.
- *Deletion:* deleting a customer's only reservation loses the customer/table data; now preserved.

**Deliberate design notes to include:** `RESTAURANT_TABLE.status` is a *permanent* operational state (AVAILABLE/MAINTENANCE/INACTIVE), never "reserved" — temporal availability is derived from `RESERVATION` rows by (table, date, slot). Cancelled reservations are retained for history; the function-based unique index only constrains active rows.

## 15. Definition of done

All of the following must be demonstrably true:

- [ ] Launches styled with FlatLaf/Inter; sidebar navigation; no unstyled default widgets
- [ ] Oracle connection via JDBC from `config.properties`; DB-down handled gracefully
- [ ] Customer CRUD, Table CRUD with validation and friendly errors
- [ ] New Reservation flow works end-to-end in ≤ 5 interactions after choosing a customer
- [ ] Visual table cards (available / recommended / selected / booked / maintenance)
- [ ] Capacity check + best-fit recommendation
- [ ] **Double booking rejected by Oracle** (unique function-based index), proven by raw SQL script **and** by the app with two instances
- [ ] Cancel → slot immediately bookable again; cancelled rows retained
- [ ] Reservations search/filter, Mark Completed, Dashboard stats, 5 reports
- [ ] Transactions: `setAutoCommit(false)`, commit / rollback in `bookTable`
- [ ] `PreparedStatement` only; no SQL in UI classes; no credentials in git
- [ ] ER diagram matches the real schema; normalization doc complete; JUnit green

## 16. Do NOT

- Do not make it a web app, use MySQL, JPA/Hibernate, Spring, or add AI/online APIs.
- Do not put SQL in listeners/panels or catch `SQLException` in UI code.
- Do not change `restaurant_table.status` when someone books.
- Do not delete reservations on cancellation.
- Do not show stack traces or `ORA-` codes to the user.
- Do not run JDBC on the Swing event thread.
- Do not add features outside this file (payments, login, SMS, waitlist) — list them under "Future enhancements" in the README only.
