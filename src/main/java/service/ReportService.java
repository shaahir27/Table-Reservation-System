package service;

import dao.ReportDAO;
import dao.ReportResult;
import exception.ValidationException;

import java.time.LocalDate;

/**
 * Service managing analytics and operational reporting (Section 10.7).
 */
public class ReportService {
    private final ReportDAO reportDAO;

    public ReportService() {
        this(new ReportDAO());
    }

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    public ReportResult getTodaysReservationsReport() {
        return reportDAO.getTodaysReservations();
    }

    public ReportResult getReservationsByDateRangeReport(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new ValidationException("Both start and end dates are required.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ValidationException("Start date cannot be after end date.");
        }
        return reportDAO.getReservationsByDateRange(fromDate, toDate);
    }

    public ReportResult getReservationsByStatusReport() {
        return reportDAO.getReservationsByStatus();
    }

    public ReportResult getMostBookedTablesReport() {
        return reportDAO.getMostFrequentlyBookedTables();
    }

    public ReportResult getReservationsBySlotReport() {
        return reportDAO.getReservationsByTimeSlot();
    }
}
