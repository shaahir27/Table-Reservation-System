-- ============================================================================
-- RTRS v2 - Migration A: Authentication, RBAC, Audit, Settings
-- Run as the application schema owner (same user that ran 01_schema.sql).
-- NOT TESTED AGAINST ORACLE IN THE ANALYSIS ENVIRONMENT: run on a scratch schema first.
-- Oracle has no CREATE ... IF NOT EXISTS (before 23c): run each script exactly once.
-- ============================================================================

CREATE SEQUENCE user_seq  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE audit_seq START WITH 1 INCREMENT BY 1 NOCACHE;

-- Staff accounts. Users are DEACTIVATED, never deleted (audit rows reference them).
CREATE TABLE app_user (
    user_id          NUMBER(10)    CONSTRAINT pk_app_user PRIMARY KEY,
    username         VARCHAR2(30)  NOT NULL CONSTRAINT uq_app_user_username UNIQUE,
    full_name        VARCHAR2(100) NOT NULL,
    role             VARCHAR2(10)  NOT NULL,
    pwd_hash         VARCHAR2(64)  NOT NULL,          -- Base64(PBKDF2-HMAC-SHA256, 32 bytes) = 44 chars
    pwd_salt         VARCHAR2(32)  NOT NULL,          -- Base64(16 random bytes)             = 24 chars
    pwd_iter         NUMBER(8)     NOT NULL,          -- stored per row so the work factor can be raised later
    is_active        NUMBER(1)     DEFAULT 1 NOT NULL,
    must_change_pwd  NUMBER(1)     DEFAULT 0 NOT NULL,
    failed_attempts  NUMBER(3)     DEFAULT 0 NOT NULL,
    locked_until     TIMESTAMP,
    last_login_at    TIMESTAMP,
    created_at       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT ck_app_user_role   CHECK (role IN ('MANAGER','HOST')),
    CONSTRAINT ck_app_user_active CHECK (is_active IN (0,1)),
    CONSTRAINT ck_app_user_mcp    CHECK (must_change_pwd IN (0,1)),
    CONSTRAINT ck_app_user_name   CHECK (username = LOWER(username)),
    CONSTRAINT ck_app_user_iter   CHECK (pwd_iter >= 100000)
);

-- Append-only audit trail. One row per security-relevant or state-changing action.
CREATE TABLE audit_log (
    audit_id     NUMBER(12)     CONSTRAINT pk_audit_log PRIMARY KEY,
    user_id      NUMBER(10),                                -- NULL for failed logins with unknown username
    action       VARCHAR2(30)   NOT NULL,                   -- e.g. LOGIN_OK, RES_CANCELLED
    entity_type  VARCHAR2(20),                              -- RESERVATION | CUSTOMER | TABLE | USER | WAITLIST | SETTING
    entity_id    NUMBER(12),
    details      VARCHAR2(400),
    created_at   TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES app_user(user_id)
);
CREATE INDEX idx_audit_created ON audit_log(created_at);
CREATE INDEX idx_audit_user    ON audit_log(user_id);

-- Statement-level trigger: ANY update/delete attempt on the audit table is rejected.
-- (The schema owner can still drop the trigger; this protects against application bugs and casual tampering.)
CREATE OR REPLACE TRIGGER trg_audit_append_only
BEFORE UPDATE OR DELETE ON audit_log
BEGIN
    RAISE_APPLICATION_ERROR(-20010, 'audit_log is append-only');
END;
/

-- Tunable business settings (editable by MANAGER in the Settings screen)
CREATE TABLE app_setting (
    setting_key    VARCHAR2(40)  CONSTRAINT pk_app_setting PRIMARY KEY,
    setting_value  VARCHAR2(100) NOT NULL,
    description    VARCHAR2(200)
);
INSERT INTO app_setting VALUES ('lockout_threshold',  '5',  'Failed logins before an account is temporarily locked');
INSERT INTO app_setting VALUES ('lockout_minutes',    '5',  'Lock duration after too many failed logins');
INSERT INTO app_setting VALUES ('idle_lock_minutes',  '10', 'Inactivity before the screen locks (0 = never)');
INSERT INTO app_setting VALUES ('grace_minutes',      '15', 'Minutes after slot start before a CONFIRMED booking is flagged OVERDUE');
INSERT INTO app_setting VALUES ('hold_minutes',       '10', 'How long a waitlist offer keeps the table before it expires');
INSERT INTO app_setting VALUES ('max_waste_seats',    '2',  'Never auto-offer a table with more than this many spare seats');
COMMIT;

-- Who created a reservation (NULL for legacy rows created before v2)
ALTER TABLE reservation ADD (created_by NUMBER(10));
ALTER TABLE reservation ADD CONSTRAINT fk_res_created_by FOREIGN KEY (created_by) REFERENCES app_user(user_id);
