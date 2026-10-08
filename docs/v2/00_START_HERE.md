# RTRS v2 — analysis & implementation plan (start here)

Project: Restaurant Table Reservation System (Project 13 — Swing, JDBC, Oracle). Stack is preserved: **Java 21, Swing + FlatLaf, JDBC, Oracle, Maven, JUnit 5; no new runtime dependency** (password hashing uses the JDK).

## What I did
Unzipped and read the whole project (80 files; `RTRS.zip` inside is a duplicate), cross-checked docs against code, ran two quick experiments (see below), and compiled/ran the reference Java for hashing, permissions and the waitlist offer policy.

## Findings in one screen
* **Strong base:** engine-level double-booking guard, clean layering, async UI. All preserved.
* **Real gaps:** no authentication; no way to record no-shows, walk-ins or refused guests (so the system cannot tell a free table from a wasted one); 11–20 guests can never book; client/DB clocks disagree; one `ClassCastException` bug on connection errors; contrast and layout issues; docs that contradict the code (e.g. the index DDL in `NORMALIZATION.md`). Full list: `01_CODEBASE_AUDIT.md` (defects D1–D22).

## Decisions
| Area | Decision | Doc |
|---|---|---|
| Authentication | **Staff login, two roles (MANAGER/HOST)**, PBKDF2 hashing, DB-persisted lockout, idle lock, append-only audit log, enforcement in services. Guest login only as an optional appendix (cannot be verified offline). | 02 |
| Novel idea | **Slot Recovery Loop**: reconfirm → arrival tracking → release (NO_SHOW/cancel) → waitlist offer with a timed HOLD that reuses the same unique index — plus a **One-Glance Availability Matrix** for fast booking. Targets the main real-life pain: empty tables from no-shows/cancellations and guests turned away. | 03 |
| Rejected | Pooled table assignment (tested: 0 gain in the current model), ML no-show prediction (no data), guest portal, variable slots. Table joining kept as an extension. | 03 §2 |
| UI | Shared `ui.kit`, contrast-fixed tokens, Service Board, matrix booking, real floor plan with editor — Swing only. | 04 |
| Delivery | 7 phases (P0–P6), each runnable, with acceptance criteria and a cut line. | 05 |

## Evidence I produced (so you can trust or challenge it)
* **Pooling test:** 100 000 random capacity-only scenarios → fixed best-fit never lost a party that re-assignment could seat. With section preferences differences appear (≈ 7.5 % of *synthetic* cases; depends on my invented mix).
* **Contrast:** computed WCAG ratios — white text on the current gold button is 2.66 : 1 (needs 4.5).
* **Code verified by running (JDK 21):** PBKDF2 known-answer vector, hash/verify, role matrix, offer-policy cases.

## What is *not* verified
Oracle SQL scripts were not run (no Oracle available) — run them on a scratch schema first. Swing code is described, not compiled. Effort numbers are estimates. Simulator results do not exist yet; the plan defines how to get them honestly.

## Files
| File | Content |
|---|---|
| `01_CODEBASE_AUDIT.md` | every file assessed, defect register, doc contradictions |
| `02_AUTHENTICATION_AND_RBAC.md` | design, schema, flows, code, tests, threat model |
| `03_SLOT_RECOVERY_LOOP.md` | idea selection, state machine, algorithms, SQL, KPIs, simulation |
| `04_UI_REDESIGN.md` | kit, tokens, shell, every screen |
| `05_IMPLEMENTATION_PLAN.md` | phases, tasks, acceptance, test rows, risks, viva |
| `sql/05…08` | migrations A/B/C and rollback |

Suggested first step: Phase P0 (a day of work, no schema change), then P1.
