package service;

import dao.ReportDAO;
import exception.AccessDeniedException;
import exception.ValidationException;
import model.AppUser;
import model.analytics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import security.AuthContext;
import security.Role;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying ReportService and its new analytics dashboard capabilities,
 * role authorization, and validation rules.
 */
class ReportServiceAnalyticsTest {

    private AppUser managerUser;
    private AppUser hostUser;
    private AnalyticsSummary sampleSummary;
    private boolean daoCalled;

    @BeforeEach
    void setUp() {
        managerUser = new AppUser("mgr_alice", "Alice Manager", Role.MANAGER, "hash", "salt", 600000);
        hostUser = new AppUser("host_bob", "Bob Host", Role.HOST, "hash", "salt", 600000);

        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 7);

        sampleSummary = new AnalyticsSummary(from, to);
        sampleSummary.setTotalCovers(184);
        sampleSummary.setTotalParties(52);
        sampleSummary.setAvgPartySize(3.54);
        sampleSummary.setSeatUtilizationPct(68.5);
        sampleSummary.setNoShowCount(3);
        sampleSummary.setNoShowPct(5.77);
        sampleSummary.setCancellationCount(4);
        sampleSummary.setCancellationPct(7.69);
        sampleSummary.setRepeatCustomerCount(18);
        sampleSummary.setRepeatCustomerPct(34.62);
        sampleSummary.setRecoveredCovers(12);

        sampleSummary.setDailyTrends(List.of(
                new DailyCoverTrend(LocalDate.of(2026, 10, 1), 25, 8),
                new DailyCoverTrend(LocalDate.of(2026, 10, 2), 32, 10)
        ));

        sampleSummary.setSlotMetrics(List.of(
                new SlotLoadMetric(1, "19:00 - 21:00", "19:00", 18, 64, 35.0),
                new SlotLoadMetric(2, "13:00 - 15:00", "13:00", 12, 42, 23.0)
        ));

        sampleSummary.setTableMetrics(List.of(
                new TablePerformanceMetric(1, "T01", 4, "Indoor", 15, 58, 3.87, 96.67),
                new TablePerformanceMetric(2, "T02", 2, "Outdoor", 12, 24, 2.0, 100.0)
        ));

        sampleSummary.setTopGuests(List.of(
                new TopGuestMetric(101, "John Smith", "9876543210", "VIP, Window Seat", 6, 22, LocalDate.of(2026, 10, 6)),
                new TopGuestMetric(102, "Sarah Connor", "9123456780", "Anniversary", 4, 16, LocalDate.of(2026, 10, 5))
        ));

        sampleSummary.setTagDistribution(List.of(
                new TagCountMetric("VIP", 14),
                new TagCountMetric("Window Seat", 9)
        ));

        sampleSummary.setWaitlistFunnel(List.of(
                new WaitlistFunnelStep("Total Waitlist Entries", 15, 100.0),
                new WaitlistFunnelStep("Offered / Notified", 12, 80.0),
                new WaitlistFunnelStep("Successfully Converted", 9, 60.0)
        ));

        sampleSummary.setKeyTakeaways(List.of(
                "Peak Demand: 19:00 - 21:00 captured 64 covers across 18 reservations.",
                "Healthy Guest Loyalty: 34.6% of reservations were from returning diners."
        ));

        daoCalled = false;
    }

    private ReportService createService(AppUser user) {
        ReportDAO fakeDao = new ReportDAO() {
            @Override
            public AnalyticsSummary getAnalyticsDashboard(LocalDate fromDate, LocalDate toDate) {
                daoCalled = true;
                return sampleSummary;
            }
        };
        return new ReportService(fakeDao, AuthContext.fixed(user));
    }

    @Test
    @DisplayName("Host user is denied access to analytics dashboard (Permission.REPORT_VIEW required)")
    void testHostDeniedAccess() {
        ReportService service = createService(hostUser);
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 7);

        assertThrows(AccessDeniedException.class, () -> service.getAnalyticsDashboard(from, to));
        assertFalse(daoCalled, "DAO should not be called if permission check fails");
    }

    @Test
    @DisplayName("Manager user can access analytics dashboard")
    void testManagerAccessAllowed() {
        ReportService service = createService(managerUser);
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 7);

        AnalyticsSummary summary = service.getAnalyticsDashboard(from, to);

        assertTrue(daoCalled, "DAO should be invoked");
        assertNotNull(summary);
        assertEquals(184, summary.getTotalCovers());
        assertEquals(52, summary.getTotalParties());
        assertEquals(3.54, summary.getAvgPartySize());
        assertEquals(68.5, summary.getSeatUtilizationPct());
        assertEquals(3, summary.getNoShowCount());
        assertEquals(5.77, summary.getNoShowPct());
        assertEquals(34.62, summary.getRepeatCustomerPct());
        assertEquals(12, summary.getRecoveredCovers());

        assertEquals(2, summary.getDailyTrends().size());
        assertEquals(2, summary.getSlotMetrics().size());
        assertEquals(2, summary.getTableMetrics().size());
        assertEquals(2, summary.getTopGuests().size());
        assertEquals(2, summary.getTagDistribution().size());
        assertEquals(3, summary.getWaitlistFunnel().size());
        assertEquals(2, summary.getKeyTakeaways().size());
    }

    @Test
    @DisplayName("Null date parameters throw ValidationException")
    void testNullDatesValidation() {
        ReportService service = createService(managerUser);
        LocalDate validDate = LocalDate.of(2026, 10, 1);

        assertThrows(ValidationException.class, () -> service.getAnalyticsDashboard(null, validDate));
        assertThrows(ValidationException.class, () -> service.getAnalyticsDashboard(validDate, null));
        assertThrows(ValidationException.class, () -> service.getAnalyticsDashboard(null, null));
        assertFalse(daoCalled);
    }

    @Test
    @DisplayName("Inverted date range throws ValidationException")
    void testInvertedDateRangeValidation() {
        ReportService service = createService(managerUser);
        LocalDate from = LocalDate.of(2026, 10, 10);
        LocalDate to = LocalDate.of(2026, 10, 5);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.getAnalyticsDashboard(from, to));
        assertTrue(ex.getMessage().contains("Start date cannot be after end date"));
        assertFalse(daoCalled);
    }
}
