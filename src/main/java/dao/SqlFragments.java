package dao;

/**
 * Shared SQL fragments and status expressions to eliminate duplicated literals across DAOs (fixes D11).
 */
public final class SqlFragments {
    private SqlFragments() {}

    /**
     * SQL IN clause for active statuses that block a physical table slot in UQ_ACTIVE_BOOKING.
     */
    public static final String ACTIVE_BOOKING_STATUSES_IN = "('HELD', 'CONFIRMED', 'SEATED', 'COMPLETED')";
}
