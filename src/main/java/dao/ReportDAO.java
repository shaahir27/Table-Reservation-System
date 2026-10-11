package dao;

import db.DatabaseConnection;
import util.OracleErrorMapper;

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
}
