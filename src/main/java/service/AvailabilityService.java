package service;

import dao.AvailabilityDAO;
import model.AvailabilityGrid;
import model.TableAvailability;
import model.TimeSlot;
import util.DbClock;

import java.time.LocalDate;
import java.util.List;

/**
 * Service orchestrating One-Glance Availability Matrix queries and recommendations.
 */
public class AvailabilityService {
    private final AvailabilityDAO availabilityDAO;
    private final ReservationService reservationService;

    public AvailabilityService() {
        this(new AvailabilityDAO(), new ReservationService());
    }

    public AvailabilityService(AvailabilityDAO availabilityDAO, ReservationService reservationService) {
        this.availabilityDAO = availabilityDAO != null ? availabilityDAO : new AvailabilityDAO();
        this.reservationService = reservationService != null ? reservationService : new ReservationService();
    }

    /**
     * Fetches the 7-day availability matrix for the specified party size and optional location filter.
     */
    public AvailabilityGrid getMatrix(LocalDate startDate, int days, int guests, String section) {
        if (startDate == null) {
            startDate = DbClock.today();
        }
        if (days <= 0) {
            days = 7;
        }
        if (guests <= 0) {
            guests = 2;
        }
        return availabilityDAO.getAvailabilityGrid(startDate, days, guests, section);
    }

    /**
     * Resolves individual table options and recommendations for a selected matrix cell.
     */
    public List<TableAvailability> getAvailableTablesForCell(LocalDate date, int slotId, int guests, String section) {
        TimeSlot slot = reservationService.getTimeSlots().stream()
                .filter(s -> s.getSlotId() == slotId)
                .findFirst()
                .orElse(null);
        if (slot == null) {
            return List.of();
        }

        List<TableAvailability> available = reservationService.findAvailableTables(date, slot, guests);
        if (section != null && !section.equalsIgnoreCase("Any") && !section.isBlank()) {
            return available.stream()
                    .filter(a -> a.getTable() != null && a.getTable().getLocation().equalsIgnoreCase(section))
                    .toList();
        }
        return available;
    }
}
