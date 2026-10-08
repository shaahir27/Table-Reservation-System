package service;

import dao.CustomerDAO;
import dao.ReservationDAO;
import dao.TableDAO;
import dao.TimeSlotDAO;
import db.DatabaseConnection;
import exception.AppException;
import exception.BookingConflictException;
import exception.ValidationException;
import model.Customer;
import model.DashboardStats;
import model.Reservation;
import model.RestaurantTable;
import model.TableAvailability;
import model.TableState;
import model.TableStatus;
import model.TimeSlot;
import util.AppLog;
import util.DbClock;
import util.OracleErrorMapper;
import util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Core business service managing reservations, table recommendations,
 * and concurrency-safe double-booking prevention transactions.
 */
public class ReservationService {
    private final ReservationDAO reservationDAO;
    private final TableDAO tableDAO;
    private final CustomerDAO customerDAO;
    private final TimeSlotDAO timeSlotDAO;

    // In-memory cache for static time slots
    private List<TimeSlot> cachedSlots = null;

    public ReservationService() {
        this(new ReservationDAO(), new TableDAO(), new CustomerDAO(), new TimeSlotDAO());
    }

    public ReservationService(ReservationDAO reservationDAO, TableDAO tableDAO, 
                              CustomerDAO customerDAO, TimeSlotDAO timeSlotDAO) {
        this.reservationDAO = reservationDAO;
        this.tableDAO = tableDAO;
        this.customerDAO = customerDAO;
        this.timeSlotDAO = timeSlotDAO;
    }

    /**
     * Retrieves all fixed 90-minute time slots (cached after initial query).
     */
    public synchronized List<TimeSlot> getTimeSlots() {
        if (cachedSlots == null || cachedSlots.isEmpty()) {
            cachedSlots = timeSlotDAO.getAllSlots();
        }
        return cachedSlots;
    }

    /**
     * Finds bookable tables for the given criteria and flags the optimal recommendation.
     */
    public List<TableAvailability> findAvailableTables(LocalDate date, TimeSlot slot, int guests) {
        if (date == null) {
            throw new ValidationException("Reservation date is required.");
        }
        if (date.isBefore(DbClock.today())) {
            throw new ValidationException("Reservation date cannot be in the past.");
        }
        if (slot == null) {
            throw new ValidationException("Please select a time slot.");
        }
        if (date.isEqual(DbClock.today()) && !slot.getStartTime().isAfter(DbClock.localTime())) {
            throw new ValidationException("Selected time slot for today has already passed or started.");
        }
        if (guests < 1 || guests > 20) {
            throw new ValidationException("Guest count must be between 1 and 20.");
        }

        List<RestaurantTable> candidates = tableDAO.getAvailableTables(date, slot.getSlotId(), guests);
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }

        TableAvailability recommended = recommendTable(candidates, guests);
        int recommendedTableId = recommended != null && recommended.getTable() != null ? 
                recommended.getTable().getTableId() : -1;

        List<TableAvailability> result = new ArrayList<>();
        for (RestaurantTable t : candidates) {
            boolean isRec = (t.getTableId() == recommendedTableId);
            result.add(new TableAvailability(t, TableState.AVAILABLE, isRec));
        }

        return result;
    }

    /**
     * Pure function recommending the best-fit table among candidates.
     * Rule: Picks smallest capacity >= guests; ties broken by lowest table_number.
     *
     * @param candidates Bookable tables with capacity >= guests
     * @param guests     Number of guests
     * @return TableAvailability with recommended flag, or null if none fit
     */
    public TableAvailability recommendTable(List<RestaurantTable> candidates, int guests) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        Optional<RestaurantTable> bestMatch = candidates.stream()
                .filter(t -> t.getCapacity() >= guests && t.getStatus() == TableStatus.AVAILABLE)
                .min(Comparator.comparingInt(RestaurantTable::getCapacity)
                        .thenComparing(RestaurantTable::getTableNumber));

        return bestMatch.map(t -> new TableAvailability(t, TableState.AVAILABLE, true)).orElse(null);
    }

    /**
     * Creates a reservation inside a strictly serializable transaction.
     * Uses pessimistic locking (SELECT ... FOR UPDATE) and Oracle's unique index.
     */
    public Reservation bookTable(int customerId, int tableId, LocalDate date, TimeSlot slot, int guests) {
        // Retrieve and pre-validate table & customer
        Customer customer = customerDAO.getCustomerById(customerId);
        if (customer == null) {
            throw new ValidationException("Selected customer does not exist.");
        }

        RestaurantTable table = tableDAO.getTableById(tableId);
        if (table == null) {
            throw new ValidationException("Selected table does not exist.");
        }

        ValidationUtil.validateReservation(customerId, date, slot, guests, table);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Lock table row to serialize concurrent bookers on this physical table
                tableDAO.lockTable(con, tableId);

                // 2. Refresh table state under lock
                RestaurantTable lockedTable = tableDAO.getTableById(con, tableId);
                if (lockedTable.getStatus() != TableStatus.AVAILABLE) {
                    throw new ValidationException("Table " + lockedTable.getTableNumber() + " is currently under maintenance or inactive.");
                }
                if (guests > lockedTable.getCapacity()) {
                    throw new ValidationException("Guest count (" + guests + ") exceeds table capacity (" + lockedTable.getCapacity() + ").");
                }

                // 3. Application pre-check for friendly error messaging
                if (reservationDAO.isSlotTaken(con, tableId, date, slot.getSlotId())) {
                    throw new BookingConflictException(lockedTable.getTableNumber(), date, slot.getLabel());
                }

                // 4. Insert reservation. Oracle's UQ_ACTIVE_BOOKING function-based unique index is the real guarantee
                Reservation newReservation = new Reservation(customerId, tableId, date, slot.getSlotId(), guests);
                Reservation created = reservationDAO.createReservation(con, newReservation);

                con.commit();
                AppLog.info("Successfully booked reservation " + created.getDisplayCode() + " for Table " + lockedTable.getTableNumber());
                return created;
            } catch (SQLException e) {
                con.rollback();
                AppException mapped = OracleErrorMapper.map(e, "ReservationService.bookTable");
                if (mapped instanceof BookingConflictException) {
                    throw new BookingConflictException(table.getTableNumber(), date, slot.getLabel());
                }
                throw mapped;
            } catch (RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationService.bookTable.connection");
        }
    }

    /**
     * Cancels an active reservation. The unique index slot becomes immediately freed,
     * while the record is preserved in the database for auditing.
     */
    public void cancelReservation(int reservationId) {
        boolean success = reservationDAO.cancelReservation(reservationId);
        if (!success) {
            throw new ValidationException("Reservation RES" + reservationId + " could not be cancelled. It may already be cancelled or completed.");
        }
        AppLog.info("Cancelled reservation RES" + reservationId);
    }

    /**
     * Marks an eligible confirmed reservation as COMPLETED.
     */
    public void markCompleted(int reservationId) {
        Optional<Reservation> opt = reservationDAO.getReservationById(reservationId);
        if (opt.isEmpty()) {
            throw new ValidationException("Reservation RES" + reservationId + " does not exist.");
        }

        Reservation res = opt.get();
        LocalDate today = DbClock.today();
        LocalTime now = DbClock.localTime();

        TimeSlot slot = getTimeSlots().stream()
                .filter(s -> s.getSlotId() == res.getSlotId())
                .findFirst()
                .orElse(null);

        boolean eligible = res.getReservationDate().isBefore(today) ||
                (res.getReservationDate().isEqual(today) && slot != null && !slot.getStartTime().isAfter(now));

        if (!eligible) {
            throw new ValidationException("Only past reservations or reservations whose seating has begun can be marked as COMPLETED.");
        }

        boolean success = reservationDAO.markCompleted(reservationId);
        if (!success) {
            throw new ValidationException("Failed to update status to COMPLETED.");
        }
        AppLog.info("Marked reservation RES" + reservationId + " as COMPLETED.");
    }

    /**
     * Retrieves table states for a given date and slot (used by Floor View).
     */
    public List<TableAvailability> getTableStates(LocalDate date, int slotId) {
        return tableDAO.getTableStates(date, slotId);
    }

    /**
     * Retrieves reservation details for a specific booked slot (used by Floor View click-to-view).
     */
    public Optional<model.ReservationDetail> getReservationForSlot(LocalDate date, int slotId, int tableId) {
        return reservationDAO.getReservationForSlot(date, slotId, tableId);
    }

    /**
     * Searches and filters reservations based on user criteria.
     */
    public List<model.ReservationDetail> searchReservations(model.ReservationFilter filter) {
        return reservationDAO.searchReservations(filter);
    }

    /**
     * Computes dashboard statistics: today's count, upcoming bookings, cancelled count,
     * available tables at next slot, and top upcoming reservations feed.
     */
    public DashboardStats getDashboardStats() {
        int todayCount = reservationDAO.getTodayCount();
        int upcomingCount = reservationDAO.getUpcomingCount();
        int cancelledCount = reservationDAO.getCancelledCount();

        LocalDate today = DbClock.today();
        LocalTime now = DbClock.localTime();
        List<TimeSlot> slots = getTimeSlots();
        TimeSlot targetSlot = null;
        LocalDate targetDate = today;

        for (TimeSlot slot : slots) {
            if (slot.getEndTime().isAfter(now)) {
                targetSlot = slot;
                break;
            }
        }

        if (targetSlot == null && !slots.isEmpty()) {
            targetDate = today.plusDays(1);
            targetSlot = slots.get(0);
        }

        int freeTables = 0;
        String slotLabel = "N/A";
        if (targetSlot != null) {
            freeTables = tableDAO.getAvailableTables(targetDate, targetSlot.getSlotId(), 1).size();
            if (targetDate.equals(today)) {
                slotLabel = "Free at " + targetSlot.getLabel();
            } else {
                slotLabel = "Tomorrow " + targetSlot.getLabel();
            }
        }

        List<model.ReservationDetail> upcoming = reservationDAO.getUpcomingReservations(8);
        return new DashboardStats(todayCount, freeTables, slotLabel, upcomingCount, cancelledCount, upcoming);
    }
}
