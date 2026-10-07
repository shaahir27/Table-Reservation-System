package service;

import exception.ValidationException;
import model.RestaurantTable;
import model.TableAvailability;
import model.TableStatus;
import model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import util.ValidationUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(null, null, null, null);
    }

    // --- recommendTable Unit Tests (Pure Function) ---

    @Test
    @DisplayName("recommendTable picks smallest capacity table that fits the guest count")
    void testRecommendTable_SmallestFit() {
        List<RestaurantTable> candidates = List.of(
                new RestaurantTable(1, "T01", 2, "Indoor", TableStatus.AVAILABLE),
                new RestaurantTable(2, "T02", 8, "Hall", TableStatus.AVAILABLE),
                new RestaurantTable(3, "T03", 4, "Window", TableStatus.AVAILABLE));

        // For 3 guests, T03 (4 seats) is the best fit over T02 (8 seats)
        TableAvailability rec = reservationService.recommendTable(candidates, 3);
        assertNotNull(rec);
        assertEquals("T03", rec.getTable().getTableNumber());
        assertEquals(4, rec.getTable().getCapacity());
        assertTrue(rec.isRecommended());
    }

    @Test
    @DisplayName("recommendTable breaks ties by selecting lowest table number")
    void testRecommendTable_TieBreaking() {
        List<RestaurantTable> candidates = List.of(
                new RestaurantTable(1, "T03", 4, "Window", TableStatus.AVAILABLE),
                new RestaurantTable(2, "T02", 4, "Indoor", TableStatus.AVAILABLE),
                new RestaurantTable(3, "T07", 4, "Outdoor", TableStatus.AVAILABLE));

        // All have capacity 4. T02 is alphabetically/numerically lowest
        TableAvailability rec = reservationService.recommendTable(candidates, 4);
        assertNotNull(rec);
        assertEquals("T02", rec.getTable().getTableNumber());
    }

    @Test
    @DisplayName("recommendTable returns null if no available table has sufficient capacity")
    void testRecommendTable_NoFit() {
        List<RestaurantTable> candidates = List.of(
                new RestaurantTable(1, "T01", 2, "Indoor", TableStatus.AVAILABLE),
                new RestaurantTable(2, "T02", 4, "Indoor", TableStatus.AVAILABLE),
                new RestaurantTable(3, "T03", 6, "Outdoor", TableStatus.AVAILABLE));

        // 8 guests cannot fit in any single table
        TableAvailability rec = reservationService.recommendTable(candidates, 8);
        assertNull(rec);
    }

    @Test
    @DisplayName("recommendTable ignores tables with status other than AVAILABLE")
    void testRecommendTable_IgnoresUnavailable() {
        List<RestaurantTable> candidates = List.of(
                new RestaurantTable(1, "T01", 4, "Indoor", TableStatus.MAINTENANCE),
                new RestaurantTable(2, "T02", 6, "Hall", TableStatus.AVAILABLE));

        TableAvailability rec = reservationService.recommendTable(candidates, 4);
        assertNotNull(rec);
        assertEquals("T02", rec.getTable().getTableNumber());
    }

    // --- Reservation Validation Tests ---

    @Test
    @DisplayName("Valid reservation inputs pass validation")
    void testValidateReservation_Valid() {
        RestaurantTable table = new RestaurantTable(1, "T05", 8, "Hall", TableStatus.AVAILABLE);
        TimeSlot slot = new TimeSlot(4, "07:30 PM", LocalTime.of(19, 30), LocalTime.of(21, 0));
        LocalDate futureDate = LocalDate.now().plusDays(2);

        assertDoesNotThrow(() -> ValidationUtil.validateReservation(1, futureDate, slot, 4, table));
    }

    @Test
    @DisplayName("Reservation fails when date is in the past")
    void testValidateReservation_PastDate() {
        RestaurantTable table = new RestaurantTable(1, "T01", 2, "Indoor", TableStatus.AVAILABLE);
        TimeSlot slot = new TimeSlot(1, "12:00 PM", LocalTime.of(12, 0), LocalTime.of(13, 30));
        LocalDate pastDate = LocalDate.now().minusDays(1);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> ValidationUtil.validateReservation(1, pastDate, slot, 2, table));
        assertTrue(ex.getFieldErrors().containsKey("date"));
    }

    @Test
    @DisplayName("Reservation fails when guests exceed table capacity")
    void testValidateReservation_GuestsExceedCapacity() {
        RestaurantTable table = new RestaurantTable(1, "T01", 2, "Indoor", TableStatus.AVAILABLE);
        TimeSlot slot = new TimeSlot(4, "07:30 PM", LocalTime.of(19, 30), LocalTime.of(21, 0));
        LocalDate futureDate = LocalDate.now().plusDays(1);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> ValidationUtil.validateReservation(1, futureDate, slot, 4, table));
        assertTrue(ex.getFieldErrors().containsKey("table"));
    }

    @Test
    @DisplayName("Reservation fails when selected table is under maintenance")
    void testValidateReservation_MaintenanceTable() {
        RestaurantTable table = new RestaurantTable(7, "T07", 4, "Outdoor", TableStatus.MAINTENANCE);
        TimeSlot slot = new TimeSlot(4, "07:30 PM", LocalTime.of(19, 30), LocalTime.of(21, 0));
        LocalDate futureDate = LocalDate.now().plusDays(1);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> ValidationUtil.validateReservation(1, futureDate, slot, 2, table));
        assertTrue(ex.getFieldErrors().containsKey("table"));
    }

    @Test
    @DisplayName("Reservation fails when slot for today has already passed")
    void testValidateReservation_PastSlotToday() {
        RestaurantTable table = new RestaurantTable(1, "T01", 4, "Indoor", TableStatus.AVAILABLE);
        // Time slot in the past (e.g. 00:00 - 01:00)
        TimeSlot pastSlot = new TimeSlot(99, "Past Slot", LocalTime.of(0, 1), LocalTime.of(1, 0));
        LocalDate today = LocalDate.now();

        ValidationException ex = assertThrows(ValidationException.class,
                () -> ValidationUtil.validateReservation(1, today, pastSlot, 2, table));
        assertTrue(ex.getFieldErrors().containsKey("slot"));
    }
}
