package dao;

import db.DatabaseConnection;
import model.AvailabilityCell;
import model.AvailabilityGrid;
import model.TimeSlot;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for high-performance One-Glance Availability Matrix queries (Section 6).
 * Computes an entire 7-day multi-slot booking availability grid in a single database round-trip.
 */
public class AvailabilityDAO {

    public AvailabilityGrid getAvailabilityGrid(LocalDate startDate, int days, int guests, String section) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return getAvailabilityGrid(con, startDate, days, guests, section);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "AvailabilityDAO.getAvailabilityGrid");
        }
    }

    public AvailabilityGrid getAvailabilityGrid(Connection con, LocalDate startDate, int days, int guests, String section) throws SQLException {
        // 1. Calculate total tables fitting the party size and optional section
        int totalFitting = 0;
        String countSql = "SELECT COUNT(*) FROM restaurant_table WHERE status = 'AVAILABLE' AND capacity >= ? AND (? IS NULL OR location = ?)";
        try (PreparedStatement ps = con.prepareStatement(countSql)) {
            ps.setInt(1, guests);
            if (section != null && !section.equalsIgnoreCase("Any") && !section.isBlank()) {
                ps.setString(2, section);
                ps.setString(3, section);
            } else {
                ps.setNull(2, java.sql.Types.VARCHAR);
                ps.setNull(3, java.sql.Types.VARCHAR);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalFitting = rs.getInt(1);
                }
            }
        }

        // 2. Fetch the entire matrix in one round-trip
        String matrixSql = """
            SELECT d.service_date, s.slot_id, s.slot_label, s.start_time, s.end_time,
                   (SELECT COUNT(*) FROM restaurant_table t
                     WHERE t.status = 'AVAILABLE' AND t.capacity >= ?
                       AND (? IS NULL OR t.location = ?)
                       AND NOT EXISTS (SELECT 1 FROM reservation r
                                        WHERE r.table_id = t.table_id AND r.reservation_date = d.service_date
                                          AND r.slot_id = s.slot_id
                                          AND r.status IN %s)) AS free_tables,
                   (SELECT COUNT(*) FROM waitlist_entry w
                     WHERE w.service_date = d.service_date AND w.status = 'WAITING'
                       AND (w.slot_id = s.slot_id OR w.slot_id IS NULL)) AS waiting
              FROM (SELECT ? + LEVEL - 1 AS service_date FROM dual CONNECT BY LEVEL <= ?) d
             CROSS JOIN time_slot s
             ORDER BY d.service_date, s.start_time
            """.formatted(SqlFragments.ACTIVE_BOOKING_STATUSES_IN);

        List<LocalDate> dateList = new ArrayList<>();
        Map<Integer, TimeSlot> slotMap = new LinkedHashMap<>();
        Map<String, AvailabilityCell> cellMap = new HashMap<>();

        try (PreparedStatement ps = con.prepareStatement(matrixSql)) {
            ps.setInt(1, guests);
            if (section != null && !section.equalsIgnoreCase("Any") && !section.isBlank()) {
                ps.setString(2, section);
                ps.setString(3, section);
            } else {
                ps.setNull(2, java.sql.Types.VARCHAR);
                ps.setNull(3, java.sql.Types.VARCHAR);
            }
            ps.setDate(4, Date.valueOf(startDate));
            ps.setInt(5, days);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate d = rs.getDate("service_date").toLocalDate();
                    if (!dateList.contains(d)) {
                        dateList.add(d);
                    }
                    int slotId = rs.getInt("slot_id");
                    String slotLabel = rs.getString("slot_label");
                    java.sql.Time st = rs.getTime("start_time");
                    java.sql.Time et = rs.getTime("end_time");

                    if (!slotMap.containsKey(slotId)) {
                        slotMap.put(slotId, new TimeSlot(slotId, slotLabel,
                                st != null ? st.toLocalTime() : null,
                                et != null ? et.toLocalTime() : null));
                    }

                    int freeTables = rs.getInt("free_tables");
                    int waiting = rs.getInt("waiting");

                    AvailabilityCell cell = new AvailabilityCell(d, slotId, slotLabel, freeTables, totalFitting, waiting);
                    cellMap.put(d.toString() + "_" + slotId, cell);
                }
            }
        }

        return new AvailabilityGrid(dateList, new ArrayList<>(slotMap.values()), guests, section, totalFitting, cellMap);
    }
}
