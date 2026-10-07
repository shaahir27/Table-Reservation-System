# Restaurant Table Reservation System (RTRS)

A standalone Java Swing desktop application connected via JDBC to Oracle Database, featuring concurrency-safe table reservation management, automated best-fit table recommendation, and engine-level double-booking prevention.

Built for **Object-Oriented Programming Using Java and Database Technology Laboratory (Project 13)**.

---

## 1. Key Features

- **Double-Booking Prevention at Oracle Engine Level:** Enforced via a function-based unique index (`UQ_ACTIVE_BOOKING`) and pessimistic locking (`SELECT ... FOR UPDATE`).
- **Streamlined 3-Step Booking:** Customer selection, intelligent date chips + seating slot toggles, and best-fit table recommendation based on party size.
- **Interactive Floor View:** Real-time spatial layout visualising all tables as `AVAILABLE`, `RESERVED`, or `MAINTENANCE` with click-to-inspect reservation details.
- **Modern FlatLaf UI:** Dark sidebar navigation, curated warm palette, Inter typography, non-blocking notification toasts, and custom `Graphics2D` table cards and bar charts.
- **Analytical Reports:** 5 core operational reports (Today's seatings, Date range, Status breakdown, Table popularity, and Slot distribution) with tabular data and bar chart visualization.
- **Strict Architecture:** UI $\rightarrow$ Service $\rightarrow$ DAO $\rightarrow$ DatabaseConnection $\rightarrow$ Oracle. Zero SQL in UI components; 100% parameterized `PreparedStatement` queries.

---

## 2. Technology Stack & Prerequisites

- **Java Development Kit:** Java 21 or higher (Java 21 or 25 recommended).
- **Build Tool:** Apache Maven 3.8+.
- **Database Engine:** Oracle Database XE (11g, 18c, or 21c).
- **Core Libraries:**
  - `ojdbc8` (21.9.0.0) — Oracle JDBC Driver.
  - `flatlaf` & `flatlaf-fonts-inter` (3.5.4) — Modern Flat Look and Feel.
  - `junit-jupiter` (5.10.2) — Automated testing framework.

---

## 3. Database Setup

### Step 1: Create Oracle User
Open SQL*Plus or SQL Developer as `SYS` or `SYSTEM`:

```sql
-- Connect to pluggable database (for 18c/21c XE)
ALTER SESSION SET CONTAINER = XEPDB1;

-- Create dedicated application user
CREATE USER rtrs IDENTIFIED BY your_db_password;
GRANT CONNECT, RESOURCE TO rtrs;
ALTER USER rtrs QUOTA UNLIMITED ON USERS;
```

### Step 2: Initialize Schema & Sample Data
Connect as the new user `rtrs` and execute the schema and seed scripts:

```sql
CONNECT rtrs/your_db_password@localhost:1521/XEPDB1;

-- 1. Create tables, sequences, indexes, and triggers
@sql/01_schema.sql

-- 2. Seed initial master data (tables, customers, slots)
@sql/02_seed.sql
```

---

## 4. Application Configuration

Configuration can be set via `.env` or `config.properties` in the project root directory:

### Option A: `.env` file (Recommended)
```ini
DB_URL=jdbc:oracle:thin:@localhost:1521/XEPDB1
DB_USER=rtrs
DB_PASSWORD=your_db_password
```

### Option B: `config.properties` file
```properties
db.url=jdbc:oracle:thin:@localhost:1521/XEPDB1
db.user=rtrs
db.password=your_db_password
```

*(Note: Sensitive credential files are excluded from version control via `.gitignore`.)*

---

## 5. Build and Execution

### Run Automated Unit Tests
```bash
mvn test
```

### Build Runnable Fat Jar
```bash
mvn clean package -DskipTests
```
This produces the self-contained, executable jar at:
`target/rtrs.jar`

### Launch the Application
- **Windows (Double-click or Command Line):**
  ```bash
  run.bat
  # or
  java -jar target/rtrs.jar
  ```
- **Linux / macOS:**
  ```bash
  chmod +x run.sh
  ./run.sh
  # or
  java -jar target/rtrs.jar
  ```

---

## 6. Proving Double-Booking Prevention

To demonstrate that Oracle itself rejects concurrent double bookings, run the provided SQL verification script:

```bash
sqlplus rtrs/your_db_password@localhost:1521/XEPDB1 @sql/04_double_booking_demo.sql
```

**Observation:**
1. Booking 1 succeeds.
2. Booking 2 for the exact same table, date, and slot immediately fails with:
   `ORA-00001: unique constraint (RTRS.UQ_ACTIVE_BOOKING) violated`.
3. Cancelling Booking 1 frees the slot, allowing Booking 2 to immediately succeed.

---

## 7. Project Documentation Index

### 📘 Comprehensive Project Reference Guides (In-Depth & Layman-Friendly)
- **[01. System Architecture, Build & Dependencies](docs/01_ARCHITECTURE_AND_DEPENDENCIES.md):** High-level architecture, Maven build system, FlatLaf & Inter dependencies, threading model (`Ui.async`), logging (`AppLog`), and project file map.
- **[02. Oracle Database In-Depth](docs/02_DATABASE_IN_DEPTH.md):** Complete schema tables, JDBC lifecycle, function-based unique index mechanics (`UQ_ACTIVE_BOOKING`), capacity triggers, error code mapping, and 1NF–BCNF normalization.
- **[03. Java Backend & Modules](docs/03_JAVA_BACKEND_AND_MODULES.md):** OOP principles showcase, domain models, generic `CrudDAO<T, ID>`, transaction boundaries, pessimistic locking (`FOR UPDATE`), table recommendation algorithm, and validation engine.
- **[04. Java Swing Presentation Layer](docs/04_UI_AND_PRESENTATION_LAYER.md):** Theme design tokens, `MainFrame` 3-state shell, 6 functional screens, 3-step booking flow, interactive Floor View, and custom `Graphics2D` components (`TableCard`, `BarChartPanel`, `Toast`).
- **[05. Testing Verification & Viva Voce Guide](docs/05_TESTING_VERIFICATION_AND_VIVA_GUIDE.md):** JUnit 5 automated test suite (20 tests), 15 end-to-end specification test matrix, live SQL proof walkthrough, and 12 Viva Voce defense Q&As with code references.

### 📄 Specialized Technical Briefs
- [Database Normalization Derivation](docs/NORMALIZATION.md): 1NF $\rightarrow$ 2NF $\rightarrow$ 3NF/BCNF derivation, functional dependencies, and operational anomalies.
- [Entity-Relationship Diagram](docs/ER.md): Visual Mermaid ER diagram and schema dictionary.
- [System Architecture](docs/ARCHITECTURE.md): Multi-tiered architectural layers, threading model, and concurrency diagrams.
- [Test Verification Report](docs/TEST_CASES.md): Verification matrix for all 15 specification test scenarios.
- [Assumptions & Design Decisions](docs/ASSUMPTIONS.md): Domain modeling and operational decisions.

---

## 8. Future Enhancements

The following features are purposefully planned for future iterations outside the laboratory brief:
- Online payments and deposit collection.
- Multi-role employee authentication (Manager vs. Host).
- SMS and email notification webhooks upon booking/cancellation.
- Automated waitlist management when tables are fully booked.
