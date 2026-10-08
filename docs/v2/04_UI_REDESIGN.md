# 04 · UI / UX redesign — Swing + FlatLaf only (stack preserved)

No new UI library. Everything below uses Swing, Java2D and FlatLaf 3.5.x already in `pom.xml`. Verify FlatLaf property names against the 3.5 docs when you implement (they are stable, but I could not run the UI in the analysis environment).

## 1. Problems this fixes (from the audit)
Duplicated builders (D17), contrast failures (D14), stale dates (D6), overlapping toasts (D16), no empty states (D9), window too tall for 1366×768 (D15), floor "plan" that is really a card grid, no user identity/role on screen, no at-a-glance operational view.

## 2. Shared UI kit — package `ui.kit` (do this first, it shrinks every panel)
| Class | Replaces | Notes |
|---|---|---|
| `Buttons.primary/secondary/danger/ghost(text)` | 6× `createPrimaryButton`, 4× secondary, 3× danger, 13 painted anonymous buttons | Use `putClientProperty("FlatLaf.style", "arc:12; background:…; foreground:…; hoverBackground:…")` instead of overriding `paintComponent` — FlatLaf then supplies focus ring, disabled and pressed states for free. |
| `Card` (JPanel with rounded border + optional title/subtitle) | 6× `createCardPanel` | Padding from `Theme.SPACE_*`. |
| `Chip` / `ChipGroup` | `DateChipButton`, `SlotToggleButton`, `FloorSlotToggleButton`, `createFilterChip` | One painted toggle, single/multi select. |
| `Pill` + `StatusPillRenderer` | pill code | Add `HELD, SEATED, NO_SHOW, OVERDUE, WAITING, OFFERED, LAPSED, FULFILLED`. |
| `FormField` (label + input + inline error + hint) | label/error boilerplate in Customer/Table/AddCustomer | Wraps the existing `OUTLINE=error` pattern. |
| `EmptyState(icon, title, hint, action)` | unused `emptyStateLabel` | Used by every table/list. |
| `BusyBar` | per-button "Searching…" text | Thin indeterminate `JProgressBar` under the header, driven from `Ui.async` (counter, so overlapping calls don't flicker). |
| `ToastManager` | `Toast` | Queue; stack newest above older (offset by height + 8 px); max 3 visible. |
| `DataTable` helper | 6 hand-built `JTable`s | Row sorter on, zebra rows (`Table.alternateRowColor`), column widths, right-click menu hook, empty overlay, double-click → detail. |
| `Icons` | text glyphs ("⚠", "✓") | Tiny vector icons drawn with `Path2D` (calendar, user, lock, clock, bell, table) so they scale on HiDPI; no image files. |

Bootstrap in `Main` (before `FlatLightLaf.setup()`): `FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#9A6B1F"))` so focus rings, default buttons and selections follow the brand colour automatically.

## 3. Design tokens — fix contrast, keep the identity
Contrast ratios computed with the WCAG 2.x formula (4.5 : 1 needed for normal text):

| Use | Current | Ratio | Replace with | Ratio |
|---|---|---|---|---|
| White text on gold button | `#C8963E` | **2.66** ✗ | fill `#9A6B1F` (white text) **or** keep `#C8963E` with dark text `#1E2024` | 4.67 ✓ / 6.13 ✓ |
| Gold hover | `#B58534` | 3.30 ✗ | `#8F6118` | 5.40 ✓ |
| Warning text | `#B7791F` on white | 3.64 ✗ | `#8A5A0B` | 5.92 ✓ |
| Maintenance grey text | `#9AA0A6` | 2.64 ✗ | `#6B7078` | 4.98 ✓ |
| Muted text on dark sidebar | `#6B7078` | 3.56 ✗ | `#9A9FA9` | 6.68 ✓ |
| Body muted on white / on app bg | `#6B7078` | 4.98 / 4.54 ✓ | keep | — |
| Success / danger text on white | `#2E7D5B` / `#C0392B` | 5.00 / 5.44 ✓ | keep | — |
| Gold accent **on dark sidebar** | `#C8963E` | 6.66 ✓ | keep (logo, active bar) | — |

Recommendation: keep `#C8963E` as the *decorative* gold (logo, indicators, chips) and use `#9A6B1F` for filled buttons with white text. Add tokens `ACCENT_FILL`, `ACCENT_FILL_HOVER`, `WARNING_TEXT`, `SIDEBAR_MUTED`. Never convey state by colour alone: every status also has text, and table cards also get an icon/hatching (already true for maintenance).

Typography: keep Inter; add a 13 px `FONT_TABLE`, minimum text size 12 px; tabular numerals for counts/countdowns (set `font-features`-free alternative: right-align numbers and use a fixed-width pill for countdowns).

## 4. Application shell
* **Root cards:** `SPLASH → DB_ERROR → SETUP → LOGIN → APP` (see doc 02). Splash gets a determinate step list ("Connecting… Checking schema… Loading settings").
* **Window:** min 1100×680; content areas scroll instead of clipping; remember size/position/maximised in `java.util.prefs`; test at 1366×768 and at 125 %/150 % OS scaling.
* **Sidebar (220 → collapsible 72 px icon rail via a toggle):** brand, **+ New Reservation**, role-filtered nav with icons and **badges** (Waitlist = offers pending, Service = overdue), footer user card (initials avatar, name, role pill, menu: Change password / Lock / Sign out), connection dot (green/red) with tooltip showing DB user@service.
* **Header (BasePanel):** page title, **live clock + date from `DbClock`** (updated each minute; fixes D6), global search field (`Ctrl+K`) that jumps to a reservation by ID/name/phone via a `JPopupMenu` result list.
* **Keyboard:** `Ctrl+N` new booking, `Ctrl+W` walk-in, `Ctrl+K` search, `F5` refresh, `Esc` closes dialogs (already in two dialogs), Enter = default button everywhere (`rootPane.setDefaultButton`).
* **Role-based home:** HOST → Service Board; MANAGER → Dashboard.

## 5. Screens

### 5.1 Service Board (new, HOST home)
Columns = today's five slots (current one highlighted, ended ones collapsed). Each booking is a card: guest, party size, table, status pill, reconfirm dot. Per-card actions appear on hover/selection: **Seat · No-show · Release & offer**. OVERDUE cards turn amber with "12 min late". `HELD` cards show a countdown and are visually distinct (dashed border). Top bar: *Walk-in*, *Waitlist (n)*, *Refresh*. Empty slot columns show "N tables free — Seat a walk-in".

### 5.2 New Reservation (rebuilt around the Availability Matrix — doc 03 §6)
Left: party size stepper + section chips + matrix (custom-painted `AvailabilityMatrixPanel`, cells 88×44, keyboard arrow navigation). Right: selected cell summary → table choice (mini floor plan when positions exist, card grid otherwise) → phone-first customer field → **Confirm**. The existing `BookingConfirmedDialog` stays as the ticket (add "Reconfirm by phone" reminder line and the staff member who created it).

### 5.3 Floor plan (replaces the card-grid "Floor View")
`FloorPlanPanel extends JComponent`, Java2D:
* Coordinates in grid units (1 unit = 24 px) from `pos_x/pos_y` (script `07_v2c_floorplan.sql`); tables drawn as rounded rect or circle sized by capacity, with **chair dots around the edge = capacity**; label = table number; fill by state (available / reserved / seated / held / overdue / maintenance-hatched); `Timer` at 1 Hz repaints countdowns and a gentle pulse on OVERDUE.
* Click → side drawer with the booking (guest, phone, party, status, actions). Hover → tooltip.
* Time scrubber = date picker (allowing **past** dates, D13) + slot chips.
* **Edit layout (MANAGER only):** toggle; drag tables with `MouseMotionListener`, snap to grid, arrow-key nudge, shape toggle, *Save* writes all positions in one batch transaction, *Reset to auto-layout*. Tables with `NULL` position fall back to the existing `TableGridPanel` (keeps the old UI working before the layout is drawn).
* Legend row + zoom-to-fit.

### 5.4 Dashboard (MANAGER)
Row of 4 clickable stat cards (Today covers · Overdue now · Waitlist waiting · Recovered covers 7 d) → navigate with a pre-applied filter. Below: a **today timeline** (5 slot bars, fill = occupied tables / available tables) and "Needs attention" list. Replace the generic upcoming table with the same `DataTable` helper and an `EmptyState`.

### 5.5 Reservations
Same filters, but: status filter becomes chips; column "By" (created_by); right-click / toolbar actions switch on status (Seat, No-show, Complete, Cancel, Re-confirm); detail drawer on double-click shows the audit timeline for that reservation (`audit_log` rows for `entity_id`); export visible rows to CSV (`java.io`, no library).

### 5.6 Customers / Tables
Customers: show visit count and no-show count (from `NO_SHOW` rows) as small badges — simple counts, no scoring. Tables: moving a table to MAINTENANCE/INACTIVE opens the affected-bookings dialog (doc 03 §7); the section combo box becomes editable-with-suggestions from `getDistinctLocations()` instead of the hard-coded four.

### 5.7 Staff, Audit, Settings (MANAGER)
Staff and audit per doc 02. Settings: grace minutes, hold minutes, max waste seats, idle lock, lockout — validated spinners, each change audited (`SETTING_CHANGED`).

## 6. Motion & feedback (small, deliberate)
Fade-in for the matrix (alpha Timer 120 ms), shake on login failure, pulse on OVERDUE, toast slide (8 px), button "pressed" feedback via FlatLaf. All animations ≤ 200 ms and disabled when `prefs.reduceMotion` is set (checkbox in Settings).

## 7. Accessibility & robustness checklist
Every control reachable by Tab in logical order; mnemonics on dialog buttons; focus visible (FlatLaf default ring in accent colour); `setAccessibleName` on icon-only buttons and custom-painted components (`AccessibleContext`); tooltips on truncated cells; no information by colour alone; HiDPI-safe (use `Theme` sizes via `UIScale.scale(...)` for custom painting, not raw pixels); long text uses ellipsis plus tooltip.

## 8. Order of work (UI)
1. `ui.kit` + tokens + `Main` accent → migrate **one** panel (Customers) as the pilot, then the rest (mechanical).
2. Shell (login cards, sidebar footer, header clock, window prefs).
3. Service Board → Waitlist/Offers → Matrix booking → Floor plan (+editor) → Dashboard.
4. Polish (motion, shortcuts, CSV export), then a 1366×768 / 150 % scaling pass.
