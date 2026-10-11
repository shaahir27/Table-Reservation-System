package model;

/**
 * Reconfirmation status of a booking for upcoming service (P3 Prevention).
 */
public enum ReconfirmStatus {
    PENDING,
    CONFIRMED,
    NO_ANSWER;

    public static ReconfirmStatus fromString(String val) {
        if (val == null || val.isBlank()) return PENDING;
        try {
            return valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
