package model;

/**
 * Lifecycle status of a waitlist entry (P4 Slot Recovery Loop).
 * State machine:
 *   WAITING -> OFFERED | CANCELLED | LAPSED
 *   OFFERED -> FULFILLED | CANCELLED | LAPSED
 */
public enum WaitlistStatus {
    WAITING,
    OFFERED,
    FULFILLED,
    CANCELLED,
    LAPSED;

    public static WaitlistStatus fromString(String val) {
        if (val == null || val.isBlank()) return WAITING;
        try {
            return valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return WAITING;
        }
    }
}
