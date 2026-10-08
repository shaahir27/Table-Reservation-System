package model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Status of a reservation in the system.
 */
public enum ReservationStatus {
    CONFIRMED,
    CANCELLED,
    COMPLETED;

    /**
     * Active statuses that occupy a physical table slot in the engine.
     * In v1: CONFIRMED and COMPLETED.
     */
    public static final Set<ReservationStatus> BLOCKING = Collections.unmodifiableSet(
            EnumSet.of(CONFIRMED, COMPLETED)
    );

    public boolean isBlocking() {
        return BLOCKING.contains(this);
    }
}
