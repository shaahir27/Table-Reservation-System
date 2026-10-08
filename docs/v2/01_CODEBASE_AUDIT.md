# 01 · Codebase Audit — every file, every finding

**Scope:** `Table-Reservation-System-main.zip` — 80 files (55 main Java, 2 test Java, 4 SQL, 10 docs, 9 root/config). `RTRS.zip` inside it is a copy: `src/`, `sql/` and `docs/` are identical to the main tree (checked with `diff -r`); it additionally carries `logs/app.log`.

**How deep I read:** all SQL, all `app/db/util/exception/model/dao/service` code, `MainFrame`, `Sidebar`, `Theme`, `BasePanel`, `NewReservationPanel`, `ReservationsPanel`, `DashboardPanel`, `CustomerPanel`, `TablePanel` (form + floor view), `TableGridPanel`, `TableCard`, `Toast`, `DateSelector`, `SearchableComboBox`, `BookingConfirmedDialog`, `StatusPillRenderer`, both test classes, README, ASSUMPTIONS, NORMALIZATION, TEST_CASES, ER (schema section). Read partially (structure + the lines that matter): `ReportsPanel` (rest of layout code), `AddCustomerDialog`, `BarChartPanel`, `StatCard`, `WrapLayout`, `IMPLEMENTATION.md`, docs 01–05 (headings + claims cross-checked against code). Nothing was executed against Oracle (none available); Java reasoning is from reading the source.

---

## 1. What the system is today

A three-tier desktop app: **Swing/FlatLaf UI → services → DAOs → `DatabaseConnection` → Oracle**. Staff operate it on behalf of guests (there are no guest accounts; `customer` is a contact record). Booking unit = *(table, date, one of 5 fixed 90-min slots)*.

```
UI panels ──Ui.async(SwingWorker)──► Service ──► DAO (PreparedStatement) ──► new JDBC connection per call ──► Oracle
                                        │
                                        └─ bookTable(): setAutoCommit(false) → lock table row (FOR UPDATE)
                                           → app pre-check → INSERT → commit      (+ UQ_ACTIVE_BOOKING as the real guard)
```

## 2. What is genuinely good (keep it)

| Strength | Where | Why it matters |
|---|---|---|
| Double-booking guard in the engine | `01_schema.sql` `uq_active_booking` (3 `CASE` columns) | Correct use of "all-NULL keys are not indexed": cancelled rows free the slot but stay as history. This is the project's signature and the v2 plan **preserves and extends** it. |
| Layering discipline | whole `src/main` | No SQL in UI; all `PreparedStatement`; services own transactions. |
| Error translation | `OracleErrorMapper` | Raw ORA codes never reach users. |
| Non-blocking UI | `Ui.async` | Every DB call is off the EDT. |
| Honest data model | `restaurant_table.status` is physical state; availability is *derived* | Avoids the classic "flip table to RESERVED" anomaly. |
| Design tokens | `Theme` | A single palette/typography file — good base for the UI kit in doc 04. |

## 3. File-by-file inventory

### 3.1 Root / config (9)
| File | Role | Assessment |
|---|---|---|
| `pom.xml` | Maven, Java 21, ojdbc8 21.9, FlatLaf 3.5.1, Inter 4.0, JUnit 5.10.2, shade plugin → `target/rtrs.jar` | Sound. v2 needs **no new runtime dependency** (PBKDF2 is in the JDK). |
| `README.md` | Setup & feature overview | Says FlatLaf 3.5.4 but `pom.xml` pins 3.5.1; uses `DB_USER` while `.env.example` uses `DB_USERNAME` (code accepts both). |
| `IMPLEMENTATION.md` | The original build spec (16 sections, 5 phases) | Useful history; superseded by this plan for v2. |
| `run.sh`, `run.bat` | `java -jar target/rtrs.jar` | Fine. |
| `config.properties.example`, `.env.example` | DB settings templates | Fine; `.env` is gitignored. Credentials are plaintext by nature of a desktop app (see doc 02 threat model). |
| `.gitignore` | ignores `target/`, `.env`, `logs/`, … | Good. |
| `RTRS.zip` | duplicate snapshot + `logs/app.log` (61 KB) | Remove from the repo; the log shows only early `ORA-00942` (schema not yet created) then a clean connect. |

### 3.2 SQL (4)
| File | Role | Assessment |
|---|---|---|
| `01_schema.sql` | 3 sequences, 4 tables, 2 indexes, `uq_active_booking`, trigger `trg_reservation_rules`, 5 slots | Solid BCNF design. `time_slot.start_time/end_time` are `VARCHAR2('HH24:MI')` — sortable and simple, but not validated by a CHECK. |
| `02_seed.sql` | 6 customers, 8 tables, 8 reservations (dates relative to `SYSDATE`) | Good demo data; T05 tomorrow 19:30 intentionally free. |
| `03_drop_all.sql` | clean teardown | Must be extended for v2 objects (doc 05). |
| `04_double_booking_demo.sql` | 2-session proof of `ORA-00001` | Excellent viva asset; keep and extend with the new statuses. |

### 3.3 Java — `app`, `db`, `util`, `exception` (12)
| File | Assessment |
|---|---|
| `app/Main` | FlatLaf + Inter bootstrap. v2: add `@accentColor`, window-state restore. |
| `db/DatabaseConnection` | `.env` → `config.properties` → env vars; 5 s login timeout. **Defect D1** (cast). New connection per call (**D10**). |
| `util/AppLog` | `java.util.logging` → `logs/app.log`. Fine. Never log passwords (v2 rule). |
| `util/OracleErrorMapper` | Clean switch on `ORA-` codes; messages mention `config.properties` though `.env` is preferred. v2 adds `-20003`, `-20010`, `30006`, `28000`. |
| `util/Ui` | `SwingWorker` wrapper. v2: add a global busy indicator hook. |
| `util/ValidationUtil` | Pure validation, aggregated field errors. **D8** (ASCII-only names). Rejects "started" slots even for walk-ins (**D3**). |
| `exception/*` (6) | Small, well-shaped hierarchy under `AppException`. v2 adds `AuthenticationException`, `AccessDeniedException`, `AccountLockedException`. |

### 3.4 Java — `model` (11)
`Customer`, `RestaurantTable`, `TimeSlot`, `Reservation`, `ReservationDetail` (read DTO), `ReservationFilter`, `DashboardStats`, `TableAvailability`, plus enums `ReservationStatus {CONFIRMED, CANCELLED, COMPLETED}`, `TableStatus {AVAILABLE, MAINTENANCE, INACTIVE}`, `TableState {AVAILABLE, RESERVED, MAINTENANCE}`. Plain POJOs with sensible `equals/hashCode`. v2: extend `ReservationStatus` (6 values), add `AppUser`, `Role`, `WaitlistEntry`, `WaitlistStatus`, `AvailabilityCell`.

### 3.5 Java — `dao` (7)
| File | Assessment |
|---|---|
| `CrudDAO<T,ID>` | Generic interface, nicely OOP. |
| `CustomerDAO`, `TableDAO` | Standard CRUD with both self-managed and caller-supplied `Connection` overloads (good for transactions). `lockTable` uses unbounded `FOR UPDATE` (**D7**). |
| `ReservationDAO` | Dynamic search with bound params only. Status literals hard-coded (**D11**); silent 11g fallback (**D12**); uses `SYSDATE`/`TO_CHAR(SYSDATE,'HH24:MI')` (**D5**). |
| `TimeSlotDAO` | Trivial. |
| `ReportDAO` + `ReportResult` | Five reports through one generic tabular result. Counts `CONFIRMED`+`COMPLETED` as "bookings" (no no-show concept — **D2**). |

### 3.6 Java — `service` (4)
| File | Assessment |
|---|---|
| `ReservationService` | The heart. `bookTable` is a correct transaction (lock → re-read → pre-check → insert → commit, rollback on error). `recommendTable` is a pure, tested best-fit. `markCompleted` lets a never-attended booking become `COMPLETED` (**D2**). |
| `CustomerService`, `TableService`, `ReportService` | Thin and clean. `TableService.updateTable` lets a table go `MAINTENANCE/INACTIVE` with future bookings and no warning (**D22**). |

### 3.7 Java — `ui` (10) and `ui.components` (11)
| File | Assessment |
|---|---|
| `MainFrame` | Root `CardLayout`: splash → DB-error → app shell. Builds **all** panels at startup (before any login would exist). Min size 1200×760 (**D15**). |
| `Sidebar` | Fixed 220 px dark nav with gold active bar. No user identity area, no badges. |
| `Theme`, `BasePanel` | Tokens + common header. Header date captured once (**D6**). |
| `DashboardPanel` | 4 stat cards + upcoming table. `emptyStateLabel` never added (**D9**). |
| `NewReservationPanel` (694 lines) | 3-step flow: customer → date/slot/guests/section → table cards. User must try slots one by one; dead-end message when nothing fits. |
| `ReservationsPanel` | Filters + table + Cancel / Mark Completed. `isSlotStarted` re-implements slot logic by parsing label strings in the UI (fragile). |
| `CustomerPanel` | Form + live debounced search. Good inline validation pattern (`FlatClientProperties.OUTLINE`). |
| `TablePanel` (801 lines) | Tabs: Manage / Floor View. "Floor view" is a wrapped **card grid**, not a spatial plan. |
| `ReportsPanel` | 5 reports + bar chart. |
| `TableCard`, `TableGridPanel`, `WrapLayout` | Custom-painted cards (150×120) in a wrap layout. Attractive; reusable for the fallback layout. |
| `Toast` | Non-blocking, but every toast uses the same screen position → overlap (**D16**). |
| `DateSelector` | Chips + spinner. Computed once at construction (**D6**); min date = today (**D13**). |
| `SearchableComboBox`, `AddCustomerDialog`, `BookingConfirmedDialog`, `StatCard`, `StatusPillRenderer`, `BarChartPanel` | Good small components; need the new statuses/colours and the shared kit (doc 04). |

### 3.8 Tests (2) and docs (10)
| File | Assessment |
|---|---|
| `ReservationServiceTest` (9), `ValidationUtilTest` (11) | 20 pure unit tests (`recommendTable`, validation). No DAO, DB, or concurrency test (**D18**). |
| `docs/01…05_*.md`, `ARCHITECTURE`, `ASSUMPTIONS`, `ER`, `NORMALIZATION`, `TEST_CASES` | Thorough and viva-friendly, but several contradict the code (**§5**). Docs 01–05 contain absolute links `file:///d:/Desktop/project/RTRS/…` that break on any other machine. |

## 4. Defect register

Severity = impact on a real restaurant shift, not on the lab demo.

| ID | Sev | Finding & evidence | Fix (phase) |
|---|---|---|---|
| D1 | High | `DatabaseConnection.getConnection` does `throw (DatabaseUnavailableException) OracleErrorMapper.map(...)`. The mapper returns `DataAccessException` for any code outside its connectivity list (e.g. `ORA-28000` locked account) → `ClassCastException`, bypassing the friendly error screen. | Throw the mapped `AppException`; map 28000/28001/1045 to "unavailable" (P0) |
| D2 | High | Only `CANCELLED`/`COMPLETED` are terminal. A no-show is either left `CONFIRMED` (blocks the slot) or "completed", and Reports 4–5 count it as a real booking. | Lifecycle + `NO_SHOW` (P3) |
| D3 | High | `ValidationUtil.validateReservation` and `findAvailableTables` reject any slot whose start ≤ now. A party that walks in cannot be recorded for the current slot at all. | Walk-in seating path (P3) |
| D4 | Med | Guest spinner/validation allow 1–20, but the largest seeded table seats 10 and a trigger forbids guests > capacity → 11–20 guests can *never* book, and the UI only says "no single table can seat…". | Cap spinner to max capacity + explicit message now; table-joining is an extension (P5/ext.) |
| D5 | Med | Validation uses client `LocalDate/LocalTime`; dashboard queries use DB `SYSDATE` and string-compare `end_time` with `TO_CHAR(SYSDATE,'HH24:MI')`. Different time zones or drifting clocks make the two disagree (15 `LocalDate.now()` vs 9 `SYSDATE` uses). | `DbClock` — one clock = DB clock (P0) |
| D6 | Med | `BasePanel` header date and `DateSelector` chips are computed in constructors; a screen left open past midnight shows yesterday as "Today". | Refresh in `onShown` + midnight timer (P2) |
| D7 | Med | `TableDAO.lockTable`: `SELECT … FOR UPDATE` has no `WAIT`. A stuck session freezes the booking worker indefinitely. | `FOR UPDATE WAIT 5`, map `ORA-30006` (P0) |
| D8 | Med | `NAME_PATTERN = ^[A-Za-z\s.'\-]{2,100}$` rejects names such as "José", "Müller", or any Tamil/Devanagari name. | `\p{L}\p{M}` classes (P0) |
| D9 | Low | `DashboardPanel.emptyStateLabel` is created, never added. | Use shared `EmptyState` (P2) |
| D10 | Low | One physical connection per DAO call; dashboard load opens ~6 serially. *Measure first.* | Tiny pool or Oracle UCP (P0, optional) |
| D11 | Low | Status literals repeated in 12 places (`TableDAO`, `ReservationDAO`, `ReportDAO`); adding statuses means editing all. | `SqlFragments.ACTIVE_STATUSES` (P0 — **prerequisite** for P3) |
| D12 | Low | `getUpcomingReservations` catches *any* `SQLException` and silently retries 11g SQL. | Catch only `ORA-00933` (P0) |
| D13 | Low | `DateSelector` spinner min = today → past floors/reservations cannot be inspected. | `minDate` parameter (P2) |
| D14 | Low | Contrast (WCAG 2.x AA needs 4.5:1 for normal text): white on gold `#C8963E` = **2.66**; `WARNING` text 3.64; maintenance grey 2.64; muted text on sidebar 3.56. | New tokens (doc 04 §3) |
| D15 | Low | Min window 1200×760; a 1366×768 laptop leaves ≈ 728 px above the taskbar. | Min 1100×680, scroll content (P2) |
| D16 | Low | `Toast` positions identically every time → overlap. | Toast queue/stack (P2) |
| D17 | Low | Duplicated UI builders: `createPrimaryButton` ×6, `createCardPanel` ×6, secondary ×4, danger ×3, three near-identical `JToggleButton` classes, 13 anonymous painted `JButton`s. | UI kit (P2) |
| D18 | Low | No DB/concurrency automated tests; "Concurrent Booking: PASS" in `TEST_CASES.md` is manual. One test uses a slot starting 00:01 and is wall-clock dependent. | Integration tests (P6) |
| D21 | Sec | No authentication. `LIKE` input wildcards unescaped (functional only). | Doc 02 |
| D22 | Med | Setting a table to `MAINTENANCE/INACTIVE` silently strands existing future `CONFIRMED` bookings. | Warn + list affected bookings (P3) |

## 5. Documentation ↔ code contradictions (fix before the viva)
1. `NORMALIZATION.md §5.2` shows a **4-column** index `(table_id, reservation_date, slot_id, CASE … ELSE NULL END)`. The real index (`01_schema.sql`) wraps **all three** columns in `CASE`. The documented variant would not free cancelled rows: Oracle documents that a partially-null composite unique key cannot repeat its non-null column values, so two cancelled rows for the same table/date/slot would collide. Verify with the 2-session demo on your XE instance, then correct the doc.
2. `ARCHITECTURE.md` and `TEST_CASES.md #9` name the trigger `TRG_CHECK_CAPACITY`; the schema's trigger is `trg_reservation_rules`.
3. `TEST_CASES.md #5` uses a "05:30 PM" slot; the seeded slots are 12:00, 1:30, 6:00, 7:30, 9:00 PM.
4. `ER.md` lists `customer.created_at`; the schema has no such column.
5. README FlatLaf version (3.5.4) ≠ `pom.xml` (3.5.1); `.env` key naming differs (see §3.1).
6. Docs 01–05 use `file:///d:/Desktop/project/RTRS/...` links.

## 6. Operational gaps (what a host actually needs on a Friday night)
| Real situation | What the app does today |
|---|---|
| Party doesn't show | Nothing. Slot stays blocked; later recorded as "completed" or left dangling. |
| Party cancels, others want that time | Table frees up, but nobody is told; guests who were turned away are not recorded anywhere. |
| Walk-in arrives | Cannot be entered (D3). |
| Guest phones: "anything tonight for 4?" | Host must try 5 slots one by one. |
| Table breaks mid-service | Status can be flipped, affected bookings are not shown (D22). |
| "Who did that?" | Unknowable — no users, no audit. |

These gaps, not decoration, drive the choices in docs 02–04.
