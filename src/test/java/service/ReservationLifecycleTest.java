package service;

import model.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying the Reservation Lifecycle State Machine (P3).
 * Ensures consistency with Oracle trigger TRG_RESERVATION_STATUS_FLOW.
 */
class ReservationLifecycleTest {

    @Test
    @DisplayName("BLOCKING set correctly contains HELD, CONFIRMED, SEATED, COMPLETED and excludes terminal frees")
    void testBlockingStatuses() {
        assertTrue(ReservationStatus.HELD.isBlocking());
        assertTrue(ReservationStatus.CONFIRMED.isBlocking());
        assertTrue(ReservationStatus.SEATED.isBlocking());
        assertTrue(ReservationStatus.COMPLETED.isBlocking());

        assertFalse(ReservationStatus.CANCELLED.isBlocking());
        assertFalse(ReservationStatus.NO_SHOW.isBlocking());

        assertEquals(4, ReservationStatus.BLOCKING.size());
    }

    @Test
    @DisplayName("CONFIRMED status transitions: allowed to SEATED, CANCELLED, NO_SHOW; rejected to COMPLETED directly")
    void testConfirmedTransitions() {
        // Legal transitions
        assertTrue(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.SEATED));
        assertTrue(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.CANCELLED));
        assertTrue(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.NO_SHOW));
        assertTrue(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.CONFIRMED));

        // Illegal: cannot skip directly to COMPLETED without SEATED
        assertFalse(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.COMPLETED));
        assertFalse(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.HELD));
    }

    @Test
    @DisplayName("HELD status transitions: allowed to CONFIRMED, SEATED, CANCELLED; rejected to COMPLETED or NO_SHOW")
    void testHeldTransitions() {
        assertTrue(ReservationStatus.HELD.canTransitionTo(ReservationStatus.CONFIRMED));
        assertTrue(ReservationStatus.HELD.canTransitionTo(ReservationStatus.SEATED));
        assertTrue(ReservationStatus.HELD.canTransitionTo(ReservationStatus.CANCELLED));
        assertTrue(ReservationStatus.HELD.canTransitionTo(ReservationStatus.HELD));

        assertFalse(ReservationStatus.HELD.canTransitionTo(ReservationStatus.COMPLETED));
        assertFalse(ReservationStatus.HELD.canTransitionTo(ReservationStatus.NO_SHOW));
    }

    @Test
    @DisplayName("SEATED status transitions: allowed only to COMPLETED")
    void testSeatedTransitions() {
        assertTrue(ReservationStatus.SEATED.canTransitionTo(ReservationStatus.COMPLETED));
        assertTrue(ReservationStatus.SEATED.canTransitionTo(ReservationStatus.SEATED));

        assertFalse(ReservationStatus.SEATED.canTransitionTo(ReservationStatus.CONFIRMED));
        assertFalse(ReservationStatus.SEATED.canTransitionTo(ReservationStatus.CANCELLED));
        assertFalse(ReservationStatus.SEATED.canTransitionTo(ReservationStatus.NO_SHOW));
        assertFalse(ReservationStatus.SEATED.canTransitionTo(ReservationStatus.HELD));
    }

    @Test
    @DisplayName("COMPLETED is terminal and cannot transition to any other status")
    void testCompletedIsTerminal() {
        assertTrue(ReservationStatus.COMPLETED.canTransitionTo(ReservationStatus.COMPLETED));

        assertFalse(ReservationStatus.COMPLETED.canTransitionTo(ReservationStatus.CONFIRMED));
        assertFalse(ReservationStatus.COMPLETED.canTransitionTo(ReservationStatus.SEATED));
        assertFalse(ReservationStatus.COMPLETED.canTransitionTo(ReservationStatus.CANCELLED));
        assertFalse(ReservationStatus.COMPLETED.canTransitionTo(ReservationStatus.NO_SHOW));
        assertFalse(ReservationStatus.COMPLETED.canTransitionTo(ReservationStatus.HELD));
    }

    @Test
    @DisplayName("CANCELLED is terminal and cannot transition to any other status")
    void testCancelledIsTerminal() {
        assertTrue(ReservationStatus.CANCELLED.canTransitionTo(ReservationStatus.CANCELLED));

        assertFalse(ReservationStatus.CANCELLED.canTransitionTo(ReservationStatus.CONFIRMED));
        assertFalse(ReservationStatus.CANCELLED.canTransitionTo(ReservationStatus.SEATED));
        assertFalse(ReservationStatus.CANCELLED.canTransitionTo(ReservationStatus.COMPLETED));
        assertFalse(ReservationStatus.CANCELLED.canTransitionTo(ReservationStatus.NO_SHOW));
        assertFalse(ReservationStatus.CANCELLED.canTransitionTo(ReservationStatus.HELD));
    }

    @Test
    @DisplayName("NO_SHOW is terminal and cannot transition to any other status")
    void testNoShowIsTerminal() {
        assertTrue(ReservationStatus.NO_SHOW.canTransitionTo(ReservationStatus.NO_SHOW));

        assertFalse(ReservationStatus.NO_SHOW.canTransitionTo(ReservationStatus.CONFIRMED));
        assertFalse(ReservationStatus.NO_SHOW.canTransitionTo(ReservationStatus.SEATED));
        assertFalse(ReservationStatus.NO_SHOW.canTransitionTo(ReservationStatus.COMPLETED));
        assertFalse(ReservationStatus.NO_SHOW.canTransitionTo(ReservationStatus.CANCELLED));
        assertFalse(ReservationStatus.NO_SHOW.canTransitionTo(ReservationStatus.HELD));
    }
}
