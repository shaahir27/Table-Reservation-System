-- ============================================================================
-- Restaurant Table Reservation System (RTRS)
-- Double-Booking Prevention Proof (Two-Session Demonstration)
--
-- Objective: Prove that the Oracle Database itself guarantees double-booking
--            prevention via UQ_ACTIVE_BOOKING, even if Java is completely bypassed.
-- ============================================================================

/*
--------------------------------------------------------------------------------
STEP 1: [SESSION A]
First customer books Table T05 for Tomorrow at 07:30 PM (slot_id = 4).
--------------------------------------------------------------------------------
*/

-- In SQL*Plus / SQL Developer Session A:
INSERT INTO reservation (
    reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status
) VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9876543210'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T05'),
    TRUNC(SYSDATE) + 1,
    4,
    4,
    'CONFIRMED'
);
COMMIT;
-- Result: 1 row created and committed.


/*
--------------------------------------------------------------------------------
STEP 2: [SESSION B]
Second customer attempts to book the EXACT SAME Table T05 for Tomorrow at 07:30 PM.
--------------------------------------------------------------------------------
*/

-- In a separate SQL*Plus / SQL Developer Session B:
INSERT INTO reservation (
    reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status
) VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9812345678'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T05'),
    TRUNC(SYSDATE) + 1,
    4,
    4,
    'CONFIRMED'
);

-- EXPECTED RESULT IN SESSION B:
-- ORA-00001: unique constraint (<SCHEMA>.UQ_ACTIVE_BOOKING) violated
-- The database engine immediately and unconditionally rejects the double booking!


/*
--------------------------------------------------------------------------------
STEP 3: [SESSION A]
Session A cancels their reservation.
--------------------------------------------------------------------------------
*/

-- In Session A:
UPDATE reservation
   SET status = 'CANCELLED'
 WHERE table_id = (SELECT table_id FROM restaurant_table WHERE table_number = 'T05')
   AND reservation_date = TRUNC(SYSDATE) + 1
   AND slot_id = 4
   AND status = 'CONFIRMED';
COMMIT;
-- Result: 1 row updated.
-- Because status is now 'CANCELLED', the expression in UQ_ACTIVE_BOOKING evaluates to NULL.
-- In Oracle, all-NULL entries are not indexed, freeing the slot while retaining history.


/*
--------------------------------------------------------------------------------
STEP 4: [SESSION B]
Session B retries the exact same insert.
--------------------------------------------------------------------------------
*/

-- In Session B:
INSERT INTO reservation (
    reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status
) VALUES (
    reservation_seq.NEXTVAL,
    (SELECT customer_id FROM customer WHERE phone = '9812345678'),
    (SELECT table_id FROM restaurant_table WHERE table_number = 'T05'),
    TRUNC(SYSDATE) + 1,
    4,
    4,
    'CONFIRMED'
);
COMMIT;

-- EXPECTED RESULT IN SESSION B:
-- 1 row created. The booking succeeds now that the previous booking is cancelled.
-- Both rows (the CANCELLED one and the CONFIRMED one) peacefully coexist in the table!
