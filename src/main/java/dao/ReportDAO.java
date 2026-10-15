package dao;

import db.DatabaseConnection;
import util.OracleErrorMapper;

import model.analytics.AnalyticsSummary;
import model.analytics.DailyCoverTrend;
import model.analytics.SlotLoadMetric;
import model.analytics.TablePerformanceMetric;
import model.analytics.TagCountMetric;
import model.analytics.TopGuestMetric;
import model.analytics.WaitlistFunnelStep;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Data Access Object for generating analytical reports (Section 7 & 10.7).
 * All queries strictly use bound parameters and return a unified ReportResult.
 */
public class ReportDAO {

    /**
     * Report 1: Today's reservations ordered by seating time and table number.
     */
    public ReportResult getTodaysReservations() {
        String title = "Today's Reservations (" + util.DbClock.today() + ")";
        List<String> columns = Arrays.asList(
                "ID", "Customer", "Phone", "Table", "Time Slot", "Guests", "Status", "Booked At"
        );

        String sql = "SELECT r.reservation_id, c.customer_name, c.phone, t.table_number, " +
                     "       s.slot_label, r.guest_count, r.status, " +
                     "       TO_CHAR(r.created_at, 'DD-Mon-YYYY HH24:MI') AS booked_at " +
                     "FROM   reservation r " +
                     "JOIN   customer c         ON c.customer_id = r.customer_id " +
                     "JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                     "JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                     "WHERE  r.reservation_date = TRUNC(SYSDATE) " +
                     "ORDER  BY s.start_time ASC, t.table_number ASC";

        return executeQuery(title, columns, sql, null);
    }

    /**
     * Report 2: Reservations within a specific date range.
     */
    public ReportResult getReservationsByDateRange(LocalDate fromDate, LocalDate toDate) {
        String title = "Reservations from " + fromDate + " to " + toDate;
        List<String> columns = Arrays.asList(
                "ID", "Date", "Customer", "Phone", "Table", "Time Slot", "Guests", "Status"
        );

        String sql = "SELECT r.reservation_id, TO_CHAR(r.reservation_date, 'YYYY-MM-DD') AS res_date, " +
                     "       c.customer_name, c.phone, t.table_number, s.slot_label, " +
                     "       r.guest_count, r.status " +
                     "FROM   reservation r " +
                     "JOIN   customer c         ON c.customer_id = r.customer_id " +
                     "JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                     "JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                     "WHERE  r.reservation_date BETWEEN ? AND ? " +
                     "ORDER  BY r.reservation_date DESC, s.start_time ASC";

        return executeQuery(title, columns, sql, ps -> {
            ps.setDate(1, Date.valueOf(fromDate));
            ps.setDate(2, Date.valueOf(toDate));
        });
    }

    /**
     * Report 3: Reservations grouped by status.
     */
    public ReportResult getReservationsByStatus() {
        String title = "Reservations by Status";
        List<String> columns = Arrays.asList("Reservation Status", "Total Bookings");

        String sql = "SELECT status, COUNT(*) AS total_count " +
                     "FROM   reservation " +
                     "GROUP  BY status " +
                     "ORDER  BY total_count DESC";

        return executeQuery(title, columns, sql, null);
    }

    /**
     * Report 4: Most frequently booked tables.
     */
    public ReportResult getMostFrequentlyBookedTables() {
        String title = "Most Frequently Booked Tables";
        List<String> columns = Arrays.asList("Table Number", "Capacity", "Location", "Total Bookings");

        String sql = "SELECT t.table_number, t.capacity, t.location, " +
                     "       COUNT(r.reservation_id) AS total_bookings " +
                     "FROM   restaurant_table t " +
                     "LEFT   JOIN reservation r " +
                     "       ON r.table_id = t.table_id AND r.status IN " + SqlFragments.ACTIVE_BOOKING_STATUSES_IN + " " +
                     "GROUP  BY t.table_number, t.capacity, t.location " +
                     "ORDER  BY total_bookings DESC, t.table_number ASC";

        return executeQuery(title, columns, sql, null);
    }

    /**
     * Report 5: Reservation count grouped by time slot.
     */
    public ReportResult getReservationsByTimeSlot() {
        String title = "Reservation Count by Time Slot";
        List<String> columns = Arrays.asList("Time Slot", "Timing", "Total Bookings");

        String sql = "SELECT s.slot_label, (s.start_time || ' - ' || s.end_time) AS timing, " +
                     "       COUNT(r.reservation_id) AS total_bookings " +
                     "FROM   time_slot s " +
                     "LEFT   JOIN reservation r " +
                     "       ON r.slot_id = s.slot_id AND r.status IN " + SqlFragments.ACTIVE_BOOKING_STATUSES_IN + " " +
                     "GROUP  BY s.slot_label, s.start_time, s.end_time " +
                     "ORDER  BY s.start_time ASC";

        return executeQuery(title, columns, sql, null);
    }

    /**
     * Report 6: No-Show Analysis across historical dining dates.
     */
    public ReportResult getNoShowAnalysis() {
        String title = "Historical No-Show Analysis";
        List<String> columns = Arrays.asList("Date", "Total Resolved", "No-Shows", "No-Show %");

        String sql = "SELECT TO_CHAR(reservation_date, 'YYYY-MM-DD') AS res_date, " +
                     "       COUNT(*) AS total_resolved, " +
                     "       SUM(CASE WHEN status = 'NO_SHOW' THEN 1 ELSE 0 END) AS no_shows, " +
                     "       ROUND(100 * SUM(CASE WHEN status = 'NO_SHOW' THEN 1 ELSE 0 END) / COUNT(*), 1) || '%' AS no_show_pct " +
                     "FROM   reservation " +
                     "WHERE  status IN ('NO_SHOW','SEATED','COMPLETED') AND reservation_date < TRUNC(SYSDATE) " +
                     "GROUP  BY reservation_date ORDER BY reservation_date DESC";

        return executeQuery(title, columns, sql, null);
    }

    /**
     * Report 7: Recovered Covers & Revenue Funnel.
     */
    public ReportResult getRecoveredCovers() {
        String title = "Recovered Covers by Booking Source";
        List<String> columns = Arrays.asList("Source Channel", "Parties Seated", "Total Covers");

        String sql = "SELECT source, COUNT(*) AS parties_seated, NVL(SUM(guest_count), 0) AS total_covers " +
                     "FROM   reservation " +
                     "WHERE  status IN ('SEATED','COMPLETED') " +
                     "GROUP  BY source ORDER BY total_covers DESC";

        return executeQuery(title, columns, sql, null);
    }

    /**
     * Report 8: Dining Slot Utilisation over Date Range.
     */
    public ReportResult getSlotUtilisation(LocalDate fromDate, LocalDate toDate) {
        String title = "Slot Utilisation (" + fromDate + " to " + toDate + ")";
        List<String> columns = Arrays.asList("Service Date", "Slot", "Occupied Tables", "Active Tables", "Utilisation %");

        String sql = "SELECT TO_CHAR(d.reservation_date, 'YYYY-MM-DD') AS service_date, " +
                     "       s.slot_label, " +
                     "       COUNT(r.reservation_id) AS occupied_tables, " +
                     "       (SELECT COUNT(*) FROM restaurant_table WHERE status = 'AVAILABLE') AS total_active, " +
                     "       ROUND(100 * COUNT(r.reservation_id) / NULLIF((SELECT COUNT(*) FROM restaurant_table WHERE status = 'AVAILABLE'), 0), 1) || '%' AS utilisation " +
                     "FROM   time_slot s " +
                     "CROSS  JOIN (SELECT DISTINCT reservation_date FROM reservation WHERE reservation_date BETWEEN ? AND ?) d " +
                     "LEFT   JOIN reservation r ON r.slot_id = s.slot_id AND r.reservation_date = d.reservation_date AND r.status IN ('SEATED','COMPLETED') " +
                     "GROUP  BY d.reservation_date, s.slot_id, s.slot_label, s.start_time " +
                     "ORDER  BY d.reservation_date DESC, s.start_time ASC";

        return executeQuery(title, columns, sql, ps -> {
            ps.setDate(1, Date.valueOf(fromDate));
            ps.setDate(2, Date.valueOf(toDate));
        });
    }

    /**
     * Report 9: Waitlist Conversion & Funnel Breakdown.
     */
    public ReportResult getWaitlistFunnel() {
        String title = "Waitlist Conversion Funnel";
        List<String> columns = Arrays.asList("Waitlist Status", "Total Entries", "% of Total");

        String sql = "SELECT status, COUNT(*) AS total_entries, " +
                     "       ROUND(100 * COUNT(*) / NULLIF((SELECT COUNT(*) FROM waitlist_entry), 0), 1) || '%' AS pct_of_total " +
                     "FROM   waitlist_entry " +
                     "GROUP  BY status ORDER BY total_entries DESC";

        return executeQuery(title, columns, sql, null);
    }

    @FunctionalInterface
    private interface ParameterSetter {
        void setParameters(PreparedStatement ps) throws SQLException;
    }

    private ReportResult executeQuery(String title, List<String> columns, String sql, ParameterSetter setter) {
        List<Object[]> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            if (setter != null) {
                setter.setParameters(ps);
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                while (rs.next()) {
                    Object[] row = new Object[colCount];
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rs.getObject(i);
                        if (val instanceof Number && i == 1 && meta.getColumnName(i).equalsIgnoreCase("reservation_id")) {
                            row[i - 1] = "RES" + val;
                        } else {
                            row[i - 1] = val != null ? val : "-";
                        }
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReportDAO.executeQuery");
        }

        return new ReportResult(title, columns, rows);
    }

    /**
     * Aggregates executive KPIs, daily trends, table turn efficiency, CRM loyal guests,
     * tag frequencies, and waitlist funnel over the specified date window.
     */
    public AnalyticsSummary getAnalyticsDashboard(LocalDate fromDate, LocalDate toDate) {
        AnalyticsSummary summary = new AnalyticsSummary(fromDate, toDate);

        try (Connection con = DatabaseConnection.getConnection()) {
            // 1. Total available seats and slot count
            int totalActiveSeats = 0;
            try (PreparedStatement ps = con.prepareStatement("SELECT NVL(SUM(capacity), 0) FROM restaurant_table WHERE status = 'AVAILABLE'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) totalActiveSeats = rs.getInt(1);
                }
            }
            int totalSlots = 5;
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM time_slot")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) totalSlots = Math.max(1, rs.getInt(1));
                }
            }

            long daysCount = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
            if (daysCount <= 0) daysCount = 1;
            int totalPotentialCapacity = (int) (daysCount * totalActiveSeats * totalSlots);

            // 2. Executive Scorecard Aggregations
            String kpiSql = "SELECT " +
                    "  COUNT(*) AS total_bookings, " +
                    "  NVL(SUM(CASE WHEN status IN ('SEATED','COMPLETED') THEN guest_count ELSE 0 END), 0) AS total_covers, " +
                    "  NVL(SUM(CASE WHEN status IN ('SEATED','COMPLETED') THEN 1 ELSE 0 END), 0) AS seated_parties, " +
                    "  NVL(SUM(CASE WHEN status = 'NO_SHOW' THEN 1 ELSE 0 END), 0) AS no_shows, " +
                    "  NVL(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancellations, " +
                    "  NVL(SUM(CASE WHEN source = 'WAITLIST_RECOVERY' AND status IN ('SEATED','COMPLETED') THEN guest_count ELSE 0 END), 0) AS recovered_covers " +
                    "FROM reservation WHERE reservation_date BETWEEN ? AND ?";

            try (PreparedStatement ps = con.prepareStatement(kpiSql)) {
                ps.setDate(1, Date.valueOf(fromDate));
                ps.setDate(2, Date.valueOf(toDate));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int totalBookings = rs.getInt("total_bookings");
                        int covers = rs.getInt("total_covers");
                        int seated = rs.getInt("seated_parties");
                        int noShows = rs.getInt("no_shows");
                        int cancels = rs.getInt("cancellations");
                        int recovered = rs.getInt("recovered_covers");

                        summary.setTotalCovers(covers);
                        summary.setTotalParties(seated);
                        summary.setAvgPartySize(seated > 0 ? Math.round(10.0 * covers / seated) / 10.0 : 0.0);
                        double util = totalPotentialCapacity > 0 ? (100.0 * covers / totalPotentialCapacity) : 0.0;
                        summary.setSeatUtilizationPct(Math.min(100.0, Math.round(10.0 * util) / 10.0));
                        summary.setNoShowCount(noShows);
                        int resolved = seated + noShows + cancels;
                        summary.setNoShowPct(resolved > 0 ? Math.round(1000.0 * noShows / resolved) / 10.0 : 0.0);
                        summary.setCancellationCount(cancels);
                        summary.setCancellationPct(totalBookings > 0 ? Math.round(1000.0 * cancels / totalBookings) / 10.0 : 0.0);
                        summary.setRecoveredCovers(recovered);
                    }
                }
            }

            // 3. Repeat Customer Rate
            String repeatSql = "SELECT COUNT(DISTINCT r.reservation_id) AS repeat_parties " +
                    "FROM reservation r " +
                    "WHERE r.reservation_date BETWEEN ? AND ? " +
                    "  AND r.status IN ('SEATED','COMPLETED') " +
                    "  AND (SELECT COUNT(*) FROM reservation r2 WHERE r2.customer_id = r.customer_id) > 1";
            try (PreparedStatement ps = con.prepareStatement(repeatSql)) {
                ps.setDate(1, Date.valueOf(fromDate));
                ps.setDate(2, Date.valueOf(toDate));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int repeatCount = rs.getInt("repeat_parties");
                        summary.setRepeatCustomerCount(repeatCount);
                        int total = summary.getTotalParties();
                        summary.setRepeatCustomerPct(total > 0 ? Math.round(1000.0 * repeatCount / total) / 10.0 : 0.0);
                    }
                }
            }

            // 4. Daily Covers Trend
            String trendSql = "SELECT reservation_date, " +
                    "  NVL(SUM(guest_count), 0) AS day_covers, " +
                    "  COUNT(*) AS day_bookings " +
                    "FROM reservation " +
                    "WHERE reservation_date BETWEEN ? AND ? AND status IN ('SEATED','COMPLETED') " +
                    "GROUP BY reservation_date ORDER BY reservation_date ASC";
            List<DailyCoverTrend> trends = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(trendSql)) {
                ps.setDate(1, Date.valueOf(fromDate));
                ps.setDate(2, Date.valueOf(toDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate d = rs.getDate("reservation_date").toLocalDate();
                        trends.add(new DailyCoverTrend(d, rs.getInt("day_covers"), rs.getInt("day_bookings")));
                    }
                }
            }
            summary.setDailyTrends(trends);

            // 5. Shift & Slot Load Distribution
            String slotSql = "SELECT s.slot_id, s.slot_label, s.start_time, " +
                    "  COUNT(r.reservation_id) AS total_bookings, " +
                    "  NVL(SUM(r.guest_count), 0) AS total_covers " +
                    "FROM time_slot s " +
                    "LEFT JOIN reservation r ON r.slot_id = s.slot_id " +
                    "  AND r.reservation_date BETWEEN ? AND ? " +
                    "  AND r.status IN ('SEATED','COMPLETED') " +
                    "GROUP BY s.slot_id, s.slot_label, s.start_time " +
                    "ORDER BY s.start_time ASC";
            List<SlotLoadMetric> slotMetrics = new ArrayList<>();
            int maxSlotCapacity = (int) (daysCount * Math.max(1, totalActiveSeats));
            try (PreparedStatement ps = con.prepareStatement(slotSql)) {
                ps.setDate(1, Date.valueOf(fromDate));
                ps.setDate(2, Date.valueOf(toDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int cov = rs.getInt("total_covers");
                        double slotUtil = maxSlotCapacity > 0 ? (100.0 * cov / maxSlotCapacity) : 0.0;
                        slotMetrics.add(new SlotLoadMetric(
                                rs.getInt("slot_id"),
                                rs.getString("slot_label"),
                                rs.getString("start_time"),
                                rs.getInt("total_bookings"),
                                cov,
                                Math.min(100.0, Math.round(10.0 * slotUtil) / 10.0)
                        ));
                    }
                }
            }
            summary.setSlotMetrics(slotMetrics);

            // 6. Table Performance & Seat Efficiency
            String tableSql = "SELECT t.table_id, t.table_number, t.capacity, t.location, " +
                    "  COUNT(r.reservation_id) AS turns, " +
                    "  NVL(SUM(r.guest_count), 0) AS total_covers " +
                    "FROM restaurant_table t " +
                    "LEFT JOIN reservation r ON r.table_id = t.table_id " +
                    "  AND r.reservation_date BETWEEN ? AND ? " +
                    "  AND r.status IN ('SEATED','COMPLETED') " +
                    "WHERE t.status = 'AVAILABLE' " +
                    "GROUP BY t.table_id, t.table_number, t.capacity, t.location " +
                    "ORDER BY total_covers DESC, turns DESC";
            List<TablePerformanceMetric> tableMetrics = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(tableSql)) {
                ps.setDate(1, Date.valueOf(fromDate));
                ps.setDate(2, Date.valueOf(toDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int turns = rs.getInt("turns");
                        int covers = rs.getInt("total_covers");
                        int cap = rs.getInt("capacity");
                        double avgP = turns > 0 ? (1.0 * covers / turns) : 0.0;
                        double eff = (turns > 0 && cap > 0) ? (100.0 * covers / (turns * cap)) : 0.0;
                        tableMetrics.add(new TablePerformanceMetric(
                                rs.getInt("table_id"),
                                rs.getString("table_number"),
                                cap,
                                rs.getString("location"),
                                turns,
                                covers,
                                Math.round(10.0 * avgP) / 10.0,
                                Math.min(100.0, Math.round(10.0 * eff) / 10.0)
                        ));
                    }
                }
            }
            summary.setTableMetrics(tableMetrics);

            // 7. Top Loyal Guests Leaderboard
            String guestSql = "SELECT c.customer_id, c.customer_name, c.phone, c.tags, " +
                    "  COUNT(r.reservation_id) AS visit_count, " +
                    "  NVL(SUM(r.guest_count), 0) AS total_covers, " +
                    "  MAX(r.reservation_date) AS last_visit " +
                    "FROM customer c " +
                    "JOIN reservation r ON r.customer_id = c.customer_id " +
                    "  AND r.reservation_date BETWEEN ? AND ? " +
                    "  AND r.status IN ('SEATED','COMPLETED') " +
                    "GROUP BY c.customer_id, c.customer_name, c.phone, c.tags " +
                    "ORDER BY visit_count DESC, total_covers DESC " +
                    "FETCH FIRST 15 ROWS ONLY";
            List<TopGuestMetric> topGuests = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(guestSql)) {
                ps.setDate(1, Date.valueOf(fromDate));
                ps.setDate(2, Date.valueOf(toDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Date lv = rs.getDate("last_visit");
                        topGuests.add(new TopGuestMetric(
                                rs.getInt("customer_id"),
                                rs.getString("customer_name"),
                                rs.getString("phone"),
                                rs.getString("tags"),
                                rs.getInt("visit_count"),
                                rs.getInt("total_covers"),
                                lv != null ? lv.toLocalDate() : null
                        ));
                    }
                }
            }
            summary.setTopGuests(topGuests);

            // 8. Hospitality & Dietary Tags Distribution
            String tagSql = "SELECT c.tags " +
                    "FROM customer c " +
                    "JOIN reservation r ON r.customer_id = c.customer_id " +
                    "WHERE r.reservation_date BETWEEN ? AND ? " +
                    "  AND r.status IN ('SEATED','COMPLETED') " +
                    "  AND c.tags IS NOT NULL";
            java.util.Map<String, Integer> tagMap = new java.util.HashMap<>();
            try (PreparedStatement ps = con.prepareStatement(tagSql)) {
                ps.setDate(1, Date.valueOf(fromDate));
                ps.setDate(2, Date.valueOf(toDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String raw = rs.getString("tags");
                        if (raw != null) {
                            for (String t : raw.split(",")) {
                                String clean = t.trim();
                                if (!clean.isEmpty()) {
                                    tagMap.put(clean, tagMap.getOrDefault(clean, 0) + 1);
                                }
                            }
                        }
                    }
                }
            }
            List<TagCountMetric> tagList = new ArrayList<>();
            tagMap.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(e -> tagList.add(new TagCountMetric(e.getKey(), e.getValue())));
            summary.setTagDistribution(tagList);

            // 9. Waitlist Conversion Funnel
            String waitSql = "SELECT status, COUNT(*) AS cnt FROM waitlist_entry " +
                    "WHERE TRUNC(service_date) BETWEEN ? AND ? " +
                    "GROUP BY status ORDER BY cnt DESC";
            int totalWait = 0;
            List<WaitlistFunnelStep> funnel = new ArrayList<>();
            java.util.Map<String, Integer> waitCounts = new java.util.LinkedHashMap<>();
            try (PreparedStatement ps = con.prepareStatement(waitSql)) {
                ps.setDate(1, Date.valueOf(fromDate));
                ps.setDate(2, Date.valueOf(toDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String st = rs.getString("status");
                        int c = rs.getInt("cnt");
                        waitCounts.put(st, c);
                        totalWait += c;
                    }
                }
            }
            for (java.util.Map.Entry<String, Integer> e : waitCounts.entrySet()) {
                double pct = totalWait > 0 ? (100.0 * e.getValue() / totalWait) : 0.0;
                funnel.add(new WaitlistFunnelStep(e.getKey(), e.getValue(), Math.round(10.0 * pct) / 10.0));
            }
            summary.setWaitlistFunnel(funnel);

            // 10. Auto-Generated Key Insights
            List<String> takeaways = new ArrayList<>();
            takeaways.add(String.format("Total of %d covers served across %d parties (average %.1f guests/party).",
                    summary.getTotalCovers(), summary.getTotalParties(), summary.getAvgPartySize()));

            SlotLoadMetric peakSlot = slotMetrics.stream()
                    .max(java.util.Comparator.comparingInt(SlotLoadMetric::getTotalCovers))
                    .orElse(null);
            if (peakSlot != null && peakSlot.getTotalCovers() > 0) {
                takeaways.add(String.format("Peak dining slot: %s (%s) with %d covers (%.1f%% slot utilization).",
                        peakSlot.getSlotLabel(), peakSlot.getStartTime(), peakSlot.getTotalCovers(), peakSlot.getCapacityUtilisationPct()));
            }

            SlotLoadMetric quietSlot = slotMetrics.stream()
                    .min(java.util.Comparator.comparingInt(SlotLoadMetric::getTotalCovers))
                    .orElse(null);
            if (quietSlot != null && peakSlot != null && quietSlot.getSlotId() != peakSlot.getSlotId()) {
                takeaways.add(String.format("Off-peak slot with available capacity: %s (%d covers). Eligible for promotion.",
                        quietSlot.getSlotLabel(), quietSlot.getTotalCovers()));
            }

            if (summary.getTotalParties() > 0) {
                takeaways.add(String.format("Guest retention: %.1f%% of diners are repeat customers (%d regular parties).",
                        summary.getRepeatCustomerPct(), summary.getRepeatCustomerCount()));
            }

            if (summary.getNoShowPct() > 5.0) {
                takeaways.add(String.format("⚠️ Elevated no-show rate of %.1f%% (%d absent parties). SMS reconfirmation recommended.",
                        summary.getNoShowPct(), summary.getNoShowCount()));
            } else {
                takeaways.add(String.format("✓ Healthy no-show rate of %.1f%% (%d lost parties) within safe operational threshold.",
                        summary.getNoShowPct(), summary.getNoShowCount()));
            }

            if (summary.getRecoveredCovers() > 0) {
                takeaways.add(String.format("✓ Recovered %d covers from waitlist and rapid walk-in slot filling.",
                        summary.getRecoveredCovers()));
            }

            summary.setKeyTakeaways(takeaways);

        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReportDAO.getAnalyticsDashboard");
        }

        return summary;
    }
}
