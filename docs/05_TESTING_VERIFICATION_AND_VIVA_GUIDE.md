# 05 — Quality Assurance, Testing Verification & Viva Voce Defense Guide

> **Document Purpose:** An in-depth, layman-friendly guide to testing and defending the **Restaurant Table Reservation System (RTRS)**. Explains the automated JUnit 5 test suite, the 15 system specification test scenarios, the two-session double-booking proof script, and provides a comprehensive Viva Voce examination question-and-answer guide with exact code references.
> **Verified against codebase:** [ReservationServiceTest.java](file:///d:/Desktop/project/RTRS/src/test/java/service/ReservationServiceTest.java), [ValidationUtilTest.java](file:///d:/Desktop/project/RTRS/src/test/java/util/ValidationUtilTest.java), [04_double_booking_demo.sql](file:///d:/Desktop/project/RTRS/sql/04_double_booking_demo.sql), [docs/TEST_CASES.md](file:///d:/Desktop/project/RTRS/docs/TEST_CASES.md).

---

## 1. Quality Assurance Philosophy: Why Test?

In software development, manual testing ("clicking around with the mouse") is slow, prone to human error, and impossible to repeat consistently.  
To guarantee that our code works flawlessly, RTRS uses **two layers of quality assurance**:
1. **Automated Unit Tests (JUnit 5):** Fast, isolated tests that run from the command line in under 2 seconds, verifying algorithmic calculations and regex validation without needing a database connection.
2. **System & Integration Test Matrix:** 15 comprehensive end-to-end scenarios verifying the full lifecycle across the GUI, service layer, and live Oracle Database.

---

## 2. Automated Unit Test Suite (JUnit 5)

To run all automated unit tests, open a terminal in the project directory and execute:
```bash
mvn test
```

### 2.1. Test Suite Summary
- **Total Tests:** 20
- **Passed:** 20 (100% Pass Rate)
- **Execution Time:** ~1.2 seconds

---

### 2.2. [ReservationServiceTest.java](file:///d:/Desktop/project/RTRS/src/test/java/service/ReservationServiceTest.java) (9 Unit Tests)

Verifies the greedy table recommendation algorithm and business validation logic:

| Test Method | What It Tests | Plain English Explanation | Result |
|---|---|---|:---:|
| `testRecommendTable_SmallestFit` | Table Recommendation Algorithm | For a party of 3, the algorithm must recommend a 4-seater table (e.g. Table T03) rather than an 8-seater table (T02), preventing seat wastage. | **PASS** |
| `testRecommendTable_TieBreaking` | Tie-breaking rule | If two available tables have the exact same capacity (e.g. T02 and T03 both seat 4), the algorithm picks the lowest alphanumeric table number (`T02`). | **PASS** |
| `testRecommendTable_NoFit` | Capacity overflow | If a party of 8 arrives but the largest free table seats 6, the algorithm returns `null`, signaling that no single table can fit them. | **PASS** |
| `testRecommendTable_IgnoresUnavailable` | Maintenance filtering | If Table T01 seats 4 but has status `MAINTENANCE`, it is ignored, and the next available 6-seater is picked. | **PASS** |
| `testValidateReservation_Valid` | Happy path validation | Future date, available table, valid slot, and party size within capacity pass with zero errors. | **PASS** |
| `testValidateReservation_PastDate` | Past date prevention | Attempting to book yesterday's date throws `ValidationException` on field `"date"`. | **PASS** |
| `testValidateReservation_GuestsExceedCapacity` | Capacity enforcement | Attempting to place 4 guests on a 2-seater table throws `ValidationException` on field `"table"`. | **PASS** |
| `testValidateReservation_MaintenanceTable` | Broken table booking | Attempting to book a table marked as `MAINTENANCE` throws `ValidationException`. | **PASS** |
| `testValidateReservation_PastSlotToday` | Expired slot today | If today is selected, but the chosen slot's start time has already passed, booking is rejected with `ValidationException` on field `"slot"`. | **PASS** |

---

### 2.3. [ValidationUtilTest.java](file:///d:/Desktop/project/RTRS/src/test/java/util/ValidationUtilTest.java) (11 Unit Tests)

Verifies phone normalization and field validation rules:

| Test Method | Target | Plain English Explanation | Result |
|---|---|---|:---:|
| `testNormalizePhone` | Phone Normalization | Converts `+91 98765-43210`, `09876543210`, and `919876543210` into a clean 10-digit number: `9876543210`. | **PASS** |
| `testValidateCustomer_Valid` | Valid customer | Full name, 10-digit mobile, and valid email pass validation. | **PASS** |
| `testValidateCustomer_NoEmail` | Optional email | In a restaurant, many customers do not provide an email. Tests that `null` or empty email passes validation without issue. | **PASS** |
| `testValidateCustomer_InvalidFields` | Malformed inputs | Rejects single-letter names, 5-digit phones, and invalid email syntax simultaneously. | **PASS** |
| `testValidateCustomer_InvalidPhoneStartDigit` | Indian mobile pattern | Rejects phone numbers starting with digits `0-5` (must start with `6-9`). | **PASS** |
| `testValidateCustomer_Null` | Null safety | Passing a `null` customer object throws `ValidationException`. | **PASS** |
| `testValidateTable_Valid` | Valid table | Table number `T01`, capacity 4, location `Indoor` passes. | **PASS** |
| `testValidateTable_InvalidCapacity` | Capacity boundaries | Rejects capacity of 0 or capacity exceeding 20 seats. | **PASS** |
| `testValidateTable_InvalidTableNumber` | Table number rules | Rejects empty table numbers or strings containing illegal special characters (`T#01@!`). | **PASS** |
| `testValidateTable_InvalidLocationAndStatus` | Blank fields | Rejects empty locations or `null` table statuses. | **PASS** |
| `testValidateTable_Null` | Null safety | Passing a `null` table object throws `ValidationException`. | **PASS** |

---

## 3. End-to-End System & Integration Verification Matrix

The 15 specification test scenarios defined in `IMPLEMENTATION.md`:

| # | Test Scenario | Step-by-Step Procedure | Expected Result | Actual Result | Status |
|---|---|---|---|---|:---:|
| **1** | **Create Customer** | Navigate to Customers tab. Enter "Arjun Verma", phone "9876543210", email "arjun@example.com". Click "Add Customer". | Saved with auto-generated ID from `customer_seq`. Appears in table. | Inserted into Oracle; displayed in UI table. | **PASS** |
| **2** | **Create Table** | Navigate to Tables tab. Enter Table No "T10", Capacity 6, Location "Window", Status "AVAILABLE". Click "Add Table". | Saved with ID from `table_seq`. Available for bookings. | Table added to inventory; visible in floor view. | **PASS** |
| **3** | **Valid Reservation** | In New Reservation, select customer, tomorrow's date, 07:30 PM slot, 4 guests. Click "Find", select recommended table, click "Confirm". | Ticket dialog with `RES` ID appears; row added to Reservations table. | Booking confirmed; dialog displays `RES` number; status pill `CONFIRMED`. | **PASS** |
| **4** | **Double-Booking Rejection** | Attempt to book the same table, date, and time slot for a different customer. | Blocked with friendly conflict message: *"Table T... is already reserved for ... at ..."* | `BookingConflictException` caught; friendly toast shown; card turns Booked. | **PASS** |
| **5** | **Same Table, Different Slot** | Book the same table on the same date, but for a different time slot (e.g. 06:00 PM vs 07:30 PM). | Booking succeeds and is confirmed. | Both bookings exist independently in database. | **PASS** |
| **6** | **Same Table, Different Date** | Book the same table for the same time slot, but on two consecutive dates. | Booking succeeds and is confirmed. | Both bookings confirmed without conflict. | **PASS** |
| **7** | **Cancel Reservation** | In Reservations tab, select an active booking and click "Cancel Reservation". Confirm dialog. | Status transitions to `CANCELLED`. Row is retained in DB for auditing. | Status updated to `CANCELLED`; record remains in database. | **PASS** |
| **8** | **Rebook Cancelled Slot** | Immediately book the cancelled table, date, and slot from Scenario 7. | Booking is permitted because cancelled records evaluate to NULL in unique index. | New reservation created successfully for the previously cancelled slot. | **PASS** |
| **9** | **Guests > Capacity** | Attempt to book a 4-seater table for 5 guests in New Reservation. | UI never offers the table in search results; DB trigger `TRG_RESERVATION_RULES` also enforces limit. | Search only returns tables with capacity $\ge$ guests; trigger protects DB. | **PASS** |
| **10** | **Invalid Customer Data** | In Customers tab, enter invalid phone "12345" or empty name. Click "Add Customer". | Validation error shown in inline red text; operation blocked. | Inline red error text displayed under field; no DB query made. | **PASS** |
| **11** | **Past Date / Past Slot Today** | Select yesterday's date or an expired time slot for today in New Reservation. | Find button or validation rejects past seatings with clear message. | Expired slot buttons disabled; validation alert indicates past seating. | **PASS** |
| **12** | **Database Unavailable** | Launch application with Oracle DB offline or invalid credentials. | Friendly error screen with clear reason and "Retry" button. No crash or raw stack trace. | DB connection error panel shown with Retry button; reconnects on DB start. | **PASS** |
| **13** | **Concurrent Booking** | Two concurrent app instances submit booking for same table and slot simultaneously. | Exactly one instance succeeds; the second receives friendly conflict alert. | First booker commits; second booker receives `BookingConflictException`. | **PASS** |
| **14** | **Delete Customer/Table in Use** | In Customers or Tables tab, attempt to delete an entity that has active reservations. | Deletion blocked with friendly message suggesting to deactivate or cancel instead. | `ReferenceInUseException` caught; friendly guidance dialog displayed. | **PASS** |
| **15** | **Maintenance Table** | Set Table T08 status to `MAINTENANCE`. | Table is never offered in Find available tables; displayed with grey hatched lines in Floor view. | Filtered out from booking search; displayed with hatched grey status in Floor view. | **PASS** |

---

## 4. Double-Booking Proof: Live SQL Demonstration

To prove to an examiner that Oracle itself rejects double bookings without relying on Java, run [04_double_booking_demo.sql](file:///d:/Desktop/project/RTRS/sql/04_double_booking_demo.sql) in two separate SQL*Plus terminal sessions:

```sql
-- SESSION A: First booking succeeds
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (reservation_seq.NEXTVAL, 1, 5, TRUNC(SYSDATE)+1, 4, 4, 'CONFIRMED');
COMMIT;
-- Result: 1 row created.

-- SESSION B: Second customer attempts exact same table, date, and slot
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (reservation_seq.NEXTVAL, 2, 5, TRUNC(SYSDATE)+1, 4, 4, 'CONFIRMED');
-- Result: ORA-00001: unique constraint (RTRS.UQ_ACTIVE_BOOKING) violated!

-- SESSION A: Session A cancels their booking
UPDATE reservation SET status = 'CANCELLED' 
WHERE table_id = 5 AND reservation_date = TRUNC(SYSDATE)+1 AND slot_id = 4;
COMMIT;

-- SESSION B: Session B retries
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (reservation_seq.NEXTVAL, 2, 5, TRUNC(SYSDATE)+1, 4, 4, 'CONFIRMED');
COMMIT;
-- Result: 1 row created! Succeeded now that previous booking is cancelled!
```

---

## 5. Comprehensive Viva Voce Examination Defense Guide

Here are the top 12 questions examiners and project evaluators ask, complete with layman explanations and exact code references:

---

### Q1: "Where is double-booking prevented: in Java code or in the Oracle database?"
- **Layman Answer:** "Both! But the **Oracle Database is the ultimate and absolute guarantee**."
- **Technical Explanation:**  
  "In Java, we do friendly pre-checks and acquire a pessimistic row lock (`SELECT ... FOR UPDATE`) in `ReservationService.java` to give the user a polite error message. However, even if someone bypassed our Java application completely and inserted rows directly through SQL*Plus, Oracle's function-based unique index `UQ_ACTIVE_BOOKING` immediately halts the insert and raises `ORA-00001`. The database engine itself makes a double booking physically impossible."
- **Code Reference:** [01_schema.sql](file:///d:/Desktop/project/RTRS/sql/01_schema.sql#L67-L71), [ReservationService.java](file:///d:/Desktop/project/RTRS/src/main/java/service/ReservationService.java#L148-L167).

---

### Q2: "Why did you use 5 fixed 90-minute time slots instead of continuous 30-minute intervals (12:00, 12:30, 01:00...)?"
- **Layman Answer:** "Because continuous overlapping times create physical collisions and make database design messy."
- **Technical Explanation:**  
  "If customer A books from 12:00 to 1:30, and customer B books at 12:30, they physically collide. Preventing this requires complex, slow mathematical date-range overlapping formulas. In fine-dining restaurants, operations are organized into fixed seatings (e.g., 2 lunch seatings, 3 dinner seatings). By storing slots in a normalized `TIME_SLOT` table in 3NF, seating windows become clean relational data rather than hardcoded logic. Adding a new seating in the future requires a simple SQL `INSERT` without changing any Java code."
- **Code Reference:** [01_schema.sql](file:///d:/Desktop/project/RTRS/sql/01_schema.sql#L33-L38), [TimeSlotDAO.java](file:///d:/Desktop/project/RTRS/src/main/java/dao/TimeSlotDAO.java).

---

### Q3: "How does cancelling a reservation free up the table without deleting the row from the database?"
- **Layman Answer:** "By using Oracle's rule that indexes ignore `NULL` values."
- **Technical Explanation:**  
  "Our unique index `UQ_ACTIVE_BOOKING` uses a `CASE` statement:  
  `CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN table_id END`.  
  When a booking is cancelled, `status` changes to `'CANCELLED'`. The `CASE` statement has no matching branch, so Oracle evaluates it to `NULL`. Because Oracle B-Tree indexes do not index rows where all key columns are `NULL`, the cancelled booking disappears from the unique index, freeing up the slot immediately for new bookings while preserving the cancelled record in the database for auditing and reporting."
- **Code Reference:** [01_schema.sql](file:///d:/Desktop/project/RTRS/sql/01_schema.sql#L67-L71), [04_double_booking_demo.sql](file:///d:/Desktop/project/RTRS/sql/04_double_booking_demo.sql#L65-L75).

---

### Q4: "How does the table recommendation algorithm select the best table?"
- **Layman Answer:** "It uses a greedy 'smallest fit first' strategy to prevent wasting large tables on small groups."
- **Technical Explanation:**  
  "In `ReservationService.recommendTable()`, candidates are filtered to ensure `capacity >= guests` and `status == AVAILABLE`. We then stream the list using `min(Comparator.comparingInt(RestaurantTable::getCapacity).thenComparing(RestaurantTable::getTableNumber))`. This selects the smallest capacity that can seat the party. Ties between two identical-sized tables are broken deterministically by selecting the lowest alphanumeric table number."
- **Code Reference:** [ReservationService.java](file:///d:/Desktop/project/RTRS/src/main/java/service/ReservationService.java#L113-L124).

---

### Q5: "How does the desktop application prevent the screen from freezing during database queries?"
- **Layman Answer:** "By keeping database network operations off the Swing Event Dispatch Thread (EDT)."
- **Technical Explanation:**  
  "Java Swing handles all UI painting and button events on a single thread: the EDT. If you run a JDBC query directly on the EDT, the window freezes until the database responds. We built an asynchronous helper `Ui.async()` powered by `SwingWorker`. The database query executes on a background worker thread, while the `done()` callback dispatches results back to the EDT via `SwingUtilities.invokeLater()` to update the UI smoothly."
- **Code Reference:** [Ui.java](file:///d:/Desktop/project/RTRS/src/main/java/util/Ui.java#L21-L46).

---

### Q6: "How do you protect the application from SQL Injection?"
- **Layman Answer:** "By never concatenating user text into SQL query strings."
- **Technical Explanation:**  
  "100% of our SQL statements in the DAO layer use `PreparedStatement` with parameterized `?` placeholders. The database driver transmits the parameters separately from the SQL statement via Oracle's native wire protocol. The engine treats user input strictly as literal data, making SQL injection impossible."
- **Code Reference:** [CustomerDAO.java](file:///d:/Desktop/project/RTRS/src/main/java/dao/CustomerDAO.java#L115-L130), [ReservationDAO.java](file:///d:/Desktop/project/RTRS/src/main/java/dao/ReservationDAO.java#L40-L48).

---

### Q7: "Why isn't a table's status in `restaurant_table` simply flipped to 'RESERVED' when a booking is created?"
- **Layman Answer:** "Because a table is not reserved forever—it is only reserved for 90 minutes on a specific date."
- **Technical Explanation:**  
  "`restaurant_table.status` represents permanent, physical operational condition (`AVAILABLE`, `MAINTENANCE`, `INACTIVE`). If we set Table T01 to `RESERVED` for tomorrow night, it would falsely appear booked for today lunch! Instead, temporal availability is derived dynamically: a table is free at `(date, slot)` if no active reservation exists for that tuple in the `RESERVATION` table."
- **Code Reference:** [TableDAO.java](file:///d:/Desktop/project/RTRS/src/main/java/dao/TableDAO.java#L195-L219), [docs/NORMALIZATION.md](file:///d:/Desktop/project/RTRS/docs/NORMALIZATION.md#L136-L148).

---

### Q8: "What happens if a cashier tries to delete a customer or table that has past reservations?"
- **Layman Answer:** "The deletion is blocked to protect historical audit records."
- **Technical Explanation:**  
  "Foreign key constraints (`fk_res_customer` and `fk_res_table`) link reservations to customers and tables. If a delete is attempted, Oracle raises `ORA-02292: integrity constraint violated - child record found`. In `OracleErrorMapper`, we catch this code and translate it into a `ReferenceInUseException`. The UI shows a friendly guidance dialog advising the cashier to set the table to `INACTIVE` instead of deleting it."
- **Code Reference:** [OracleErrorMapper.java](file:///d:/Desktop/project/RTRS/src/main/java/util/OracleErrorMapper.java#L35-L39), [TableService.java](file:///d:/Desktop/project/RTRS/src/main/java/service/TableService.java#L48-L57).

---

### Q9: "How do you explain the database normalization of this project to an examiner?"
- **Layman Answer:** "We started with an unnormalized flat spreadsheet and systematically eliminated partial and transitive dependencies to reach Boyce-Codd Normal Form (BCNF)."
- **Technical Explanation:**  
  - **1NF:** Ensured all attributes are atomic scalars (no repeating groups).
  - **2NF:** Eliminated partial dependencies on composite candidate keys by decomposing the flat relation into `CUSTOMER`, `RESTAURANT_TABLE`, `TIME_SLOT`, and `RESERVATION`.
  - **3NF:** Eliminated transitive dependencies so non-key attributes depend only on the primary key.
  - **BCNF:** In every table, every determinant is a superkey (candidate key). This eliminates update, insertion, and deletion anomalies.
- **Code Reference:** [docs/NORMALIZATION.md](file:///d:/Desktop/project/RTRS/docs/NORMALIZATION.md).

---

### Q10: "Why use FlatLaf instead of standard Swing Metal or Ocean look-and-feel?"
- **Layman Answer:** "To give the application a clean, modern aesthetic with rounded corners, good contrast, and legible typography."
- **Technical Explanation:**  
  "FlatLaf is a lightweight, modern look-and-feel engine for Swing. It respects high-DPI scaling on 4K monitors, integrates the Inter typeface, supports customizable rounded corners on buttons and input fields (`Component.arc = 12`), and provides clean table padding without needing heavy web frameworks."
- **Code Reference:** [Main.java](file:///d:/Desktop/project/RTRS/src/main/java/app/Main.java#L20-L35).

---

### Q11: "How do you securely handle database credentials in this application?"
- **Layman Answer:** "By keeping them out of source code and loading them from external `.env` or `config.properties` files that are ignored by Git."
- **Technical Explanation:**  
  "`DatabaseConnection.java` loads connection properties at startup from `.env` or `config.properties` located in the application working directory. Both files are included in `.gitignore` so passwords are never committed to GitHub. Template files (`.env.example` and `config.properties.example`) are provided for team setup."
- **Code Reference:** [DatabaseConnection.java](file:///d:/Desktop/project/RTRS/src/main/java/db/DatabaseConnection.java#L28-L62), [.gitignore](file:///d:/Desktop/project/RTRS/.gitignore).

---

### Q12: "How does the application handle startup when the Oracle Database is not running?"
- **Layman Answer:** "It shows a friendly error card with a retry button instead of crashing."
- **Technical Explanation:**  
  "`MainFrame` runs `DatabaseConnection.testConnection()` during startup with a 5-second login timeout. If the Oracle listener is offline or credentials fail, the window switches its root `CardLayout` to `DB_ERROR`. This displays a warm diagnostic card explaining the exact failure reason, providing setup hints, and offering a 'Retry Connection' button that re-tests connectivity as soon as the user starts Oracle XE."
- **Code Reference:** [MainFrame.java](file:///d:/Desktop/project/RTRS/src/main/java/ui/MainFrame.java#L101-L182), [DatabaseConnection.java](file:///d:/Desktop/project/RTRS/src/main/java/db/DatabaseConnection.java#L108-L117).
