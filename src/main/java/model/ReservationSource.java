package model;

/**
 * Origin of a reservation (P3 Lifecycle).
 */
public enum ReservationSource {
    BOOKING,
    WALK_IN,
    WAITLIST;

    public static ReservationSource fromString(String val) {
        if (val == null || val.isBlank()) return BOOKING;
        try {
            return valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BOOKING;
        }
    }
}
