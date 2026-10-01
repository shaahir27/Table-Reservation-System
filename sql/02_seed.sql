-- ============================================================================
-- Restaurant Table Reservation System (RTRS) - Seed Demo Data
-- Note: Dates are relative to SYSDATE so the demo remains functional anytime.
-- CRITICAL: T05 on tomorrow at 07:30 PM (slot 4) is intentionally UNRESERVED
-- for the live demonstration.
-- ============================================================================

-- 1. Insert 6 Customers (10-digit Indian mobile numbers starting 6-9)
INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Arun Sharma', '9876543210', 'arun.sharma@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Priya Patel', '9812345678', 'priya.patel@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Rahul Kumar', '9723456789', 'rahul.kumar@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Meena Iyer', '9634567890', 'meena.iyer@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Karthik Rao', '9545678901', 'karthik.rao@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Divya Nair', '9456789012', 'divya.nair@example.com');

-- 2. Insert 8 Restaurant Tables
INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T01', 2, 'Indoor', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T02', 4, 'Indoor', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T03', 4, 'Window', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T04', 6, 'Outdoor', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T05', 8, 'Hall', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T06', 2, 'Window', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T07', 4, 'Outdoor', 'MAINTENANCE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T08', 10, 'Hall', 'AVAILABLE');

-- 3. Insert ~8 Reservations across Relative Dates
-- Res 1: Yesterday CONFIRMED on T02 at 07:30 PM (can demo "Mark Completed")
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9876543210'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
    TRUNC(SYSDATE) - 1,
    4,
    4,
    'CONFIRMED'
);

-- Res 2: 2 days ago COMPLETED on T01 at 01:30 PM
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9812345678'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
    TRUNC(SYSDATE) - 2,
    2,
    2,
    'COMPLETED'
);

-- Res 3: Today CONFIRMED on T03 at 07:30 PM
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9723456789'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
    TRUNC(SYSDATE),
    4,
    3,
    'CONFIRMED'
);

-- Res 4: Today CONFIRMED on T04 at 09:00 PM
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9634567890'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T04'),
    TRUNC(SYSDATE),
    5,
    5,
    'CONFIRMED'
);

-- Res 5: Today CANCELLED on T01 at 07:30 PM (allows testing rebooking a cancelled slot today)
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9545678901'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
    TRUNC(SYSDATE),
    4,
    2,
    'CANCELLED'
);

-- Res 6: Tomorrow CONFIRMED on T01 at 12:00 PM
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9456789012'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
    TRUNC(SYSDATE) + 1,
    1,
    2,
    'CONFIRMED'
);

-- Res 7: Tomorrow CONFIRMED on T03 at 06:00 PM
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9876543210'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
    TRUNC(SYSDATE) + 1,
    3,
    4,
    'CONFIRMED'
);

-- Res 8: Day after tomorrow CONFIRMED on T08 at 07:30 PM
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status)
VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9812345678'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T08'),
    TRUNC(SYSDATE) + 2,
    4,
    8,
    'CONFIRMED'
);

COMMIT;
