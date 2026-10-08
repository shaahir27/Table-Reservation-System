# 05 · Implementation plan — phases, files, tests, acceptance, viva

Effort figures are **my judgment** for one developer who knows the codebase (S ≈ ≤ 4 h, M ≈ 4–10 h, L ≈ 10–20 h). Total ≈ 85–120 h. Each phase leaves the app runnable and committed.

## Dependency order
`P0 hardening → P1 auth → P2 UI kit & shell → P3 lifecycle & Service Board → P4 waitlist & recovery → P5 matrix & floor plan → P6 reports, tests, docs`
**Cut line if time is short:** P0 + P1 + P3 + P4 (minimal UI, existing panels) is already a complete, defensible upgrade. P2/P5 are the "polish" tier.

---
## P0 — Hardening (≈ 6–9 h, M) — no schema change
| # | Task | Files | Done when |
|---|---|---|---|
| 0.1 | Throw mapped `AppException` (no cast); map `ORA-28000/28001/1045` to unavailable | `DatabaseConnection`, `OracleErrorMapper` | Locking the DB user shows the friendly error screen, not a stack trace (D1) |
| 0.2 | `FOR UPDATE WAIT 5`; map `ORA-30006` → "Table is busy, try again" | `TableDAO.lockTable`, mapper | Two sessions: the second gets the message within ~5 s (D7) |
| 0.3 | `DbClock` + replace 15 `LocalDate/LocalTime.now()` | new `util/DbClock`, validation, services, panels | Tests inject a fixed clock; no `now()` without a clock in `service`/`util` (D5) |
| 0.4 | Unicode names | `ValidationUtil.NAME_PATTERN` → `^[\p{L}\p{M}\s.'\-]{2,100}$` | "José", "முகமது" accepted; digits rejected (D8) |
| 0.5 | `ReservationStatus.BLOCKING` + `SqlFragments`; replace 12 literals | `ReservationStatus`, 3 DAOs | `grep "'CONFIRMED'"` finds only `SqlFragments` (D11) |
| 0.6 | Narrow the 11g fallback to `ORA-00933` | `ReservationDAO.getUpcomingReservations` | Real errors surface (D12) |
| 0.7 | Cap guest spinner at max table capacity with explanatory message | `NewReservationPanel`, `TableService.getMaxCapacity` | 11+ guests no longer a silent dead end (D4) |
| 0.8 | (Optional) tiny connection pool or Oracle UCP — **measure first** (time `getDashboardStats`) | `DatabaseConnection` | Only if dashboard load > ~1 s |
| 0.9 | Repo hygiene + doc fixes: delete `RTRS.zip` and logs; fix the 6 doc contradictions (audit §5); relative links | docs, README | Docs match code |
**Tests:** `DbClockTest`, extend `ValidationUtilTest`, mapper test for the new codes.

## P1 — Authentication & RBAC (≈ 14–20 h, L) — doc 02
Order: run `05_v2a_auth.sql` → `security/*` + `PasswordHasher/Policy` (+ unit tests) → `UserDAO/AuditDAO/SettingDAO` → `AuthService/UserService` → `AuthContext` injected into **all** services (constructor overload keeps existing tests compiling) → `LoginPanel`/`FirstRunSetupPanel` + `MainFrame.buildShell` → sidebar footer + role-filtered nav → `StaffPanel`, `ChangePasswordDialog` → `IdleLockMonitor`/`LockOverlay` → `AuditPanel`.
**Acceptance:** first run creates a manager; wrong password ×5 locks 5 min (survives restart); HOST calling `TableService.deleteTable` gets `AccessDeniedException`; every mutator writes an `audit_log` row in the same transaction; `UPDATE audit_log …` fails `ORA-20010`; idle lock after the configured minutes; last-manager guard holds.

## P2 — UI kit & shell (≈ 14–20 h, L) — doc 04 §2–4
Kit + tokens + accent → migrate Customers (pilot) → other panels → header clock, toasts stack, window prefs, `BusyBar`, sidebar rail/badges, global search.
**Acceptance:** no `createPrimaryButton`/`createCardPanel` left; contrast table in doc 04 §3 met; 1366×768 shows everything without clipping; toasts stack.

## P3 — Lifecycle & Service Board (≈ 12–16 h, L) — doc 03 §3–4
Run `06_v2b_lifecycle_waitlist.sql` (**stop all clients first**; it swaps the unique index) → extend enum/pills/DAOs (`seat`, `markNoShow`, `walkIn`, reconfirm update, `source`, `seated_at`) → `ReservationService.seat/markNoShow/complete/walkIn` with transition checks + audit → table-out-of-service dialog (D22) → Service Board + Walk-in dialog → Reconfirm tab → "Needs attention" for legacy rows → update reports for new statuses.
**Acceptance:** illegal transitions rejected in Java *and* by `ORA-20003`; walk-in seats into the current slot; OVERDUE flag appears exactly at `start + grace`; NO_SHOW frees the slot for rebooking; `04_double_booking_demo.sql` still passes.

## P4 — Waitlist & Slot Recovery (≈ 16–24 h, L) — doc 03 §5
`WaitlistDAO`, `WaitlistService`, `OfferPolicy` (+ `BestFit`, `Fifo`), `RecoveryService.fillSlot/sweep/accept/decline`, hook `fillSlot` into cancel / no-show / decline / expiry / table-restored → `Notifier` (`UiNotifier`) → Waitlist screen + Offers strip + countdown + sidebar badge → 30 s sweep `Timer` in `MainFrame` (runs through `Ui.async`).
**Acceptance:** the demo script in doc 03 §10.1 works end-to-end; two clients racing never double-offer (check `uq_active_booking` + waitlist row lock); accept-after-expiry shows "Offer expired"; `OfferPolicyTest` mirrors the verified cases.

## P5 — Matrix booking & Floor plan (≈ 14–20 h, L) — doc 03 §6, doc 04 §5.2–5.3
`AvailabilityDAO/Service` (one query) → `AvailabilityMatrixPanel` → rebuilt `NewReservationPanel` → run `07_v2c_floorplan.sql` → `FloorPlanPanel` (+ edit mode, save batch) → Dashboard timeline.
**Acceptance:** matrix loads in one round trip; red cell → waitlist dialog; plan renders chairs = capacity; layout edit persists; NULL positions fall back to cards; past dates viewable.

## P6 — Reports, tests, docs (≈ 10–14 h, M)
Reports R6–R10 (doc 03 §7) → `RecoverySimulator` + parameter sweep table → DB integration tests behind `RTRS_IT=1` (concurrent booking, release+offer race, lockout persistence, audit immutability, transition trigger) → update `03_drop_all.sql` (drop new triggers/tables/sequences/index in reverse order), `ER.md`, `NORMALIZATION.md`, `TEST_CASES.md` (add rows below), viva guide.

## New test-matrix rows to add to `TEST_CASES.md`
| # | Scenario | Expected |
|---|---|---|
| 16 | HOST opens Tables / deletes a table via service | Nav hidden; service throws `AccessDeniedException` |
| 17 | 5 wrong passwords, restart app | Still locked until `locked_until` |
| 18 | Edit/delete `audit_log` in SQL*Plus | `ORA-20010` |
| 19 | `CONFIRMED → COMPLETED` directly | `ORA-20003` |
| 20 | No-show released, waitlisted party exists | Offer appears; slot not visible as free in between |
| 21 | Accept offer after hold expiry | "Offer expired"; next party offered |
| 22 | Walk-in into the current slot | `SEATED`, `source=WALK_IN` |
| 23 | Table set to MAINTENANCE with future bookings | Affected-bookings dialog; nothing stranded |
| 24 | 12 guests, largest table 10 | Clear explanation, no search dead end |
| 25 | Past-midnight session | Header date / "Today" chip update |

## Normalization note for the new tables (prerequisite of the problem statement)
`app_user`, `audit_log`, `app_setting`, `waitlist_entry` each have a single-attribute key and every non-key attribute depends only on it → **BCNF**. No guest/table/slot attributes are copied into `waitlist_entry` or `audit_log` (only foreign keys), so no update anomalies. Users are deactivated, not deleted, so `audit_log.user_id` never dangles. The only deliberate denormalisation is none: `reservation.source/seated_at/hold_expires_at/reconfirm_status` are facts about the reservation itself. Document the new functional dependencies (`username → user_id…`, `entry_id → …`) in `NORMALIZATION.md`.

## Risks & mitigations
| Risk | Mitigation |
|---|---|
| Index swap in `06_` fails or leaves two indexes | Script builds the new index first; rollback script provided; run on a scratch schema, then take an export (`expdp`) before production. |
| Oracle SQL here is untested | Treat `sql/*.sql` as reviewed drafts: run them on a fresh XE schema, then run the 2-session demo before relying on them. |
| Scope creep | Phase cut line above; every phase has acceptance criteria. |
| Doc/code drift again | Part of each phase's "done" is the matching doc edit. |
| Auth bypass via direct DB | Stated openly in doc 02 §9; hardening options listed. |

## Likely viva questions (short answers)
1. *Where is double-booking stopped now that holds exist?* Same function-based unique index, now covering `HELD, CONFIRMED, SEATED, COMPLETED`; a hold is a real row, so the engine blocks it exactly like a booking.
2. *Why a trigger for status changes?* So no client — including SQL*Plus — can write an impossible history; Java checks give friendly messages, the trigger gives the guarantee.
3. *Why not store passwords with SHA-256?* Fast hashes are cheap to brute-force; PBKDF2 with a per-user salt and 600 000 iterations is deliberately slow.
4. *Why enforce permissions in services, not just hide buttons?* UI hiding is advisory; services are the trust boundary the UI cannot skip.
5. *Why not auto-release overdue bookings?* A wrong release costs a real guest; a 1-click human release costs seconds.
6. *Is it novel?* The parts exist in commercial products; the contribution is the transactional, DB-enforced, measurable design (state it plainly).
7. *Why not pooled table assignment?* Tested: no gain in the capacity-only model (0 / 100 000 differences) and it weakens the single-index guarantee.
8. *What does your simulator prove?* Sensitivity to assumptions, not a forecast; real rates come from the new reports.
