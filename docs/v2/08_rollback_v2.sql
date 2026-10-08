-- ============================================================================
-- RTRS v2 - Rollback (reverse order). Data conversion first, then structure.
-- Review before running: this DISCARDS audit/waitlist/user data.
-- ============================================================================
-- C
ALTER TABLE restaurant_table DROP CONSTRAINT ck_table_pos;
ALTER TABLE restaurant_table DROP CONSTRAINT ck_table_shape;
ALTER TABLE restaurant_table DROP (pos_x, pos_y, shape);

-- B: map new statuses back to the 3-status world BEFORE re-tightening the CHECK
DROP TRIGGER trg_reservation_status_flow;
UPDATE reservation SET status = 'CANCELLED' WHERE status IN ('HELD','NO_SHOW');
UPDATE reservation SET status = 'CONFIRMED' WHERE status = 'SEATED';
COMMIT;
DROP TABLE waitlist_entry CASCADE CONSTRAINTS;
DROP SEQUENCE waitlist_seq;
CREATE UNIQUE INDEX uq_active_booking_v1 ON reservation (
    CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN table_id         END,
    CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN reservation_date END,
    CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN slot_id          END
);
DROP INDEX uq_active_booking;
ALTER INDEX uq_active_booking_v1 RENAME TO uq_active_booking;
ALTER TABLE reservation DROP CONSTRAINT ck_res_status;
ALTER TABLE reservation ADD CONSTRAINT ck_res_status CHECK (status IN ('CONFIRMED','CANCELLED','COMPLETED'));
ALTER TABLE reservation DROP CONSTRAINT ck_res_hold;
ALTER TABLE reservation DROP CONSTRAINT ck_res_reconfirm;
ALTER TABLE reservation DROP CONSTRAINT ck_res_source;
ALTER TABLE reservation DROP (source, seated_at, hold_expires_at, reconfirm_status);

-- A
ALTER TABLE reservation DROP CONSTRAINT fk_res_created_by;
ALTER TABLE reservation DROP (created_by);
DROP TRIGGER trg_audit_append_only;
DROP TABLE audit_log   CASCADE CONSTRAINTS;
DROP TABLE app_setting CASCADE CONSTRAINTS;
DROP TABLE app_user    CASCADE CONSTRAINTS;
DROP SEQUENCE audit_seq;
DROP SEQUENCE user_seq;
