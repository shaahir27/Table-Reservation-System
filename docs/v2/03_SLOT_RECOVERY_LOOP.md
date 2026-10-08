# 03 · The novel feature — **Slot Recovery Loop** (+ One-Glance Availability Matrix)

## 1. One-paragraph pitch
Every restaurant loses money in the same two ways: **a table sits empty because a booking was cancelled or never showed**, and **a guest is turned away at the exact moment that table becomes free**. Today RTRS cannot even *see* either event (no check-in, no no-show state, no waitlist, no record of who was refused). The Slot Recovery Loop closes that gap in four connected steps — **Prevent → Detect → Release → Refill** — and a one-screen *Availability Matrix* removes the guess-work from taking a booking in the first place.

```
 Prevent                Detect                    Release                    Refill
 Reconfirmation   →   Arrival tracking     →   NO_SHOW / CANCELLED    →   Waitlist offer with a timed HOLD
 call list             (SEATED / OVERDUE)       frees the slot (same txn)    HOLD → CONFIRMED / SEATED  or  expires → next in line
```

### Honest novelty statement
Waitlists, reconfirmation and no-show handling are *established practice* in commercial restaurant software, so this is not a new invention. What is distinctive for this project is the **database-native design**: the freed slot and the offer happen in *one transaction*, the offer is a real reservation row in a `HELD` state protected by the *same* `UQ_ACTIVE_BOOKING` index, legal state changes are enforced by a trigger, and the whole loop is measurable with SQL. That keeps the project's signature strength (engine-level double-booking prevention) and extends it, rather than bolting on an unrelated feature.

## 2. Why this idea (and not the others) — options I weighed

Scored 1–5 (5 = best) on: **Pain** (real-world severity), **Evidence** (a visible gap in *this* codebase), **Fit** (stack, DB showcase), **Risk** (5 = low), **Effort** (5 = small).

| # | Idea | Pain | Evidence | Fit | Risk | Effort | Verdict |
|---|---|:-:|:-:|:-:|:-:|:-:|---|
| 1 | **Slot Recovery Loop** (lifecycle + waitlist + holds + reconfirm list) | 5 | 5 | 5 | 4 | 3 | ✅ **Chosen** |
| 2 | One-Glance Availability Matrix (quick booking) | 3 | 4 | 5 | 5 | 4 | ✅ Included as the "front door" of #1 (cells with 0 free → *Join waitlist*) |
| 3 | Pooled / deferred table assignment (bipartite matching) | 2* | 1 | 4 | 2 | 2 | ❌ Rejected — see §2.1 |
| 4 | Table joining for parties > largest table | 3 | 4 (D4) | 4 | 3 | 2 | ⏸ Documented extension (§9) |
| 5 | ML no-show prediction | 3 | 1 | 2 | 2 | 1 | ❌ No training data (8 seeded rows); a plain per-customer no-show count from `NO_SHOW` rows gives the same signal honestly |
| 6 | Guest portal / QR booking | 3 | 2 | 1 | 2 | 1 | ❌ Needs verified identity + another channel (see doc 02 §1) |
| 7 | Variable-length slots | 2 | 2 | 2 | 1 | 1 | ❌ Changes the unique-key design that the project is built on |

### 2.1 I tested idea 3 before dismissing it
My first instinct was to *defer* table choice (book "a table for 4" and assign later, so the system could re-shuffle). I checked whether that would let the restaurant accept more bookings than today's best-fit assignment:

* **Capacity-only model** (current app): brute-force over 100 000 random table sets and arrival sequences — fixed best-fit **never** rejected a party that perfect re-assignment could have seated (0 differences). This also follows from an exchange argument: best-fit on a single capacity dimension is optimal for any arrival order. So pooling brings **nothing** here.
* **With "I want Window/Outdoor" preferences:** differences do appear (≈ 7.5 % of *synthetic* scenarios in which 60 % of parties state a section). That number depends entirely on my invented demand mix — it shows a *mechanism*, not a real-world rate.

Conclusion: the gain is limited to section-preference conflicts, while the risk is real (aggregate capacity cannot be a declarative constraint, so the unique index would stop being the whole guarantee). Not worth it as the headline. If section preferences become common, revisit.

## 3. The reservation lifecycle (the foundation)

Six statuses (script `06_v2b_lifecycle_waitlist.sql`). *Blocking* statuses (they occupy the slot in `UQ_ACTIVE_BOOKING`): `HELD, CONFIRMED, SEATED, COMPLETED`. *Freeing*: `CANCELLED, NO_SHOW`.

| From → To | Meaning | Who | Notes |
|---|---|---|---|
| (insert) → CONFIRMED | normal booking | host | `source = BOOKING` |
| (insert) → SEATED | **walk-in** seated now | host | `source = WALK_IN`; slot must be in progress |
| (insert) → HELD | waitlist offer | system | `hold_expires_at` required (CHECK) |
| HELD → CONFIRMED / SEATED | guest accepts | host | `UPDATE … WHERE status='HELD' AND hold_expires_at > :now` (0 rows = expired) |
| HELD → CANCELLED | guest declines, or hold expires | host / sweep | triggers next offer |
| CONFIRMED → SEATED | guest arrived | host | sets `seated_at` |
| CONFIRMED → CANCELLED | guest cancelled | host | frees slot → Refill |
| CONFIRMED → NO_SHOW | released after grace | host | frees slot → Refill |
| SEATED → COMPLETED | meal finished | host | only path to COMPLETED |

Illegal moves (e.g. `COMPLETED → CONFIRMED`, `CONFIRMED → COMPLETED`) raise `ORA-20003` from trigger `trg_reservation_status_flow`, so even a script or SQL*Plus session cannot corrupt the history. The old "Mark Completed on a never-attended booking" becomes **Seat → Complete**; legacy past `CONFIRMED` rows appear in a *"Needs attention"* list where the host picks *Arrived (backfill)* or *No-show*.

### 3.1 Derived states (never stored)
* **OVERDUE** = `CONFIRMED` ∧ date = today ∧ `now ≥ slot.start + grace_minutes` (default 15). Computed with the DB clock (`DbClock`, §7). The system **flags** the booking; a human clicks *Release*. I deliberately do **not** auto-release: wrongly releasing a guest who is parking the car is worse than a short delay.
* **AT RISK** = tomorrow's / today's `CONFIRMED` with `reconfirm_status = 'PENDING'` after midday. Visual only.

## 4. Prevent — the reconfirmation list
`reservation.reconfirm_status ∈ {PENDING, CONFIRMED, NO_ANSWER}`.
Screen *Today → Reconfirm* lists tomorrow's bookings (soonest first) with the guest's phone, one-tap **Confirmed / No answer / Cancel**. *Cancel* flows into Refill. There is no SMS gateway, so the app produces the **call list** (what restaurants do by phone anyway) behind a `Notifier` interface:

```java
public interface Notifier {                       // Strategy pattern — plug SMS/e-mail later
    void offerCreated(WaitlistEntry e, Reservation held, RestaurantTable t);
    void offerExpired(WaitlistEntry e);
}
public class UiNotifier implements Notifier { /* badge + toast + Offers panel */ }
public class LogNotifier implements Notifier { /* AppLog only; used in tests */ }
```

## 5. Refill — waitlist, offers, holds

### 5.1 Joining the waitlist
Offered only where it makes sense: from a **red cell** of the matrix (no table fits that slot) or from the Waitlist screen. Fields: customer (required — we must be able to phone them), date, slot **or "any slot"**, party size, optional section, note. One live entry per customer/date/slot (`uq_active_waitlist`).

### 5.2 `fillSlot(con, date, slotId)` — level-triggered, idempotent
Run (a) inside the transaction of every capacity-freeing action (cancel, release no-show, decline, hold-expiry, table back to AVAILABLE) and (b) by a periodic sweep. Because it recomputes from current rows, it is safe to run twice and heals after a crashed client.

```
freeTables  = tables with status AVAILABLE and no blocking reservation for (date, slot), ordered by capacity DESC
for each table T in freeTables:
    candidates = WAITING entries for (date, slot or ANY) with party ≤ T.capacity
                 and (pref IS NULL or pref = T.location)
                 and customer has no blocking reservation in this slot
                 → locked with  SELECT … FOR UPDATE WAIT 5
    pick = OfferPolicy.pick(T, candidates)               // §5.3
    if pick: INSERT reservation(status 'HELD', source 'WAITLIST', hold_expires_at = SYSTIMESTAMP + hold_minutes)
             UPDATE waitlist_entry SET status='OFFERED', offered_reservation_id = :newId
             audit OFFER_MADE
```
* Largest table first, so a big party is not stranded by a small party taking the big table.
* If the `INSERT` hits `ORA-00001` (`UQ_ACTIVE_BOOKING`) another client got there first → skip that table; the engine, not Java, arbitrates.
* Hold length = `min(hold_minutes, minutes until slot end)`; never offer for a slot that has ended.

### 5.3 `OfferPolicy` (Strategy) — default **BestFit**
Order of preference: (1) **least wasted seats** (`capacity − party`), (2) a party that **explicitly asked** for this section beats one that did not, (3) **FIFO** (`created_at`). Hard filter: waste ≤ `max_waste_seats` (default 2) so a 10-top is not auto-offered to a couple; the host can still **Offer manually** outside the rule. Trade-off, stated plainly: the waste cap protects large tables from small parties but can leave a table empty — tune it using the simulator (§10). `FifoPolicy` is provided for comparison.

Verified reference implementation (compiled and run; all assertions passed):
```java
static Optional<Waiting> pickBestFit(FreedTable t, List<Waiting> waiting, int maxWaste) {
    return waiting.stream()
        .filter(w -> w.party() <= t.capacity())
        .filter(w -> w.prefLocation() == null || w.prefLocation().equalsIgnoreCase(t.location()))
        .filter(w -> t.capacity() - w.party() <= maxWaste)
        .min(Comparator.comparingInt((Waiting w) -> t.capacity() - w.party())        // 1. least waste
             .thenComparingInt(w -> w.prefLocation() != null ? 0 : 1)                // 2. explicit section wins ties
             .thenComparingLong(Waiting::createdOrder));                             // 3. FIFO
}
```
Checked cases: 4-top Indoor → earliest 4-party; 4-top Window → the Window-requesting party beats an earlier no-preference party; 8-top → the 6-party (waste 2); 10-top with waste cap 2 → no auto-offer; a party larger than the table is never offered; pure ties → FIFO.

### 5.4 Offer lifecycle
```
WAITING ──fillSlot──► OFFERED ──accept──► FULFILLED   (reservation HELD→CONFIRMED/SEATED)
                         │ ├─decline──► CANCELLED    (reservation HELD→CANCELLED, next in line)
                         │ └─expire───► LAPSED       (sweep; next in line)
WAITING ──host/guest cancels──► CANCELLED      WAITING ──date passed──► LAPSED
```
**Accept** is race-safe: `UPDATE reservation SET status = :newStatus, hold_expires_at = NULL, seated_at = … WHERE reservation_id = :id AND status = 'HELD' AND hold_expires_at > SYSTIMESTAMP` → 0 rows means "offer expired", shown to the host, never a silent double-seating.

**Expiry sweep** (every client runs it every 30 s; atomic in the DB, so concurrent sweeps are harmless). Capture *one* timestamp so the two statements agree:
```sql
-- :now  = SELECT SYSTIMESTAMP FROM dual   (fetched once, passed to both statements, one transaction)
UPDATE waitlist_entry w SET status = 'LAPSED'
 WHERE status = 'OFFERED'
   AND EXISTS (SELECT 1 FROM reservation r
                WHERE r.reservation_id = w.offered_reservation_id
                  AND r.status = 'HELD' AND r.hold_expires_at < :now);
UPDATE reservation SET status = 'CANCELLED'
 WHERE status = 'HELD' AND hold_expires_at < :now;
-- then fillSlot() for each affected (date, slot)
```
(`UQ_ACTIVE_BOOKING` cannot reference `SYSTIMESTAMP`, so expiry must be an explicit sweep; a `DBMS_SCHEDULER` job would be the server-side alternative but needs the `CREATE JOB` privilege that `CONNECT, RESOURCE` does not grant.)

### 5.5 Why "release + offer" in one transaction matters
`releaseNoShow` does: lock table row → lock reservation `WAIT 5` → `CONFIRMED→NO_SHOW` → `fillSlot` → commit. Lock order is always **table → reservation → waitlist**, matching `bookTable`, to avoid deadlocks. Expected behaviour (verify with the two-session script, §11): another session trying to book the same slot waits for the commit and then receives `ORA-00001`, because the `HELD` row already owns the key — the freed slot is never visible as "free" between release and offer.

## 6. One-Glance Availability Matrix (the easy-to-use front door)

**Today:** pick customer → date → slot → press Find → read table cards; to learn whether *any* other time works the host repeats the search per slot. **New:** set the party size once and see all slots × next 7 days.

```
 Guests [ − 4 + ]   Section (Any)(Indoor)(Window)(Outdoor)(Hall)
                Thu 08   Fri 09   Sat 10   Sun 11   Mon 12 …
 12:00 PM        ● 5      ● 4      ◐ 1      ● 5      ● 5
 01:30 PM        ● 5      ● 5      ◐ 2      ● 5      ● 5
 06:00 PM        ◐ 2      ○ WL 1   ○ WL 3   ● 3      ● 4          ● ≥ 25 % of fitting tables free
 07:30 PM        ○ WL 0   ○ WL 2   ○ WL 4   ◐ 1      ● 3          ◐ few left   ○ none → join waitlist (WL n waiting)
 09:00 PM        ● 3      ◐ 2      ◐ 1      ● 4      ● 5          (past slots today greyed out)
```
* One SQL statement returns the whole grid (dates from `CONNECT BY LEVEL`, cross join `time_slot`, correlated counts of free fitting tables and waiting entries) — a single round-trip instead of 5–35 searches.
* Cell colour: free = 0 → red; free ≤ max(1, ⌈25 % of fitting tables⌉) → amber; else green. (Rule is relative to how many tables *could* fit, so it scales with the restaurant.)
* **Click a cell** → right pane lists the fitting tables with the best-fit preselected (existing `recommendTable`) → *Confirm*. **Click a red cell** → *Join waitlist* dialog prefilled.
* **Customer last, phone first:** one field accepts a phone or name; digits trigger an instant lookup; one match auto-selects; no match reveals an inline *Create "…"* (name only; phone already typed).
* Guest-count spinner is capped at the **largest active table capacity**; above it the pane explains why and (when implemented) offers table joining — fixes D4's silent dead-end.
* Design target (not measured): booking drops from ≈ 7+ interactions and up to 5 searches to ≈ 3 clicks plus typing the phone number. Measure with a stopwatch test on 5 volunteers before claiming it.

```sql
-- availability grid (Oracle 11g-compatible)
SELECT d.service_date, s.slot_id, s.slot_label,
       (SELECT COUNT(*) FROM restaurant_table t
         WHERE t.status = 'AVAILABLE' AND t.capacity >= ?
           AND (? IS NULL OR t.location = ?)
           AND NOT EXISTS (SELECT 1 FROM reservation r
                            WHERE r.table_id = t.table_id AND r.reservation_date = d.service_date
                              AND r.slot_id = s.slot_id
                              AND r.status IN ('HELD','CONFIRMED','SEATED','COMPLETED'))) AS free_tables,
       (SELECT COUNT(*) FROM waitlist_entry w
         WHERE w.service_date = d.service_date AND w.status = 'WAITING'
           AND (w.slot_id = s.slot_id OR w.slot_id IS NULL)) AS waiting
  FROM (SELECT TRUNC(SYSDATE) + LEVEL - 1 AS service_date FROM dual CONNECT BY LEVEL <= ?) d
 CROSS JOIN time_slot s
 ORDER BY d.service_date, s.start_time
```

## 7. Cross-cutting design decisions

**One clock (`DbClock`, fixes D5).** At login (and every 5 min) run `SELECT TO_CHAR(SYSTIMESTAMP,'YYYY-MM-DD"T"HH24:MI:SS.FF3TZH:TZM') FROM dual`, parse as `OffsetDateTime`, store `offsetMs = dbInstant − System.currentTimeMillis()`. `DbClock extends java.time.Clock` returns `System.currentTimeMillis() + offsetMs` in the DB's zone. All `LocalDate.now()` / `LocalTime.now()` in validation, panels and services become `LocalDate.now(clock)`; tests inject a fixed clock (also fixes the wall-clock-dependent test).

**Central status list (D11).** `ReservationStatus.BLOCKING = {HELD, CONFIRMED, SEATED, COMPLETED}` and `SqlFragments.BLOCKING_IN = "('HELD','CONFIRMED','SEATED','COMPLETED')"`; replace all 12 literals. Do this *first* — it is the safest way to introduce the new statuses.

**Table taken out of service (D22).** Before setting `MAINTENANCE/INACTIVE`, `TableService` lists future blocking reservations on it; the dialog offers **Move to best-fit free table** (re-uses `recommendTable`) or **Cancel and offer to waitlist**. Nothing is stranded silently.

**Walk-ins.** *Seat now* button (Service Board): pick customer (phone-first), party size → best-fit free table for the **current** slot → insert `SEATED`, `source=WALK_IN`. Validation gets `allowStartedSlot = true` only on this path.

**Reports** (new, MANAGER): *No-show rate* (by day/slot), *Slot utilisation*, *Waitlist funnel* (joined → offered → fulfilled / lapsed), *Recovered covers*, *Reconfirmation outcomes*. Existing Reports 4–5 switch to `BLOCKING` minus `HELD`.
```sql
-- recovered covers (the headline KPI)
SELECT COUNT(*) AS parties, NVL(SUM(guest_count),0) AS covers
  FROM reservation WHERE source = 'WAITLIST' AND status IN ('SEATED','COMPLETED');
-- no-show rate, resolved past bookings only
SELECT reservation_date,
       SUM(CASE WHEN status = 'NO_SHOW' THEN 1 ELSE 0 END) AS no_shows, COUNT(*) AS resolved,
       ROUND(100 * SUM(CASE WHEN status = 'NO_SHOW' THEN 1 ELSE 0 END) / COUNT(*), 1) AS no_show_pct
  FROM reservation
 WHERE status IN ('NO_SHOW','SEATED','COMPLETED') AND reservation_date < TRUNC(SYSDATE)
 GROUP BY reservation_date ORDER BY reservation_date DESC;
-- table-slot utilisation over a date range  (:from, :to inclusive)
SELECT ROUND(100 * (SELECT COUNT(*) FROM reservation
                     WHERE status IN ('SEATED','COMPLETED') AND reservation_date BETWEEN :from AND :to)
       / ((SELECT COUNT(*) FROM restaurant_table WHERE status = 'AVAILABLE')
          * (SELECT COUNT(*) FROM time_slot) * (:to - :from + 1)), 1) AS utilisation_pct FROM dual;
```

## 8. Screens
* **Service Board** (HOST home): columns = today's slots; each booking is a card with **Seat / No-show / Release** actions, OVERDUE cards amber with minutes late, `HELD` cards show a live countdown; header buttons *Walk-in* and *Waitlist*; sidebar badges show *offers waiting* and *overdue* counts (polled with the 30 s sweep).
* **Waitlist** screen: live list ordered by `created_at`, status pills, *Offer manually*, *Remove*; **Offers** strip at top with Accept / Decline and countdown.
* **Reconfirm** tab (tomorrow's calls).
* **Dashboard** (MANAGER home): stat cards become *Today covers · Overdue now · Waitlist waiting · Recovered covers (7 d)*; plus the "Needs attention" card (legacy unresolved bookings).

## 9. Extension (not in the core plan): table joining
Parties above the largest table (D4) need *join groups* (e.g. T02+T03 are adjacent). Sketch: `table_join(group_id, table_id)` + `join_group(group_id, effective_capacity)`; a booking on a group inserts **one reservation row per member table** sharing a `booking_group_id`, so `UQ_ACTIVE_BOOKING` still blocks every member. Deferred because it touches the cancel/seat/complete paths of several rows at once.

## 10. How to prove it works (for the viva and the report)
1. **Deterministic demo script** (add to `sql/` and the docs): book T05 tomorrow 7:30 PM → Divya joins the waitlist for 7 guests (no table fits) → cancel the T05 booking → an offer to Divya appears with a 10-minute hold → accept → `HELD→CONFIRMED`, waitlist `FULFILLED`, KPI "recovered covers" = 7.
2. **Two-session SQL proof** (extend `04_double_booking_demo.sql`): while Session A has an uncommitted release+offer, Session B's insert for the same slot blocks, then fails with `ORA-00001`.
3. **Illegal transition proof:** `UPDATE reservation SET status='COMPLETED' WHERE status='CONFIRMED'` → `ORA-20003`.
4. **Monte-Carlo simulator** (`src/test/java/tools/RecoverySimulator`, plain `main`): one service night on the seeded 8 tables × 5 slots; inputs — arrival rate per slot, party-size mix, no-show probability *p*, late-cancel probability *q*, probability a refused guest joins the waitlist, offer-acceptance probability; compare **baseline** vs **loop**; metrics — covers served, empty table-slots, offers accepted; 10 000 nights per parameter set, report mean ± 95 % CI and a sensitivity table over *p ∈ {5 %, 10 %, 20 %}*. **Caveat to state in the report:** results show how the benefit scales with the assumptions, not a forecast; replace the assumptions with the restaurant's real *no-show rate* and *turn-away count* once the new reports have collected a few weeks of data.

## 11. Edge cases & decisions
| Case | Behaviour |
|---|---|
| Two clients free two tables at once, one waiting party | Waitlist row lock serialises; the second `fillSlot` finds the entry `OFFERED` and skips it. |
| Accept clicked after the hold expired | 0 rows updated → "Offer expired" → next in line is offered. |
| Customer on waitlist already has a booking in that slot | Skipped (cannot dine twice in one seating); other slots still allowed. |
| Slot already ended / date passed | No offer; waiting entries for past dates → `LAPSED` by the sweep. |
| Waiting party's `any slot` entry | Considered for every non-ended slot of that day, earliest first. |
| Customer deleted while waiting | FK blocks the delete (`ORA-02292`) with the existing friendly message. |
| Offers made by another client | Seen within ≤ 30 s (polling, not push) or immediately via *Refresh*. |
| `HELD` rows in reports | Excluded from "bookings" until they become `CONFIRMED`/`SEATED`. |
| Nobody fits within the waste cap | No auto-offer; the table stays visibly free; host can *Offer manually*. |
