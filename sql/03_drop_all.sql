-- ============================================================================
-- Restaurant Table Reservation System (RTRS) - Clean Drop Script
-- ============================================================================

-- Drop trigger
BEGIN
    EXECUTE IMMEDIATE 'DROP TRIGGER trg_reservation_rules';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- Drop tables with foreign-key cascade
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE reservation CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE restaurant_table CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE time_slot CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE customer CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- Drop sequences
BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE reservation_seq';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE table_seq';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE customer_seq';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/
