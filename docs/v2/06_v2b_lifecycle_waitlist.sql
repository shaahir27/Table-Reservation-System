-- ============================================================================
-- RTRS v2 - Migration B: Reservation lifecycle + Waitlist (Slot Recovery Loop)
-- Requires Migration A. Run once, ideally while no client is connected.
-- NOT TESTED AGAINST ORACLE IN THE ANALYSIS ENVIRONMENT: run on a scratch schema first.
-- ============================================================================

CREATE SEQUENCE waitlist_seq START WITH 1 INCREMENT BY 1 NOCACHE;

-- 1. New reservation columns -------------------------------------------------
ALTER TABLE reservation ADD (
    source            VARCHAR2(10)  DEFAULT 'BOOKING' NOT NULL,   -- BOOKING | WALK_IN | WAITLIST
    seated_at         TIMESTAMP,
    hold_expires_at   TIMESTAMP,
    reconfirm_status  VARCHAR2(10)  DEFAULT 'PENDING' NOT NULL    -- PENDING | CONFIRMED | NO_ANSWER
);
ALTER TABLE reservation ADD CONSTRAINT ck_res_source    CHECK (source IN ('BOOKING','WALK_IN','WAITLIST'));
ALTER TABLE reservation ADD CONSTRAINT ck_res_reconfirm CHECK (reconfirm_status IN ('PENDING','CONFIRMED','NO_ANSWER'));
ALTER TABLE reservation ADD CONSTRAINT ck_res_hold      CHECK (status <> 'HELD' OR hold_expires_at IS NOT NULL);

-- 2. Six statuses instead of three -------------------------------------------
ALTER TABLE reservation DROP CONSTRAINT ck_res_status;
ALTER TABLE reservation ADD CONSTRAINT ck_res_status
    CHECK (status IN ('HELD','CONFIRMED','SEATED','COMPLETED','CANCELLED','NO_SHOW'));

-- 3. Re-create the double-booking guard so HELD and SEATED also block the slot.
--    Build the new index FIRST, then swap, so there is never a moment without protection.
CREATE UNIQUE INDEX uq_active_booking_v2 ON reservation (
    CASE WHEN status IN ('HELD','CONFIRMED','SEATED','COMPLETED') THEN table_id         END,
    CASE WHEN status IN ('HELD','CONFIRMED','SEATED','COMPLETED') THEN reservation_date END,
    CASE WHEN status IN ('HELD','CONFIRMED','SEATED','COMPLETED') THEN slot_id          END
);
DROP INDEX uq_active_booking;
ALTER INDEX uq_active_booking_v2 RENAME TO uq_active_booking;   -- OracleErrorMapper matches on this name

-- 4. Legal status transitions, enforced by the engine ------------------------
--      HELD      -> CONFIRMED | SEATED | CANCELLED
--      CONFIRMED -> SEATED | CANCELLED | NO_SHOW
--      SEATED    -> COMPLETED
--    CANCELLED, NO_SHOW, COMPLETED are terminal. (INSERT may start in HELD, CONFIRMED or SEATED.)
CREATE OR REPLACE TRIGGER trg_reservation_status_flow
BEFORE UPDATE OF status ON reservation
FOR EACH ROW
WHEN (OLD.status <> NEW.status)
BEGIN
    IF NOT (
           (:OLD.status = 'HELD'      AND :NEW.status IN ('CONFIRMED','SEATED','CANCELLED'))
        OR (:OLD.status = 'CONFIRMED' AND :NEW.status IN ('SEATED','CANCELLED','NO_SHOW'))
        OR (:OLD.status = 'SEATED'    AND :NEW.status =  'COMPLETED')
    ) THEN
        RAISE_APPLICATION_ERROR(-20003,
            'Illegal reservation status change: ' || :OLD.status || ' -> ' || :NEW.status);
    END IF;
END;
/

-- 5. Waitlist ------------------------------------------------------------------
CREATE TABLE waitlist_entry (
    entry_id                NUMBER(10)   CONSTRAINT pk_waitlist PRIMARY KEY,
    customer_id             NUMBER(10)   NOT NULL,
    service_date            DATE         NOT NULL,
    slot_id                 NUMBER(3),                         -- NULL = "any slot that day"
    party_size              NUMBER(3)    NOT NULL,
    pref_location           VARCHAR2(50),                      -- NULL = any section
    status                  VARCHAR2(10) DEFAULT 'WAITING' NOT NULL,
    offered_reservation_id  NUMBER(10),                        -- the HELD / resulting reservation
    created_by              NUMBER(10),
    created_at              TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    note                    VARCHAR2(200),
    CONSTRAINT fk_wl_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT fk_wl_slot     FOREIGN KEY (slot_id)     REFERENCES time_slot(slot_id),
    CONSTRAINT fk_wl_res      FOREIGN KEY (offered_reservation_id) REFERENCES reservation(reservation_id),
    CONSTRAINT fk_wl_user     FOREIGN KEY (created_by)  REFERENCES app_user(user_id),
    CONSTRAINT ck_wl_party    CHECK (party_size BETWEEN 1 AND 20),
    CONSTRAINT ck_wl_status   CHECK (status IN ('WAITING','OFFERED','FULFILLED','CANCELLED','LAPSED')),
    CONSTRAINT ck_wl_midnight CHECK (service_date = TRUNC(service_date)),
    CONSTRAINT ck_wl_offer    CHECK (status <> 'OFFERED' OR offered_reservation_id IS NOT NULL)
);
CREATE INDEX idx_wl_lookup ON waitlist_entry(service_date, status);

-- Same trick as UQ_ACTIVE_BOOKING: one live (WAITING/OFFERED) entry per customer/date/slot.
CREATE UNIQUE INDEX uq_active_waitlist ON waitlist_entry (
    CASE WHEN status IN ('WAITING','OFFERED') THEN customer_id          END,
    CASE WHEN status IN ('WAITING','OFFERED') THEN service_date         END,
    CASE WHEN status IN ('WAITING','OFFERED') THEN NVL(slot_id, 0)      END
);
