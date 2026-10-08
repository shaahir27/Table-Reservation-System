# 02 · Authentication, Roles & Audit — design and implementation plan

## 1. Decision

**Implement staff authentication with two roles (MANAGER, HOST), server-side permission checks, an append-only audit log, lockout, idle-lock and a managed first-run.** Guests do *not* get accounts in the core plan.

### Why staff auth, not guest auth (options weighed)
| Option | Verdict | Reasoning |
|---|---|---|
| **A. Staff login + RBAC** | ✅ chosen | Matches how the app is actually used: staff enter bookings for guests. Gives accountability (who cancelled?), separation of duties (hosts must not edit tables or read revenue-style reports), and is fully verifiable offline. |
| B. Guest accounts / portal | ❌ not core | The problem statement says customers book "through a Swing/AWT interface", but a guest cannot be *verified* without SMS/e-mail (not available in a Swing-only, offline lab setup). Without verification, a guest login only protects nothing. Offered as an optional appendix (§10) using in-person enrolment, which *is* verifiable. |
| C. One Oracle DB user per employee | ❌ | Strongest isolation, but needs DBA rights on every machine, is not portable to the lab, and complicates connection config. Listed as the "hardening" path (§9). |

## 2. Roles and permissions

| Permission | HOST | MANAGER |
|---|:-:|:-:|
| Create reservation / walk-in, join waitlist | ✅ | ✅ |
| Cancel, seat, mark no-show, accept/decline offers | ✅ | ✅ |
| Add/edit customers | ✅ | ✅ |
| Delete customers | ❌ | ✅ |
| Create/edit/delete tables, edit floor layout | ❌ | ✅ |
| Reports & analytics, audit log | ❌ | ✅ |
| Manage staff accounts, settings | ❌ | ✅ |

Enforcement happens **in the service layer**; hiding a sidebar item is only cosmetic. A reviewer will test exactly this: "what if I call `TableService.deleteTable` while logged in as HOST?" → `AccessDeniedException`.

### Verified reference code (compiled and run on JDK 21; see test output in §8)
```java
package security;

public enum Permission {
    RESERVATION_CREATE, RESERVATION_CANCEL, RESERVATION_SEAT, RESERVATION_NOSHOW, WAITLIST_MANAGE,
    CUSTOMER_EDIT, CUSTOMER_DELETE, TABLE_MANAGE, FLOOR_LAYOUT_EDIT,
    REPORT_VIEW, USER_MANAGE, AUDIT_VIEW, SETTINGS_EDIT
}

public enum Role {
    HOST(EnumSet.of(Permission.RESERVATION_CREATE, Permission.RESERVATION_CANCEL, Permission.RESERVATION_SEAT,
            Permission.RESERVATION_NOSHOW, Permission.WAITLIST_MANAGE, Permission.CUSTOMER_EDIT)),
    MANAGER(EnumSet.allOf(Permission.class));

    private final Set<Permission> perms;
    Role(Set<Permission> p) { perms = Collections.unmodifiableSet(p); }
    public boolean can(Permission p) { return perms.contains(p); }
}
```
```java
// security/AuthContext.java  — injected into services so they are unit-testable without a Swing session
public interface AuthContext {
    AppUser currentUser();                       // null when signed out
    default void require(Permission p) {
        AppUser u = currentUser();
        if (u == null)            throw new AccessDeniedException("Please sign in.");
        if (!u.getRole().can(p))  throw new AccessDeniedException("Your role (" + u.getRole() + ") cannot perform this action.");
    }
    static AuthContext fixed(AppUser u) { return () -> u; }   // for tests
}
// security/Session.java implements AuthContext { private static volatile AppUser current; ... }
```
Service pattern (apply to **every** mutating method):
```java
public void deleteTable(int tableId) {
    auth.require(Permission.TABLE_MANAGE);          // 1) authorize first
    ...existing code...
    audit.log(con, "TABLE_DELETED", "TABLE", tableId, null);   // 2) audit in the SAME transaction
}
```

## 3. Password storage

* **Algorithm:** PBKDF2-WithHmac-SHA256 from the JDK (`SecretKeyFactory`) — *no new dependency*.
* **Work factor:** 600 000 iterations (OWASP's current guidance for PBKDF2-HMAC-SHA256). Measured on the analysis sandbox: roughly 0.2–0.8 s per hash, acceptable for a human login and run off the EDT via `Ui.async`.
* **Per-user 16-byte random salt**, per-row iteration count (so the factor can be raised later and old hashes **upgraded transparently on next successful login**).
* **Constant-time comparison** (`MessageDigest.isEqual`), `char[]` passwords, `PBEKeySpec.clearPassword()`.
* **Storage:** `pwd_hash` (44 Base64 chars), `pwd_salt` (24), `pwd_iter` — columns in `app_user` (script `05_v2a_auth.sql`).

```java
package security;

public final class PasswordHasher {
    public static final String ALGO = "PBKDF2WithHmacSHA256";
    public static final int SALT_BYTES = 16, KEY_BITS = 256, CURRENT_ITERATIONS = 600_000;
    private static final SecureRandom RNG = new SecureRandom();
    public record Hashed(String hashB64, String saltB64, int iterations) {}

    public static Hashed hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES]; RNG.nextBytes(salt);
        byte[] dk = derive(password, salt, CURRENT_ITERATIONS, KEY_BITS);
        return new Hashed(b64(dk), b64(salt), CURRENT_ITERATIONS);
    }
    public static boolean verify(char[] password, String hashB64, String saltB64, int iterations) {
        byte[] expected = Base64.getDecoder().decode(hashB64);
        byte[] actual   = derive(password, Base64.getDecoder().decode(saltB64), iterations, expected.length * 8);
        return MessageDigest.isEqual(expected, actual);
    }
    public static boolean needsRehash(int storedIterations) { return storedIterations < CURRENT_ITERATIONS; }

    static byte[] derive(char[] pw, byte[] salt, int iter, int bits) {
        PBEKeySpec spec = new PBEKeySpec(pw, salt, iter, bits);
        try { return SecretKeyFactory.getInstance(ALGO).generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }
    private static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }
}
```
**Password policy** (`PasswordPolicy.validate`): ≥ 10 characters, ≠ username, not in an embedded list of ~100 common passwords, max 128 chars. (Following NIST SP 800-63B: length and deny-list rather than forced symbol rules.) Show a simple strength bar in the UI.

## 4. Login flow (all steps run in `Ui.async`)

```
Splash → DB check ─┬─ app_user empty? ──► FirstRunSetupPanel (create the first MANAGER)
                   └─ else ─────────────► LoginPanel ──► AuthService.login(username, char[])
                                                           │ ok → (must_change_pwd? ChangePasswordDialog) → buildShell(user)
                                                           └ fail → generic message / lock countdown
```
`AuthService.login`:
1. Normalise username (`trim().toLowerCase()`).
2. Load row. **Unknown user →** still run one dummy PBKDF2 derivation (equalises timing) and fail with the *same* generic message `"Invalid username or password."`.
3. If `is_active = 0` → same generic failure (do not reveal that the account exists).
4. If `locked_until > SYSTIMESTAMP` (DB time, not client time) → `AccountLockedException(secondsRemaining)` **before** verifying the password.
5. Verify. On failure, one atomic statement increments and maybe locks:
   ```sql
   UPDATE app_user
      SET failed_attempts = failed_attempts + 1,
          locked_until    = CASE WHEN failed_attempts + 1 >= :threshold
                                 THEN SYSTIMESTAMP + NUMTODSINTERVAL(:lock_minutes, 'MINUTE')
                                 ELSE locked_until END
    WHERE user_id = :id
   ```
   (In an Oracle `UPDATE`, right-hand sides see the *old* row, so `failed_attempts + 1` is the new count.) Audit `LOGIN_FAILED` / `LOGIN_LOCKED`.
6. On success: `failed_attempts = 0, locked_until = NULL, last_login_at = SYSTIMESTAMP`; if `PasswordHasher.needsRehash(iter)` re-hash and store; audit `LOGIN_OK`; set `Session`.
The lock state lives in the database, so restarting the app does not reset it.

**First run:** the repository ships **no default password**. When `SELECT COUNT(*) FROM app_user` is 0 the app shows the setup screen. (Two clients racing here could create two managers — acceptable for this scale; note it in the viva.)

## 5. UI specification (Swing + FlatLaf only)

**`LoginPanel`** (new card in `MainFrame` root `CardLayout`)
* Split layout: left 40 % dark brand panel (`SIDEBAR_BG`, gold wordmark, one-line tagline), right card with the form, max width 380 px, vertically centred.
* Fields: Username, Password with FlatLaf reveal button (`FlatClientProperties.STYLE` → `showRevealButton: true`), "Remember username" (`java.util.prefs.Preferences`; **never** the password).
* **Caps-Lock hint** via `Toolkit.getLockingKeyState(KeyEvent.VK_CAPS_LOCK)` on focus/key events.
* Enter submits; button shows "Signing in…" and fields disable while PBKDF2 runs; error text under the form with `OUTLINE=error`; a short horizontal shake (Timer, 8 frames) on failure; lock message with a live `mm:ss` countdown.
* Accessibility: tab order username → password → button; mnemonic on the button; focus lands on username.

**Sidebar footer** — avatar circle (initials), full name, role pill, menu: *Change password · Lock screen · Sign out*. Nav items the role cannot use are **not added** (not merely disabled).

**Idle lock (`IdleLockMonitor`)** — `Toolkit.addAWTEventListener(..., MOUSE_EVENT_MASK | MOUSE_MOTION_EVENT_MASK | KEY_EVENT_MASK)` resets a `javax.swing.Timer`; on timeout the frame's glass pane shows `LockOverlay` (blurred snapshot or solid scrim) asking for the **current user's password**; 3 wrong attempts → full sign-out. Timeout from `app_setting.idle_lock_minutes`.

**`StaffPanel` (MANAGER only)** — table of users (name, username, role pill, status, last login) + form: create, change role, reset password (generates a one-time temporary password shown once, sets `must_change_pwd = 1`), deactivate/reactivate. Guards enforced in `UserService`: cannot deactivate yourself; cannot remove the **last active manager** (check inside a transaction that `SELECT … FOR UPDATE`s the manager rows).

**`ChangePasswordDialog`** — current + new + confirm; same policy; forced when `must_change_pwd = 1`.

**`MainFrame` restructure** — today `createAppShellPanel()` builds every panel at startup. Change to `buildShell(AppUser)` after login and `tearDownShell()` on sign-out (drop references, stop timers) so no data from one user's session lingers.

## 6. Audit trail

* Table `audit_log` (append-only; trigger `trg_audit_append_only` rejects UPDATE/DELETE).
* `AuditDAO.log(Connection con, String action, String entityType, Long entityId, String details)` — takes the caller's connection so the audit row **commits or rolls back with the business change**.
* Events: `LOGIN_OK, LOGIN_FAILED, LOGIN_LOCKED, LOGOUT, USER_CREATED, USER_ROLE_CHANGED, USER_DEACTIVATED, PWD_CHANGED, PWD_RESET, RES_CREATED, RES_CANCELLED, RES_SEATED, RES_NO_SHOW, RES_COMPLETED, WALKIN_SEATED, WAITLIST_JOINED, OFFER_MADE, OFFER_ACCEPTED, OFFER_DECLINED, OFFER_EXPIRED, TABLE_CREATED/UPDATED/DELETED, CUSTOMER_CREATED/UPDATED/DELETED, SETTING_CHANGED`.
* `details` is short free text (e.g. `T05 · 2026-10-09 · 07:30 PM · 4 guests`). **Never** store passwords or hashes.
* Manager screen "Audit log": filter by user/action/date range, newest first, paged (`FETCH FIRST ? ROWS ONLY` with the 11g `ROWNUM` fallback pattern already used in `ReservationDAO`).
* `reservation.created_by` records the creating user directly (shown in the reservation detail).

## 7. Files to add / change
| New | Changed |
|---|---|
| `security/{Role,Permission,AuthContext,Session,PasswordHasher,PasswordPolicy,IdleLockMonitor}` | `MainFrame` (login cards, `buildShell`), `Sidebar` (user footer, role-filtered nav), `Main` (accent colour) |
| `model/AppUser`, `dao/UserDAO`, `dao/AuditDAO`, `dao/SettingDAO` | **every** service with mutators (`auth.require` + `audit.log`) |
| `service/{AuthService,UserService,AuditService,SettingsService}` | `ReservationService.bookTable` (set `created_by`), `OracleErrorMapper` |
| `ui/auth/{LoginPanel,FirstRunSetupPanel,LockOverlay,ChangePasswordDialog}`, `ui/StaffPanel`, `ui/AuditPanel` | `exception/*` + `AuthenticationException`, `AccessDeniedException`, `AccountLockedException` |

## 8. Tests
**Already verified** (JDK 21 source-launcher run during this analysis): PBKDF2-HMAC-SHA256 known-answer vector (`"passwd"/"salt"/c=1`), correct password accepted, wrong rejected, Base64 lengths 44/24, HOST lacks `TABLE_MANAGE` and `REPORT_VIEW`, MANAGER holds every permission.

**To write (JUnit 5):**
* `PasswordPolicyTest` (length, username equality, common list).
* `RolePermissionMatrixTest` — table-driven: every `(role, permission)` pair against §2.
* `ServiceAuthorizationTest` — each mutating service method with `AuthContext.fixed(host)` → `AccessDeniedException`; with manager → passes validation stage (mock DAOs).
* **DB integration (guarded by `RTRS_IT=1`)**: lockout after N failures; lock survives a new JDBC session; `audit_log` UPDATE/DELETE raises `ORA-20010`; last-manager guard under two threads.
* Manual: caps-lock hint, idle lock, forced password change, sign-out clears panels.

## 9. Threat model — be explicit in the viva
* **Protects against:** accidental or unauthorised actions by staff using the app; gives attribution for every change.
* **Does not protect against:** someone who has the `.env` file (they can connect with SQL*Plus and bypass the app). The DB credentials live on each client; this is inherent to a two-tier desktop app. Mitigations if required: restrict the schema owner's network access, per-user Oracle accounts + roles (option C), or a middle tier (out of scope — "Swing only, preserve the stack").
* Shoulder-surfing / unattended screen → idle lock. Brute force → DB-persisted lockout. Credential theft from DB dump → salted, slow hashes.

## 10. Optional appendix — Guest self-service kiosk (only if the examiner expects customer login)
* **Enrolment is in person:** at first booking the host sets a 6-digit PIN with the guest present (`customer_credential(customer_id PK/FK, pin_hash, pin_salt, pin_iter, failed_attempts, locked_until)`, same hasher, same lockout).
* Kiosk mode (separate launch flag `--kiosk`) shows only: sign in with *phone + PIN* → my reservations → book → cancel **own** bookings. No customer list, no other guests' data, auto-logout after 60 s idle.
* Honest limits: a 6-digit PIN is weak against online guessing, so the lockout is essential; there is no PIN reset without staff. Effort ≈ one extra phase; **not** required for the core plan.
