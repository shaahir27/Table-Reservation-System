-- ============================================================================
-- Restaurant Table Reservation System (RTRS) - Rich Demo Seed Data
-- Target: Oracle Database (XE 11g, 18c, 21c)
--
-- Features:
--   - Clean idempotent execution (safely purges existing data in FK order)
--   - 20 Authentic customer profiles with valid phones and emails
--   - 12 Restaurant tables spanning all 4 zones (Indoor, Window, Outdoor, Hall)
--     with seating capacities from 2 to 12 seats (including T07 in MAINTENANCE)
--   - 70 Realistic reservations across past history, today, tomorrow, and future
--   - Preserves T05 tomorrow at 07:30 PM (slot 4) UNRESERVED for live double-booking proof
--   - Dynamic relative dates using TRUNC(SYSDATE) so the demo is evergreen
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. Clean Existing Data (allows re-running script cleanly without FK conflicts)
-- ----------------------------------------------------------------------------
DELETE FROM reservation;
DELETE FROM restaurant_table;
DELETE FROM customer;
COMMIT;

-- ----------------------------------------------------------------------------
-- 1. Insert 20 Customers (10-digit Indian mobile numbers, diverse profiles)
-- ----------------------------------------------------------------------------
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

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Vikram Malhotra', '9833445566', 'vikram.m@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Ananya Deshmukh', '9744556677', 'ananya.d@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Rohan Sen', '9655667788', 'rohan.sen@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Sneha Kulkarni', '9566778899', 'sneha.k@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Aditya Verma', '9877889900', 'aditya.v@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Pooja Hegde', '9788990011', 'pooja.h@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Siddharth Joshi', '9699001122', 'siddharth.j@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Neha Bansal', '9500112233', 'neha.b@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Rajesh Menon', '9811223344', 'rajesh.menon@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Kavita Reddy', '9722334455', 'kavita.r@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Amitava Ghosh', '9633445566', 'amitava.g@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Sunita Chawla', '9544556677', 'sunita.c@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Tarun Singhania', '9855667788', 'tarun.s@example.com');

INSERT INTO customer (customer_id, customer_name, phone, email)
VALUES (customer_seq.NEXTVAL, 'Deepa Sundaram', '9766778899', 'deepa.s@example.com');

-- ----------------------------------------------------------------------------
-- 2. Insert 12 Restaurant Tables (Balanced across Indoor, Window, Outdoor, Hall)
-- ----------------------------------------------------------------------------
-- Indoor Dining Zone
INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T01', 2, 'Indoor', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T02', 4, 'Indoor', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T10', 4, 'Indoor', 'AVAILABLE');

-- Scenic Window Zone
INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T03', 4, 'Window', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T06', 2, 'Window', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T11', 6, 'Window', 'AVAILABLE');

-- Garden & Terrace Outdoor Zone
INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T04', 6, 'Outdoor', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T07', 4, 'Outdoor', 'MAINTENANCE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T09', 2, 'Outdoor', 'AVAILABLE');

-- Grand Dining Hall Zone
INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T05', 8, 'Hall', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T08', 10, 'Hall', 'AVAILABLE');

INSERT INTO restaurant_table (table_id, table_number, capacity, location, status)
VALUES (table_seq.NEXTVAL, 'T12', 12, 'Hall', 'AVAILABLE');

-- ----------------------------------------------------------------------------
-- 3. Insert Reservations (~70 bookings across past history, today, and future)
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 3.1. Historical Seatings (Past 6 Days: SYSDATE - 6 to SYSDATE - 1)
-- Feeds Date Range Audit, Table Popularity & Time Slot Analytics
-- ============================================================================

-- --- 6 Days Ago ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9876543210'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
        TRUNC(SYSDATE) - 6, 1, 2, 'COMPLETED', SYSTIMESTAMP - INTERVAL '8' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9812345678'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
        TRUNC(SYSDATE) - 6, 2, 4, 'COMPLETED', SYSTIMESTAMP - INTERVAL '8' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9833445566'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
        TRUNC(SYSDATE) - 6, 4, 4, 'COMPLETED', SYSTIMESTAMP - INTERVAL '7' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9566778899'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T05'),
        TRUNC(SYSDATE) - 6, 4, 7, 'COMPLETED', SYSTIMESTAMP - INTERVAL '7' DAY);

-- --- 5 Days Ago ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9723456789'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T06'),
        TRUNC(SYSDATE) - 5, 1, 2, 'COMPLETED', SYSTIMESTAMP - INTERVAL '7' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9634567890'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T04'),
        TRUNC(SYSDATE) - 5, 3, 5, 'COMPLETED', SYSTIMESTAMP - INTERVAL '6' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9855667788'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T08'),
        TRUNC(SYSDATE) - 5, 4, 9, 'COMPLETED', SYSTIMESTAMP - INTERVAL '6' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9877889900'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
        TRUNC(SYSDATE) - 5, 5, 3, 'CANCELLED', SYSTIMESTAMP - INTERVAL '6' DAY);

-- --- 4 Days Ago ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9545678901'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T10'),
        TRUNC(SYSDATE) - 4, 2, 4, 'COMPLETED', SYSTIMESTAMP - INTERVAL '6' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9744556677'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
        TRUNC(SYSDATE) - 4, 3, 3, 'COMPLETED', SYSTIMESTAMP - INTERVAL '5' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9811223344'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T11'),
        TRUNC(SYSDATE) - 4, 4, 6, 'COMPLETED', SYSTIMESTAMP - INTERVAL '5' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9456789012'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
        TRUNC(SYSDATE) - 4, 4, 2, 'COMPLETED', SYSTIMESTAMP - INTERVAL '5' DAY);

-- --- 3 Days Ago ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9655667788'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
        TRUNC(SYSDATE) - 3, 1, 4, 'COMPLETED', SYSTIMESTAMP - INTERVAL '5' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9544556677'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T05'),
        TRUNC(SYSDATE) - 3, 3, 8, 'COMPLETED', SYSTIMESTAMP - INTERVAL '4' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9788990011'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
        TRUNC(SYSDATE) - 3, 4, 4, 'COMPLETED', SYSTIMESTAMP - INTERVAL '4' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9699001122'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T04'),
        TRUNC(SYSDATE) - 3, 5, 6, 'COMPLETED', SYSTIMESTAMP - INTERVAL '4' DAY);

-- --- 2 Days Ago ---
-- Note: Matches original Seed Res 2 on T01 at 01:30 PM
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9812345678'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
        TRUNC(SYSDATE) - 2, 2, 2, 'COMPLETED', SYSTIMESTAMP - INTERVAL '4' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9766778899'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T06'),
        TRUNC(SYSDATE) - 2, 3, 2, 'COMPLETED', SYSTIMESTAMP - INTERVAL '3' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9722334455'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T10'),
        TRUNC(SYSDATE) - 2, 4, 4, 'COMPLETED', SYSTIMESTAMP - INTERVAL '3' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9500112233'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T12'),
        TRUNC(SYSDATE) - 2, 4, 11, 'COMPLETED', SYSTIMESTAMP - INTERVAL '3' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9633445566'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
        TRUNC(SYSDATE) - 2, 5, 2, 'CANCELLED', SYSTIMESTAMP - INTERVAL '3' DAY);

-- --- Yesterday ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9456789012'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
        TRUNC(SYSDATE) - 1, 1, 2, 'COMPLETED', SYSTIMESTAMP - INTERVAL '3' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9833445566'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T04'),
        TRUNC(SYSDATE) - 1, 3, 5, 'COMPLETED', SYSTIMESTAMP - INTERVAL '2' DAY);

-- Note: Matches original Seed Res 1 (Yesterday CONFIRMED on T02 at 07:30 PM for 'Mark Completed' demo)
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9876543210'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
        TRUNC(SYSDATE) - 1, 4, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '2' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9566778899'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T08'),
        TRUNC(SYSDATE) - 1, 4, 8, 'COMPLETED', SYSTIMESTAMP - INTERVAL '2' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9877889900'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T11'),
        TRUNC(SYSDATE) - 1, 5, 6, 'COMPLETED', SYSTIMESTAMP - INTERVAL '2' DAY);


-- ============================================================================
-- 3.2. Today's Reservations (TRUNC(SYSDATE))
-- Feeds Dashboard 'Today's Reservations' (12 count), Live Feed, Floor View & Today Report
-- ============================================================================

-- --- Today Lunch: 12:00 PM (Slot 1) ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9655667788'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
        TRUNC(SYSDATE), 1, 2, 'COMPLETED', SYSTIMESTAMP - INTERVAL '1' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9566778899'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
        TRUNC(SYSDATE), 1, 4, 'COMPLETED', SYSTIMESTAMP - INTERVAL '1' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9545678901'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T10'),
        TRUNC(SYSDATE), 1, 3, 'COMPLETED', SYSTIMESTAMP - INTERVAL '1' DAY);

-- --- Today Lunch: 01:30 PM (Slot 2) ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9744556677'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
        TRUNC(SYSDATE), 2, 4, 'COMPLETED', SYSTIMESTAMP - INTERVAL '20' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9811223344'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T05'),
        TRUNC(SYSDATE), 2, 8, 'COMPLETED', SYSTIMESTAMP - INTERVAL '20' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9634567890'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T06'),
        TRUNC(SYSDATE), 2, 2, 'COMPLETED', SYSTIMESTAMP - INTERVAL '18' HOUR);

-- --- Today Evening: 06:00 PM (Slot 3) ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9788990011'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
        TRUNC(SYSDATE), 3, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '14' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9699001122'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T04'),
        TRUNC(SYSDATE), 3, 6, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '12' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9544556677'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T11'),
        TRUNC(SYSDATE), 3, 5, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '10' HOUR);

-- --- Today Prime Dinner: 07:30 PM (Slot 4) ---
-- Note: Matches original Seed Res 3 on T03
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9723456789'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
        TRUNC(SYSDATE), 4, 3, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '8' HOUR);

-- Note: Matches original Seed Res 5 (CANCELLED on T01 - allows testing re-booking a cancelled slot today)
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9545678901'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
        TRUNC(SYSDATE), 4, 2, 'CANCELLED', SYSTIMESTAMP - INTERVAL '6' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9833445566'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T05'),
        TRUNC(SYSDATE), 4, 8, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '8' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9500112233'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T10'),
        TRUNC(SYSDATE), 4, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '6' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9855667788'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T08'),
        TRUNC(SYSDATE), 4, 10, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '5' HOUR);

-- --- Today Late Dinner: 09:00 PM (Slot 5) ---
-- Note: Matches original Seed Res 4 on T04
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9634567890'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T04'),
        TRUNC(SYSDATE), 5, 5, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '4' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9633445566'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T06'),
        TRUNC(SYSDATE), 5, 2, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '4' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9722334455'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T12'),
        TRUNC(SYSDATE), 5, 12, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '3' HOUR);


-- ============================================================================
-- 3.3. Tomorrow's Reservations (TRUNC(SYSDATE) + 1)
-- CRITICAL ANCHOR: Table T05 at 07:30 PM (slot 4) is INTENTIONALLY UNRESERVED
-- for the live 04_double_booking_demo.sql demonstration!
-- ============================================================================

-- --- Tomorrow Lunch: 12:00 PM (Slot 1) ---
-- Note: Matches original Seed Res 6 on T01
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9456789012'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
        TRUNC(SYSDATE) + 1, 1, 2, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '2' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9566778899'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
        TRUNC(SYSDATE) + 1, 1, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

-- --- Tomorrow Lunch: 01:30 PM (Slot 2) ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9655667788'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
        TRUNC(SYSDATE) + 1, 2, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9877889900'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T10'),
        TRUNC(SYSDATE) + 1, 2, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

-- --- Tomorrow Evening: 06:00 PM (Slot 3) ---
-- Note: Matches original Seed Res 7 on T03
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9876543210'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
        TRUNC(SYSDATE) + 1, 3, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9766778899'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T06'),
        TRUNC(SYSDATE) + 1, 3, 2, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '18' HOUR);

-- --- Tomorrow Prime Dinner: 07:30 PM (Slot 4) ---
-- TABLE T05 IS DELIBERATELY LEFT FREE FOR THE EXAMINER DOUBLE-BOOKING DEMO!
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9812345678'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
        TRUNC(SYSDATE) + 1, 4, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '12' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9699001122'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T04'),
        TRUNC(SYSDATE) + 1, 4, 6, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '10' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9855667788'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T08'),
        TRUNC(SYSDATE) + 1, 4, 8, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '8' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9744556677'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T11'),
        TRUNC(SYSDATE) + 1, 4, 5, 'CANCELLED', SYSTIMESTAMP - INTERVAL '6' HOUR);

-- --- Tomorrow Late Dinner: 09:00 PM (Slot 5) ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9723456789'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
        TRUNC(SYSDATE) + 1, 5, 2, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '14' HOUR);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9788990011'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T10'),
        TRUNC(SYSDATE) + 1, 5, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '10' HOUR);


-- ============================================================================
-- 3.4. Day After Tomorrow (TRUNC(SYSDATE) + 2)
-- Feeds Floor View '+2 Days' chip and Upcoming Bookings StatCard
-- ============================================================================

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9722334455'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
        TRUNC(SYSDATE) + 2, 2, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '2' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9500112233'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T05'),
        TRUNC(SYSDATE) + 2, 2, 7, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '2' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9766778899'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T01'),
        TRUNC(SYSDATE) + 2, 3, 2, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9633445566'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T04'),
        TRUNC(SYSDATE) + 2, 3, 5, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

-- Note: Matches original Seed Res 8 on T08 at 07:30 PM
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9812345678'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T08'),
        TRUNC(SYSDATE) + 2, 4, 8, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '2' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9833445566'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
        TRUNC(SYSDATE) + 2, 4, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9811223344'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T12'),
        TRUNC(SYSDATE) + 2, 4, 10, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9634567890'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T06'),
        TRUNC(SYSDATE) + 2, 5, 2, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);


-- ============================================================================
-- 3.5. Forward Schedule Outlook (Days +3 to +6)
-- Provides rich forward depth for calendar search & table popularity rankings
-- ============================================================================

-- --- Day +3 ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9544556677'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T03'),
        TRUNC(SYSDATE) + 3, 4, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '2' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9855667788'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T05'),
        TRUNC(SYSDATE) + 3, 4, 8, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '2' DAY);

-- --- Day +4 ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9744556677'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T02'),
        TRUNC(SYSDATE) + 4, 3, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '2' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9699001122'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T11'),
        TRUNC(SYSDATE) + 4, 4, 6, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

-- --- Day +5 ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9566778899'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T04'),
        TRUNC(SYSDATE) + 5, 4, 6, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '2' DAY);

INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9811223344'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T08'),
        TRUNC(SYSDATE) + 5, 5, 10, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

-- --- Day +6 ---
INSERT INTO reservation (reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_at)
VALUES (reservation_seq.NEXTVAL, (SELECT customer_id FROM customer WHERE phone = '9788990011'),
        (SELECT table_id FROM restaurant_table WHERE table_number = 'T10'),
        TRUNC(SYSDATE) + 6, 4, 4, 'CONFIRMED', SYSTIMESTAMP - INTERVAL '1' DAY);

-- ----------------------------------------------------------------------------
-- Commit all seed transactions
-- ----------------------------------------------------------------------------
COMMIT;
