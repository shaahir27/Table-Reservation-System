package service;

import dao.AuditDAO;
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
import model.ReconfirmStatus;
import model.Reservation;
import model.ReservationDetail;
import model.ReservationSource;
import model.ReservationStatus;
import model.RestaurantTable;
import model.TableAvailability;
import model.TableState;
import model.TableStatus;
import model.TimeSlot;
import security.AuthContext;
import security.Permission;
import security.Session;
import util.AppLog;
import util.DbClock;
import util.OracleErrorMapper;
import util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Core business service managing reservations, table recommendations,
 * lifecycle state transitions (P3), and automated waitlist recovery (P4).
 */
public class ReservationService {
    private final ReservationDAO reservationDAO;
    private final TableDAO tableDAO;
    private final CustomerDAO customerDAO;
    private final TimeSlotDAO timeSlotDAO;
    private final AuditDAO auditDAO;
    private final AuthContext auth;
    private RecoveryService recoveryService;

    // In-memory cache for static time slots
    private List<TimeSlot> cachedSlots = null;

    public ReservationService() {
        this(new ReservationDAO(), new TableDAO(), new CustomerDAO(), new TimeSlotDAO(), new AuditDAO(), Session.getInstance());
    }

    public ReservationService(ReservationDAO reservationDAO, TableDAO tableDAO, 
                              CustomerDAO customerDAO, TimeSlotDAO timeSlotDAO) {
        this(reservationDAO, tableDAO, customerDAO, timeSlotDAO, new AuditDAO(), Session.getInstance());
    }

    public ReservationService(ReservationDAO reservationDAO, TableDAO tableDAO, 
                              CustomerDAO customerDAO, TimeSlotDAO timeSlotDAO,
                              AuditDAO auditDAO, AuthContext auth) {
        this(reservationDAO, tableDAO, customerDAO, timeSlotDAO, auditDAO, auth, null);
    }

    public ReservationService(ReservationDAO reservationDAO, TableDAO tableDAO, 
                              CustomerDAO customerDAO, TimeSlotDAO timeSlotDAO,
                              AuditDAO auditDAO, AuthContext auth, RecoveryService recoveryService) {
        this.reservationDAO = reservationDAO;
        this.tableDAO = tableDAO;
        this.customerDAO = customerDAO;
        this.timeSlotDAO = timeSlotDAO;
        this.auditDAO = auditDAO != null ? auditDAO : new AuditDAO();
        this.auth = auth != null ? auth : Session.getInstance();
        this.recoveryService = recoveryService;
    }

    public void setRecoveryService(RecoveryService recoveryService) {
        this.recoveryService = recoveryService;
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
        auth.require(Permission.RESERVATION_CREATE);

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

                // 4. Insert reservation
                Integer userId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
                Reservation newReservation = new Reservation(
                        0, customerId, tableId, date, slot.getSlotId(), guests,
                        ReservationStatus.CONFIRMED, DbClock.localDateTime(), userId,
                        ReservationSource.BOOKING, null, null, ReconfirmStatus.PENDING
                );
                Reservation created = reservationDAO.createReservation(con, newReservation);

                // 5. Transactional audit log entry in the same transaction
                auditDAO.log(con, userId, "RES_CREATED", "RESERVATION", (long) created.getReservationId(),
                        "Table " + lockedTable.getTableNumber() + " · " + date + " · " + slot.getLabel() + " · " + guests + " guests");

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
     * Seats an arriving party (P3 Lifecycle transition: CONFIRMED or HELD -> SEATED).
     */
    public void seat(int reservationId) {
        auth.require(Permission.RESERVATION_SEAT);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Optional<Reservation> opt = reservationDAO.getReservationById(con, reservationId);
                if (opt.isEmpty()) {
                    throw new ValidationException("Reservation RES" + reservationId + " does not exist.");
                }
                Reservation res = opt.get();
                if (!res.getStatus().canTransitionTo(ReservationStatus.SEATED)) {
                    throw new ValidationException("Cannot seat reservation in status " + res.getStatus());
                }

                LocalDateTime now = DbClock.localDateTime();
                reservationDAO.seat(con, reservationId, now);

                Integer userId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
                auditDAO.log(con, userId, "RES_SEATED", "RESERVATION", (long) reservationId,
                        "Guest arrived and seated at Table #" + res.getTableId());

                con.commit();
                AppLog.info("Seated reservation RES" + reservationId);
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "ReservationService.seat");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationService.seat");
        }
    }

    /**
     * Marks an absent confirmed party as NO_SHOW and immediately triggers Slot Recovery (P3+P4).
     */
    public void markNoShow(int reservationId) {
        auth.require(Permission.RESERVATION_NOSHOW);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Optional<Reservation> opt = reservationDAO.getReservationById(con, reservationId);
                if (opt.isEmpty()) {
                    throw new ValidationException("Reservation RES" + reservationId + " does not exist.");
                }
                Reservation res = opt.get();
                if (!res.getStatus().canTransitionTo(ReservationStatus.NO_SHOW)) {
                    throw new ValidationException("Cannot mark as NO_SHOW from status " + res.getStatus());
                }

                // 1. Update status to NO_SHOW (frees slot in UQ_ACTIVE_BOOKING)
                reservationDAO.markNoShow(con, reservationId);

                Integer userId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
                auditDAO.log(con, userId, "RES_NO_SHOW", "RESERVATION", (long) reservationId,
                        "Marked as NO_SHOW. Table #" + res.getTableId() + " released.");

                // 2. Trigger slot refill within same transaction if recoveryService is active
                if (recoveryService != null) {
                    recoveryService.fillSlot(con, res.getReservationDate(), res.getSlotId());
                }

                con.commit();
                AppLog.info("Marked reservation RES" + reservationId + " as NO_SHOW and refilled slot.");
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "ReservationService.markNoShow");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationService.markNoShow");
        }
    }

    /**
     * Marks a seated party as COMPLETED upon concluding their dining experience (P3 Lifecycle).
     */
    public void complete(int reservationId) {
        auth.require(Permission.RESERVATION_SEAT);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Optional<Reservation> opt = reservationDAO.getReservationById(con, reservationId);
                if (opt.isEmpty()) {
                    throw new ValidationException("Reservation RES" + reservationId + " does not exist.");
                }
                Reservation res = opt.get();
                if (!res.getStatus().canTransitionTo(ReservationStatus.COMPLETED)) {
                    throw new ValidationException("Cannot complete reservation from status " + res.getStatus() + ". Only SEATED bookings can be completed.");
                }

                reservationDAO.markCompleted(con, reservationId);

                Integer userId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
                auditDAO.log(con, userId, "RES_COMPLETED", "RESERVATION", (long) reservationId,
                        "Table #" + res.getTableId() + " service completed.");

                con.commit();
                AppLog.info("Completed reservation RES" + reservationId);
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "ReservationService.complete");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationService.complete");
        }
    }

    /**
     * Backward-compatible alias for complete().
     */
    public void markCompleted(int reservationId) {
        complete(reservationId);
    }

    /**
     * Seats an immediate walk-in party into the current ongoing slot (P3 Lifecycle).
     */
    public Reservation walkIn(int customerId, int guests, String notes) {
        return walkIn(customerId, guests, notes, null);
    }

    public Reservation walkIn(int customerId, int guests, String notes, Integer specificTableId) {
        auth.require(Permission.RESERVATION_CREATE);

        LocalDate today = DbClock.today();
        LocalTime now = DbClock.localTime();
        List<TimeSlot> slots = getTimeSlots();

        // Find active ongoing or upcoming slot for today
        TimeSlot currentSlot = null;
        for (TimeSlot s : slots) {
            if (!now.isBefore(s.getStartTime()) && now.isBefore(s.getEndTime())) {
                currentSlot = s;
                break;
            }
        }
        if (currentSlot == null) {
            // Pick next upcoming slot today if any
            for (TimeSlot s : slots) {
                if (now.isBefore(s.getStartTime())) {
                    currentSlot = s;
                    break;
                }
            }
        }
        if (currentSlot == null) {
            throw new ValidationException("No active dining slots remaining today for walk-in seating.");
        }

        // Find available tables fitting party
        List<RestaurantTable> candidates = tableDAO.getAvailableTables(today, currentSlot.getSlotId(), guests);
        if (candidates.isEmpty()) {
            throw new ValidationException("No available tables fit " + guests + " guests for slot " + currentSlot.getLabel());
        }

        RestaurantTable table;
        if (specificTableId != null) {
            table = candidates.stream()
                    .filter(t -> t.getTableId() == specificTableId)
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("Selected table is no longer available or does not fit " + guests + " guests."));
        } else {
            TableAvailability rec = recommendTable(candidates, guests);
            if (rec == null || rec.getTable() == null) {
                throw new ValidationException("No suitable table found.");
            }
            table = rec.getTable();
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                tableDAO.lockTable(con, table.getTableId());
                if (reservationDAO.isSlotTaken(con, table.getTableId(), today, currentSlot.getSlotId())) {
                    throw new BookingConflictException("Table was just occupied by another booking.");
                }

                Integer userId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
                LocalDateTime seatedNow = DbClock.localDateTime();

                Reservation walkInRes = new Reservation(
                        0, customerId, table.getTableId(), today, currentSlot.getSlotId(), guests,
                        ReservationStatus.SEATED, seatedNow, userId,
                        ReservationSource.WALK_IN, seatedNow, null, ReconfirmStatus.CONFIRMED
                );
                Reservation created = reservationDAO.createReservation(con, walkInRes);

                auditDAO.log(con, userId, "WALK_IN_SEATED", "RESERVATION", (long) created.getReservationId(),
                        "Walk-in (" + guests + " guests) seated immediately on Table " + table.getTableNumber());

                con.commit();
                AppLog.info("Walk-in seated: RES" + created.getReservationId() + " on Table " + table.getTableNumber());
                return created;
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "ReservationService.walkIn");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationService.walkIn");
        }
    }

    /**
     * Checks if a table has an upcoming reservation booked for any subsequent slot today.
     */
    public Optional<TimeSlot> getNextBookedSlotForTable(int tableId, LocalDate date, int currentSlotId) {
        List<TimeSlot> slots = getTimeSlots();
        for (TimeSlot s : slots) {
            if (s.getSlotId() > currentSlotId) {
                if (reservationDAO.isSlotTaken(tableId, date, s.getSlotId())) {
                    return Optional.of(s);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Cancels an active reservation and triggers the Slot Recovery Loop to offer the table to waitlist.
     */
    public void cancelReservation(int reservationId) {
        auth.require(Permission.RESERVATION_CANCEL);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Optional<Reservation> opt = reservationDAO.getReservationById(con, reservationId);
                if (opt.isEmpty()) {
                    throw new ValidationException("Reservation RES" + reservationId + " does not exist.");
                }
                Reservation res = opt.get();
                if (!res.getStatus().canTransitionTo(ReservationStatus.CANCELLED)) {
                    throw new ValidationException("Cannot cancel reservation in status " + res.getStatus());
                }

                reservationDAO.cancelReservation(con, reservationId);

                Integer userId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
                auditDAO.log(con, userId, "RES_CANCELLED", "RESERVATION", (long) reservationId,
                        "Cancelled reservation RES" + reservationId);

                // Slot recovery refill in same transaction
                if (recoveryService != null) {
                    recoveryService.fillSlot(con, res.getReservationDate(), res.getSlotId());
                }

                con.commit();
                AppLog.info("Cancelled reservation RES" + reservationId + " and refilled slot.");
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "ReservationService.cancelReservation");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationService.cancelReservation");
        }
    }

    /**
     * Updates reconfirmation status.
     */
    public void updateReconfirmStatus(int reservationId, ReconfirmStatus status) {
        auth.require(Permission.RESERVATION_SEAT);

        try (Connection con = DatabaseConnection.getConnection()) {
            reservationDAO.updateReconfirmStatus(con, reservationId, status);
            Integer userId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
            auditDAO.log(con, userId, "RECONFIRM_UPDATE", "RESERVATION", (long) reservationId,
                    "Reconfirmation set to " + status.name());
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationService.updateReconfirmStatus");
        }
    }

    /**
     * Retrieves all reservations for a given service date (Service Board).
     */
    public List<ReservationDetail> getReservationsByDate(LocalDate date) {
        return reservationDAO.getReservationsByDate(date);
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
     * Computes dashboard statistics.
     */
    public DashboardStats getDashboardStats() {
        ReservationDAO.DashboardCounts counts = reservationDAO.getDashboardCounts();

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
        return new DashboardStats(counts.todayCount(), freeTables, slotLabel, counts.upcomingCount(), counts.cancelledCount(), counts.recoveredCount(), upcoming);
    }

    /**
     * Retrieves total recovered covers saved by the Slot Recovery Loop.
     */
    public int getRecoveredCoversCount() {
        return reservationDAO.getRecoveredCoversCount();
    }

    /**
     * Retrieves all reservations for a specific customer.
     */
    public List<ReservationDetail> getReservationsByCustomer(int customerId) {
        return reservationDAO.getReservationsByCustomerId(customerId);
    }
}
