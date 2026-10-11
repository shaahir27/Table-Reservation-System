package model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Status of a reservation in the system (P3 Lifecycle).
 * State machine:
 *   (insert)  -> HELD | CONFIRMED | SEATED
 *   HELD      -> CONFIRMED | SEATED | CANCELLED
 *   CONFIRMED -> SEATED | CANCELLED | NO_SHOW
 *   SEATED    -> COMPLETED
 *   COMPLETED, CANCELLED, NO_SHOW are terminal.
 */
public enum ReservationStatus {
    HELD,
    CONFIRMED,
    SEATED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    /**
     * Active statuses that occupy a physical table slot in the engine.
     * Enforced by the unique index UQ_ACTIVE_BOOKING in Oracle XE.
     */
    public static final Set<ReservationStatus> BLOCKING = Collections.unmodifiableSet(
            EnumSet.of(HELD, CONFIRMED, SEATED, COMPLETED)
    );

    public boolean isBlocking() {
        return BLOCKING.contains(this);
    }

    /**
     * Validates whether a state transition from this status to target is legal.
     * Matches database trigger TRG_RESERVATION_STATUS_FLOW.
     */
    public boolean canTransitionTo(ReservationStatus target) {
        if (this == target) return true;
        return switch (this) {
            case HELD -> target == CONFIRMED || target == SEATED || target == CANCELLED;
            case CONFIRMED -> target == SEATED || target == CANCELLED || target == NO_SHOW;
            case SEATED -> target == COMPLETED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }
}
