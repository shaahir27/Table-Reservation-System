-- ============================================================================
-- Restaurant Table Reservation System (RTRS) - Schema Definition
-- Target: Oracle Database (XE 11g, 18c, 21c)
-- ============================================================================

-- Sequences
CREATE SEQUENCE customer_seq    START WITH 1    INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE table_seq       START WITH 1    INCREMENT BY 1 NOCACHE;
-- gives RES1001, RES1002...
CREATE SEQUENCE reservation_seq START WITH 1001 INCREMENT BY 1 NOCACHE;

-- Customer Table
CREATE TABLE customer (
    customer_id   NUMBER(10)    CONSTRAINT pk_customer PRIMARY KEY,
    customer_name VARCHAR2(100) NOT NULL,
    phone         VARCHAR2(15)  NOT NULL CONSTRAINT uq_customer_phone UNIQUE,
    email         VARCHAR2(100),
    CONSTRAINT ck_customer_email CHECK (email IS NULL OR email LIKE '%_@_%.__%')
);

-- Restaurant Table
CREATE TABLE restaurant_table (
    table_id     NUMBER(10)   CONSTRAINT pk_rtable PRIMARY KEY,
    table_number VARCHAR2(10) NOT NULL CONSTRAINT uq_table_number UNIQUE,
    capacity     NUMBER(3)    NOT NULL,
    location     VARCHAR2(50) NOT NULL,
    status       VARCHAR2(20) DEFAULT 'AVAILABLE' NOT NULL,
    CONSTRAINT ck_table_capacity CHECK (capacity BETWEEN 1 AND 20),
    CONSTRAINT ck_table_status   CHECK (status IN ('AVAILABLE','MAINTENANCE','INACTIVE'))
);

-- Time Slot Table (Data-driven 90-minute non-overlapping seatings)
CREATE TABLE time_slot (
    slot_id    NUMBER(3)    CONSTRAINT pk_time_slot PRIMARY KEY,
    slot_label VARCHAR2(20) NOT NULL CONSTRAINT uq_slot_label UNIQUE,   -- '07:30 PM'
    start_time VARCHAR2(5)  NOT NULL,                                   -- '19:30' (24h, sortable)
    end_time   VARCHAR2(5)  NOT NULL                                    -- '21:00'
);

-- Reservation Table
CREATE TABLE reservation (
    reservation_id   NUMBER(10)  CONSTRAINT pk_reservation PRIMARY KEY,
    customer_id      NUMBER(10)  NOT NULL,
    table_id         NUMBER(10)  NOT NULL,
    reservation_date DATE        NOT NULL,
    slot_id          NUMBER(3)   NOT NULL,
    guest_count      NUMBER(3)   NOT NULL,
    status           VARCHAR2(20) DEFAULT 'CONFIRMED' NOT NULL,
    created_at       TIMESTAMP   DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_res_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT fk_res_table    FOREIGN KEY (table_id)    REFERENCES restaurant_table(table_id),
    CONSTRAINT fk_res_slot     FOREIGN KEY (slot_id)     REFERENCES time_slot(slot_id),
    CONSTRAINT ck_res_guests   CHECK (guest_count > 0),
    CONSTRAINT ck_res_status   CHECK (status IN ('CONFIRMED','CANCELLED','COMPLETED')),
    -- Oracle DATE carries a time part; force midnight so the unique index below cannot be dodged
    CONSTRAINT ck_res_date_midnight CHECK (reservation_date = TRUNC(reservation_date))
);

CREATE INDEX idx_res_customer ON reservation(customer_id);
CREATE INDEX idx_res_date     ON reservation(reservation_date);

-- ============ DOUBLE-BOOKING PREVENTION (THE KEY FEATURE) ============
-- Function-based UNIQUE index. Rows with status CANCELLED produce NULL for all three
-- key parts; Oracle does not index all-NULL keys, so any number of cancelled rows may
-- coexist, while at most ONE active (CONFIRMED/COMPLETED) row can exist per
-- (table, date, slot). Enforced by the database engine, independent of Java.
CREATE UNIQUE INDEX uq_active_booking ON reservation (
    CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN table_id         END,
    CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN reservation_date END,
    CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN slot_id          END
);

-- Cross-table rule a CHECK constraint cannot express: guests <= capacity, table bookable
CREATE OR REPLACE TRIGGER trg_reservation_rules
BEFORE INSERT OR UPDATE OF table_id, guest_count ON reservation
FOR EACH ROW
DECLARE
    v_capacity NUMBER;
    v_status   VARCHAR2(20);
BEGIN
    SELECT capacity, status INTO v_capacity, v_status
      FROM restaurant_table WHERE table_id = :NEW.table_id;

    IF :NEW.guest_count > v_capacity THEN
        RAISE_APPLICATION_ERROR(-20001, 'Guest count exceeds table capacity');
    END IF;
    IF INSERTING AND v_status <> 'AVAILABLE' THEN
        RAISE_APPLICATION_ERROR(-20002, 'Table is not available for booking');
    END IF;
END;
/

-- Insert standard 5 fixed seatings
INSERT INTO time_slot VALUES (1, '12:00 PM', '12:00', '13:30');
INSERT INTO time_slot VALUES (2, '01:30 PM', '13:30', '15:00');
INSERT INTO time_slot VALUES (3, '06:00 PM', '18:00', '19:30');
INSERT INTO time_slot VALUES (4, '07:30 PM', '19:30', '21:00');
INSERT INTO time_slot VALUES (5, '09:00 PM', '21:00', '22:30');
COMMIT;
