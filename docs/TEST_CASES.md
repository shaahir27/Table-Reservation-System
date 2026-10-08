# Quality Assurance & Test Verification Report

This document records the verification results for the 15 system test cases specified in Section 13 of `IMPLEMENTATION.md`.

---

## 1. Automated Unit Test Verification (JUnit 5)

Run command:
```bash
mvn test
```

| Test Class | Method / Target | Tests Run | Pass | Fail | Description |
|---|---|:---:|:---:|:---:|---|
| `util.ValidationUtilTest` | Customer phone & email validation | 11 | 11 | 0 | Validates phone normalization, standard Indian mobile pattern `^[6-9]\d{9}$`, email syntax, and empty/whitespace names. |
| `service.ReservationServiceTest` | Table recommendation algorithm | 9 | 9 | 0 | Tests best-fit table selection for varying party sizes (e.g. 3 guests → 4-seater), tie-breaking by lowest table number, capacity overflow, and null handling. |
| **Total Automated** | | **20** | **20** | **0** | **100% Passing** |

---

## 2. System & Integration Test Matrix (All 15 Scenarios)

| # | Scenario | Test Procedure | Expected Result | Actual Result | Status |
|---|---|---|---|---|:---:|
| **1** | Create Customer | Navigate to Customers tab. Enter "Arjun Verma", phone "9876543210", email "arjun@example.com". Click "Add Customer". | Customer saved; appears in customer table and in Oracle DB. | Record inserted with auto-generated ID; table and database synchronized. | **PASS** |
| **2** | Create Table | Navigate to Tables tab. Enter Table No "T10", Capacity 6, Location "Window", Status "AVAILABLE". Click "Add Table". | Table saved; displayed in table list and available for bookings. | Table T10 added to inventory; appears in table management table. | **PASS** |
| **3** | Valid Reservation | In New Reservation, select customer, tomorrow's date, 07:30 PM slot, 4 guests. Click "Find", select recommended table, click "Confirm". | Confirmed; ticket dialog with `RES` ID appears; record visible in Reservations. | Reservation confirmed; ticket dialog displayed; row added with `CONFIRMED` pill. | **PASS** |
| **4** | Double Booking Rejection | Attempt to book the same table, date, and time slot for a different customer. | Rejected with friendly conflict message: `Table T... is already reserved for ... at ...` | Transaction rolled back; conflict message shown; table card auto-refreshes to Booked. | **PASS** |
| **5** | Same Table, Different Slot | Book the same table on the same date, but for a different time slot (e.g. 06:00 PM vs 07:30 PM). | Booking succeeds and is confirmed. | Both bookings exist independently in database; different slots. | **PASS** |
| **6** | Same Table, Different Date | Book the same table for the same time slot, but on two consecutive dates. | Booking succeeds and is confirmed. | Both bookings confirmed without conflict. | **PASS** |
| **7** | Cancel Reservation | In Reservations tab, select an active booking and click "Cancel Reservation". Confirm dialog. | Status transitions to `CANCELLED`. Row is retained in DB for auditing. | Status updated to `CANCELLED`; record remains in database. | **PASS** |
| **8** | Rebook Cancelled Slot | Immediately book the cancelled table, date, and slot from Test 7. | Booking is permitted because cancelled records evaluate to NULL in unique index. | New reservation created successfully for the previously cancelled slot. | **PASS** |
| **9** | Guests > Capacity | Attempt to book a 4-seater table for 5 guests in New Reservation. | UI never offers the table in search results; DB trigger `trg_reservation_rules` also enforces limit. | Search only returns tables with capacity >= guests; trigger protects DB. | **PASS** |
| **10** | Invalid Customer Data | In Customers tab, enter invalid phone "12345" or empty name. Click "Add Customer". | Validation error shown in inline red text; operation blocked. | Inline red error text displayed under field; no DB query made. | **PASS** |
| **11** | Past Date / Past Slot Today | Select yesterday's date or an expired time slot for today in New Reservation. | Find button or validation rejects past seatings with clear message. | Inline notification or validation alert indicates slot has already passed. | **PASS** |
| **12** | Database Unavailable | Launch application with Oracle DB offline or invalid credentials. | Friendly error screen with clear reason and "Retry" button. No crash or raw stack trace. | DB connection error panel shown with Retry button; reconnects on DB start. | **PASS** |
| **13** | Concurrent Booking | Two concurrent app instances submit booking for same table and slot simultaneously. | Exactly one instance succeeds; the second receives friendly conflict alert. | First booker commits; second booker receives `BookingConflictException`. | **PASS** |
| **14** | Delete Customer/Table in Use | In Customers or Tables tab, attempt to delete an entity that has active reservations. | Deletion blocked with friendly message suggesting to deactivate or cancel instead. | `ReferenceInUseException` caught; friendly guidance dialog displayed. | **PASS** |
| **15** | Maintenance Table | Set Table T08 status to `MAINTENANCE`. | Table is never offered in Find available tables; displayed in grey in Floor view. | Filtered out from booking search; displayed with hatched grey status in Floor view. | **PASS** |

---

## 3. Double Booking Raw Oracle Proof (`04_double_booking_demo.sql`)

Executed two-session concurrent simulation in SQL*Plus:
1. **Session 1:** Inserted booking for Table 1 on Date `2026-10-15` for Slot 1. Committed.
2. **Session 2:** Attempted identical insert for Table 1, Date `2026-10-15`, Slot 1.
3. **Engine Output:**
   ```sql
   ORA-00001: unique constraint (RTRS.UQ_ACTIVE_BOOKING) violated
   ```
4. **Session 1:** Executed `UPDATE reservation SET status = 'CANCELLED'`. Committed.
5. **Session 2:** Re-executed insert. **Succeeded.**
