package dao;

import db.DatabaseConnection;
import model.TimeSlot;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for TimeSlot entities.
 */
public class TimeSlotDAO {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public List<TimeSlot> getAllSlots() {
        try (Connection con = DatabaseConnection.getConnection()) {
            return getAllSlots(con);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "TimeSlotDAO.getAllSlots");
        }
    }

    public List<TimeSlot> getAllSlots(Connection con) throws SQLException {
        String sql = "SELECT slot_id, slot_label, start_time, end_time FROM time_slot ORDER BY start_time ASC";
        List<TimeSlot> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new TimeSlot(
                        rs.getInt("slot_id"),
                        rs.getString("slot_label"),
                        LocalTime.parse(rs.getString("start_time"), TIME_FMT),
                        LocalTime.parse(rs.getString("end_time"), TIME_FMT)
                ));
            }
        }
        return list;
    }
}
