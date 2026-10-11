package service;

import dao.AuditDAO;
import dao.ReservationDAO;
import dao.SettingDAO;
import dao.TableDAO;
import dao.WaitlistDAO;
import db.DatabaseConnection;
import model.ReconfirmStatus;
import model.Reservation;
import model.ReservationSource;
import model.ReservationStatus;
import model.RestaurantTable;
import model.TableStatus;
import model.WaitlistEntry;
import model.WaitlistStatus;
import security.AuthContext;
import security.Permission;
import security.Session;
import service.notification.LogNotifier;
import service.notification.Notifier;
import service.offer.BestFitOfferPolicy;
import service.offer.OfferPolicy;
import util.AppLog;
import util.DbClock;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Core engine for the Slot Recovery Loop (P4).
 * Handles level-triggered slot refilling, 30-second sweeps, and race-safe offer accept/decline.
 */
public class RecoveryService {
    private final ReservationDAO reservationDAO;
    private final WaitlistDAO waitlistDAO;
    private final TableDAO tableDAO;
    private final SettingDAO settingDAO;
    private final AuditDAO auditDAO;
    private final AuthContext auth;
    private final OfferPolicy offerPolicy;
    private final Notifier notifier;

    public RecoveryService() {
        this(new ReservationDAO(), new WaitlistDAO(), new TableDAO(), new SettingDAO(),
                new AuditDAO(), Session.getInstance(), new BestFitOfferPolicy(), new LogNotifier());
    }

    public RecoveryService(ReservationDAO reservationDAO, WaitlistDAO waitlistDAO, TableDAO tableDAO,
                           SettingDAO settingDAO, AuditDAO auditDAO, AuthContext auth,
                           OfferPolicy offerPolicy, Notifier notifier) {
        this.reservationDAO = reservationDAO;
        this.waitlistDAO = waitlistDAO;
        this.tableDAO = tableDAO;
        this.settingDAO = settingDAO;
        this.auditDAO = auditDAO;
        this.auth = auth != null ? auth : Session.getInstance();
        this.offerPolicy = offerPolicy != null ? offerPolicy : new BestFitOfferPolicy();
        this.notifier = notifier != null ? notifier : new LogNotifier();
    }

    /**
     * Level-triggered, idempotent algorithm to refill freed tables in a slot.
     * Safe to invoke multiple times; serialized by table & waitlist row locks.
     */
    public int fillSlot(Connection con, LocalDate date, int slotId) throws SQLException {
        int offersMade = 0;
        int maxWaste = settingDAO.getInt("max_waste_seats", 2);
        int holdMinutes = settingDAO.getInt("hold_minutes", 10);
        LocalDateTime now = DbClock.localDateTime();
        LocalDateTime holdExpiry = now.plusMinutes(holdMinutes);

        // 1. Find all available tables ordered by capacity DESC
        List<RestaurantTable> allTables = tableDAO.getAllTables(con);
        List<RestaurantTable> freeTables = new ArrayList<>();
        for (RestaurantTable t : allTables) {
            if (t.getStatus() == TableStatus.AVAILABLE) {
                if (!reservationDAO.isSlotTaken(con, t.getTableId(), date, slotId)) {
                    freeTables.add(t);
                }
            }
        }
        freeTables.sort((a, b) -> Integer.compare(b.getCapacity(), a.getCapacity()));

        // 2. For each free table, find and lock eligible WAITING entries
        for (RestaurantTable t : freeTables) {
            List<WaitlistEntry> candidates = waitlistDAO.findActiveWaitingLocked(con, date, slotId);
            if (candidates.isEmpty()) continue;

            Optional<WaitlistEntry> pickOpt = offerPolicy.pick(t, candidates, maxWaste);
            if (pickOpt.isPresent()) {
                WaitlistEntry pick = pickOpt.get();

                // Create HELD reservation
                Reservation held = new Reservation(
                        0, pick.getCustomerId(), t.getTableId(), date, slotId,
                        pick.getPartySize(), ReservationStatus.HELD, now,
                        auth.currentUser() != null ? auth.currentUser().getUserId() : null,
                        ReservationSource.WAITLIST, null, holdExpiry, ReconfirmStatus.PENDING
                );

                try {
                    reservationDAO.createReservation(con, held);
                    waitlistDAO.updateStatus(con, pick.getEntryId(), WaitlistStatus.OFFERED, held.getReservationId());

                    Integer currentUserId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
                    auditDAO.log(con, currentUserId, "OFFER_MADE", "WAITLIST", (long) pick.getEntryId(),
                            "Created HELD offer for " + pick.getCustomerName() + " on Table " + t.getTableNumber()
                                    + " (Hold expires in " + holdMinutes + "m)");

                    notifier.offerCreated(pick, held, t);
                    offersMade++;
                } catch (SQLException e) {
                    // If ORA-00001 (concurrent booking won the slot), skip gracefully
                    if (e.getErrorCode() == 1) {
                        AppLog.warning("Concurrent allocation detected for table " + t.getTableNumber() + ". Skipping.");
                    } else {
                        throw e;
                    }
                }
            }
        }
        return offersMade;
    }

    /**
     * Background sweep: lapses expired waitlist offers and reclaims slots for the next candidates.
     */
    public int sweep() {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                LocalDateTime now = DbClock.localDateTime();

                // 1. Identify which reservations are expiring
                String findExpiringSql = "SELECT reservation_date, slot_id FROM reservation " +
                                         "WHERE status = 'HELD' AND hold_expires_at <= ?";
                Set<SlotKey> affectedSlots = new HashSet<>();
                try (var ps = con.prepareStatement(findExpiringSql)) {
                    ps.setTimestamp(1, java.sql.Timestamp.valueOf(now));
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            affectedSlots.add(new SlotKey(rs.getDate(1).toLocalDate(), rs.getInt(2)));
                        }
                    }
                }

                // 2. Lapse waitlist entries & cancel held reservations
                int lapsedCount = waitlistDAO.lapseExpiredOffers(con, now);
                int cancelledHolds = reservationDAO.cancelExpiredHolds(con, now);

                // 3. For each affected slot, trigger fillSlot
                for (SlotKey slot : affectedSlots) {
                    fillSlot(con, slot.date(), slot.slotId());
                }

                con.commit();
                if (lapsedCount > 0) {
                    AppLog.info("Sweep lapsed " + lapsedCount + " expired offers, cancelled " + cancelledHolds + " holds.");
                }
                return lapsedCount;
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "RecoveryService.sweep");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "RecoveryService.sweep");
        }
    }

    /**
     * Accepts a held waitlist offer race-safely.
     */
    public void acceptOffer(int waitlistEntryId, boolean seatImmediately) {
        auth.require(Permission.WAITLIST_MANAGE);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Optional<WaitlistEntry> opt = waitlistDAO.findById(waitlistEntryId);
                if (opt.isEmpty()) {
                    throw new exception.ValidationException("Waitlist entry #" + waitlistEntryId + " not found.");
                }
                WaitlistEntry entry = opt.get();
                if (entry.getStatus() != WaitlistStatus.OFFERED || entry.getOfferedReservationId() == null) {
                    throw new exception.ValidationException("Entry is not currently in OFFERED status.");
                }

                LocalDateTime now = DbClock.localDateTime();
                ReservationStatus targetStatus = seatImmediately ? ReservationStatus.SEATED : ReservationStatus.CONFIRMED;

                boolean accepted = reservationDAO.acceptHeldOffer(con, entry.getOfferedReservationId(), targetStatus, now);
                if (!accepted) {
                    throw new exception.ValidationException("Offer has expired and is no longer available.");
                }

                waitlistDAO.updateStatus(con, waitlistEntryId, WaitlistStatus.FULFILLED, entry.getOfferedReservationId());

                Integer currentUserId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
                auditDAO.log(con, currentUserId, "OFFER_ACCEPTED", "WAITLIST", (long) waitlistEntryId,
                        "Offer accepted for " + entry.getCustomerName() + " (Res #" + entry.getOfferedReservationId() + " -> " + targetStatus + ")");

                con.commit();
                AppLog.info("Offer accepted: " + entry);
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "RecoveryService.acceptOffer");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "RecoveryService.acceptOffer");
        }
    }

    /**
     * Declines a held waitlist offer and immediately offers the table to the next in line.
     */
    public void declineOffer(int waitlistEntryId) {
        auth.require(Permission.WAITLIST_MANAGE);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Optional<WaitlistEntry> opt = waitlistDAO.findById(waitlistEntryId);
                if (opt.isEmpty()) {
                    throw new exception.ValidationException("Waitlist entry #" + waitlistEntryId + " not found.");
                }
                WaitlistEntry entry = opt.get();
                if (entry.getStatus() != WaitlistStatus.OFFERED || entry.getOfferedReservationId() == null) {
                    throw new exception.ValidationException("Entry is not in OFFERED status.");
                }

                // 1. Cancel the held reservation
                reservationDAO.cancelReservation(con, entry.getOfferedReservationId());

                // 2. Update waitlist entry to CANCELLED
                waitlistDAO.updateStatus(con, waitlistEntryId, WaitlistStatus.CANCELLED, entry.getOfferedReservationId());

                Integer currentUserId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
                auditDAO.log(con, currentUserId, "OFFER_DECLINED", "WAITLIST", (long) waitlistEntryId,
                        "Offer declined for " + entry.getCustomerName() + ". Refilling slot for next candidate.");

                // 3. Immediately refill slot for next in line
                if (entry.getSlotId() != null) {
                    fillSlot(con, entry.getServiceDate(), entry.getSlotId());
                }

                con.commit();
                AppLog.info("Offer declined and slot refilled: " + entry);
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "RecoveryService.declineOffer");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "RecoveryService.declineOffer");
        }
    }

    private record SlotKey(LocalDate date, int slotId) {}
}
