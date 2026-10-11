package service;

import dao.AuditDAO;
import dao.WaitlistDAO;
import db.DatabaseConnection;
import model.WaitlistEntry;
import model.WaitlistStatus;
import security.AuthContext;
import security.Permission;
import security.Session;
import util.AppLog;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service managing customer waitlist entries and permissions (P4).
 */
public class WaitlistService {
    private final WaitlistDAO waitlistDAO;
    private final AuditDAO auditDAO;
    private final AuthContext auth;

    public WaitlistService() {
        this(new WaitlistDAO(), new AuditDAO(), Session.getInstance());
    }

    public WaitlistService(WaitlistDAO waitlistDAO, AuditDAO auditDAO, AuthContext auth) {
        this.waitlistDAO = waitlistDAO;
        this.auditDAO = auditDAO;
        this.auth = auth != null ? auth : Session.getInstance();
    }

    public WaitlistEntry addToWaitlist(WaitlistEntry entry) {
        auth.require(Permission.RESERVATION_CREATE);

        if (entry == null || entry.getCustomerId() <= 0) {
            throw new exception.ValidationException("Valid customer is required to join waitlist.");
        }
        if (entry.getServiceDate() == null) {
            throw new exception.ValidationException("Service date is required.");
        }
        if (entry.getPartySize() < 1 || entry.getPartySize() > 20) {
            throw new exception.ValidationException("Party size must be between 1 and 20 guests.");
        }

        Integer currentUserId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
        entry.setCreatedBy(currentUserId);
        entry.setStatus(WaitlistStatus.WAITING);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                waitlistDAO.insert(con, entry);
                auditDAO.log(con, currentUserId, "WAITLIST_JOIN", "WAITLIST", (long) entry.getEntryId(),
                        "Customer #" + entry.getCustomerId() + " joined waitlist for " + entry.getServiceDate()
                                + " (" + entry.getPartySize() + " guests)");
                con.commit();
                AppLog.info("Added to waitlist: " + entry);
                return entry;
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "WaitlistService.addToWaitlist");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "WaitlistService.addToWaitlist");
        }
    }

    public void cancelEntry(int entryId) {
        auth.require(Permission.RESERVATION_CANCEL);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Optional<WaitlistEntry> opt = waitlistDAO.findById(entryId);
                if (opt.isEmpty()) {
                    throw new exception.ValidationException("Waitlist entry #" + entryId + " not found.");
                }
                WaitlistEntry entry = opt.get();
                if (entry.getStatus() != WaitlistStatus.WAITING && entry.getStatus() != WaitlistStatus.OFFERED) {
                    throw new exception.ValidationException("Cannot cancel waitlist entry in status " + entry.getStatus());
                }

                waitlistDAO.updateStatus(con, entryId, WaitlistStatus.CANCELLED, null);
                Integer currentUserId = auth.currentUser() != null ? auth.currentUser().getUserId() : null;
                auditDAO.log(con, currentUserId, "WAITLIST_CANCEL", "WAITLIST", (long) entryId,
                        "Cancelled waitlist entry for " + entry.getCustomerName());
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "WaitlistService.cancelEntry");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "WaitlistService.cancelEntry");
        }
    }

    public List<WaitlistEntry> getWaitingForDate(LocalDate date) {
        return waitlistDAO.findByDate(date);
    }

    public List<WaitlistEntry> getActiveOffers(LocalDate date) {
        return waitlistDAO.findActiveOffers(date);
    }

    public int countWaiting(LocalDate date) {
        return waitlistDAO.countWaiting(date);
    }
}
