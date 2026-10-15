package service;

import dao.ReportDAO;
import dao.ReportResult;
import exception.ValidationException;
import model.analytics.AnalyticsSummary;
import security.AuthContext;
import security.Permission;
import security.Session;

import java.time.LocalDate;

/**
 * Service managing analytics and operational reporting.
 * Access restricted to roles with REPORT_VIEW permission (MANAGER).
 */
public class ReportService {
    private final ReportDAO reportDAO;
    private final AuthContext auth;

    public ReportService() {
        this(new ReportDAO(), Session.getInstance());
    }

    public ReportService(ReportDAO reportDAO) {
        this(reportDAO, Session.getInstance());
    }

    public ReportService(ReportDAO reportDAO, AuthContext auth) {
        this.reportDAO = reportDAO;
        this.auth = auth != null ? auth : Session.getInstance();
    }

    public ReportResult getTodaysReservationsReport() {
        auth.require(Permission.REPORT_VIEW);
        return reportDAO.getTodaysReservations();
    }

    public ReportResult getReservationsByDateRangeReport(LocalDate fromDate, LocalDate toDate) {
        auth.require(Permission.REPORT_VIEW);
        if (fromDate == null || toDate == null) {
            throw new ValidationException("Both start and end dates are required.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ValidationException("Start date cannot be after end date.");
        }
        return reportDAO.getReservationsByDateRange(fromDate, toDate);
    }

    public ReportResult getReservationsByStatusReport() {
        auth.require(Permission.REPORT_VIEW);
        return reportDAO.getReservationsByStatus();
    }

    public ReportResult getMostBookedTablesReport() {
        auth.require(Permission.REPORT_VIEW);
        return reportDAO.getMostFrequentlyBookedTables();
    }

    public ReportResult getReservationsBySlotReport() {
        auth.require(Permission.REPORT_VIEW);
        return reportDAO.getReservationsByTimeSlot();
    }

    public ReportResult getNoShowAnalysisReport() {
        auth.require(Permission.REPORT_VIEW);
        return reportDAO.getNoShowAnalysis();
    }

    public ReportResult getRecoveredCoversReport() {
        auth.require(Permission.REPORT_VIEW);
        return reportDAO.getRecoveredCovers();
    }

    public ReportResult getSlotUtilisationReport(LocalDate fromDate, LocalDate toDate) {
        auth.require(Permission.REPORT_VIEW);
        if (fromDate == null || toDate == null) {
            throw new ValidationException("Both start and end dates are required.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ValidationException("Start date cannot be after end date.");
        }
        return reportDAO.getSlotUtilisation(fromDate, toDate);
    }

    public ReportResult getWaitlistFunnelReport() {
        auth.require(Permission.REPORT_VIEW);
        return reportDAO.getWaitlistFunnel();
    }

    public AnalyticsSummary getAnalyticsDashboard(LocalDate fromDate, LocalDate toDate) {
        auth.require(Permission.REPORT_VIEW);
        if (fromDate == null || toDate == null) {
            throw new ValidationException("Both start and end dates are required.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ValidationException("Start date cannot be after end date.");
        }
        return reportDAO.getAnalyticsDashboard(fromDate, toDate);
    }
}
