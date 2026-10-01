package dao;

import db.DatabaseConnection;
import model.Reservation;
import model.ReservationDetail;
import model.ReservationStatus;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Reservation records.
 * Strictly uses PreparedStatements and bound parameters.
 */
public class ReservationDAO {

    public Reservation createReservation(Reservation reservation) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return createReservation(con, reservation);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.createReservation");
        }
    }

    public Reservation createReservation(Connection con, Reservation reservation) throws SQLException {
        String sql = "INSERT INTO reservation (" +
                     "  reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status" +
                     ") VALUES (reservation_seq.NEXTVAL, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = con.prepareStatement(sql, new String[]{"RESERVATION_ID"})) {
            ps.setInt(1, reservation.getCustomerId());
            ps.setInt(2, reservation.getTableId());
            ps.setDate(3, Date.valueOf(reservation.getReservationDate()));
            ps.setInt(4, reservation.getSlotId());
            ps.setInt(5, reservation.getGuestCount());
            ps.setString(6, reservation.getStatus() != null ? reservation.getStatus().name() : "CONFIRMED");
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs != null && rs.next()) {
                    reservation.setReservationId(rs.getInt(1));
                }
            }
        }
        return reservation;
    }

    public Optional<Reservation> getReservationById(int reservationId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return getReservationById(con, reservationId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getReservationById");
        }
    }

    public Optional<Reservation> getReservationById(Connection con, int reservationId) throws SQLException {
        String sql = "SELECT reservation_id, customer_id, table_id, reservation_date, slot_id, " +
                     "       guest_count, status, created_at " +
                     "FROM   reservation " +
                     "WHERE  reservation_id = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if an active (CONFIRMED or COMPLETED) reservation exists for table, date, and slot.
     */
    public boolean isSlotTaken(Connection con, int tableId, LocalDate date, int slotId) throws SQLException {
        String sql = "SELECT 1 FROM reservation " +
                     "WHERE table_id = ? " +
                     "  AND reservation_date = ? " +
                     "  AND slot_id = ? " +
                     "  AND status IN ('CONFIRMED', 'COMPLETED')";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            ps.setDate(2, Date.valueOf(date));
            ps.setInt(3, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean isSlotTaken(int tableId, LocalDate date, int slotId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return isSlotTaken(con, tableId, date, slotId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.isSlotTaken");
        }
    }

    /**
     * Cancels a reservation. Only updates if current status is CONFIRMED.
     * Returns true if successfully updated, false if not found or not in CONFIRMED state.
     */
    public boolean cancelReservation(int reservationId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return cancelReservation(con, reservationId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.cancelReservation");
        }
    }

    public boolean cancelReservation(Connection con, int reservationId) throws SQLException {
        String sql = "UPDATE reservation SET status = 'CANCELLED' " +
                     "WHERE reservation_id = ? AND status = 'CONFIRMED'";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            int rows = ps.executeUpdate();
            return rows > 0;
        }
    }

    /**
     * Marks a reservation as COMPLETED. Only updates if current status is CONFIRMED.
     */
    public boolean markCompleted(int reservationId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return markCompleted(con, reservationId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.markCompleted");
        }
    }

    public boolean markCompleted(Connection con, int reservationId) throws SQLException {
        String sql = "UPDATE reservation SET status = 'COMPLETED' " +
                     "WHERE reservation_id = ? AND status = 'CONFIRMED'";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            int rows = ps.executeUpdate();
            return rows > 0;
        }
    }

    public void updateReservationStatus(int reservationId, ReservationStatus status) {
        try (Connection con = DatabaseConnection.getConnection()) {
            String sql = "UPDATE reservation SET status = ? WHERE reservation_id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, status.name());
                ps.setInt(2, reservationId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.updateReservationStatus");
        }
    }

    /**
     * Executes dynamic joined search using fixed SQL fragments and strictly bound parameters.
     */
    public List<ReservationDetail> searchReservations(model.ReservationFilter filter) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return searchReservations(con, filter);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.searchReservations");
        }
    }

    public List<ReservationDetail> searchReservations(Connection con, model.ReservationFilter filter) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT r.reservation_id, c.customer_name, c.phone, t.table_number, r.reservation_date, " +
                "       s.slot_label, r.guest_count, r.status, r.created_at " +
                "FROM   reservation r " +
                "JOIN   customer c         ON c.customer_id = r.customer_id " +
                "JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                "JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                "WHERE  1=1 "
        );

        List<Object> params = new ArrayList<>();

        if (filter != null) {
            // Search text (matches numeric ID, RES prefix, customer name, phone, or table number)
            if (filter.getSearchText() != null && !filter.getSearchText().trim().isEmpty()) {
                String raw = filter.getSearchText().trim();
                String cleanedId = raw.toUpperCase().startsWith("RES") ? raw.substring(3).trim() : raw;
                boolean isNumeric = cleanedId.matches("\\d+");

                if (isNumeric) {
                    sql.append("AND (r.reservation_id = ? OR UPPER(c.customer_name) LIKE UPPER(?) OR c.phone LIKE ? OR UPPER(t.table_number) = UPPER(?)) ");
                    params.add(Integer.parseInt(cleanedId));
                    params.add("%" + raw + "%");
                    params.add("%" + raw + "%");
                    params.add(raw);
                } else {
                    sql.append("AND (UPPER(c.customer_name) LIKE UPPER(?) OR c.phone LIKE ? OR UPPER(t.table_number) = UPPER(?)) ");
                    params.add("%" + raw + "%");
                    params.add("%" + raw + "%");
                    params.add(raw);
                }
            }

            // Date filter
            if (filter.getDate() != null) {
                sql.append("AND r.reservation_date = ? ");
                params.add(Date.valueOf(filter.getDate()));
            }

            // Status filter
            if (filter.getStatus() != null) {
                sql.append("AND r.status = ? ");
                params.add(filter.getStatus().name());
            }

            // Table filter
            if (filter.getTableNumber() != null && !filter.getTableNumber().trim().isEmpty() && !"ALL".equalsIgnoreCase(filter.getTableNumber())) {
                sql.append("AND t.table_number = ? ");
                params.add(filter.getTableNumber().trim());
            }
        }

        sql.append("ORDER BY r.reservation_date DESC, s.start_time DESC");

        List<ReservationDetail> results = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapDetailRow(rs));
                }
            }
        }
        return results;
    }

    public List<ReservationDetail> getAllReservations() {
        return searchReservations(null);
    }

    /**
     * Dashboard metric: count of confirmed/completed reservations today.
     */
    public int getTodayCount() {
        String sql = "SELECT COUNT(*) FROM reservation " +
                     "WHERE reservation_date = TRUNC(SYSDATE) AND status IN ('CONFIRMED','COMPLETED')";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getTodayCount");
        }
    }

    /**
     * Dashboard metric: count of future confirmed reservations.
     */
    public int getUpcomingCount() {
        String sql = "SELECT COUNT(*) FROM reservation r " +
                     "JOIN time_slot s ON s.slot_id = r.slot_id " +
                     "WHERE r.status = 'CONFIRMED' " +
                     "  AND (r.reservation_date > TRUNC(SYSDATE) " +
                     "       OR (r.reservation_date = TRUNC(SYSDATE) AND s.end_time > TO_CHAR(SYSDATE, 'HH24:MI')))";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getUpcomingCount");
        }
    }

    /**
     * Dashboard metric: count of cancelled reservations from today onward.
     */
    public int getCancelledCount() {
        String sql = "SELECT COUNT(*) FROM reservation " +
                     "WHERE status = 'CANCELLED' AND reservation_date >= TRUNC(SYSDATE)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getCancelledCount");
        }
    }

    /**
     * Dashboard feed: top N upcoming confirmed reservations.
     */
    public List<ReservationDetail> getUpcomingReservations(int limit) {
        String sql = "SELECT r.reservation_id, c.customer_name, c.phone, t.table_number, r.reservation_date, " +
                     "       s.slot_label, r.guest_count, r.status, r.created_at " +
                     "FROM   reservation r " +
                     "JOIN   customer c         ON c.customer_id = r.customer_id " +
                     "JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                     "JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                     "WHERE  r.status = 'CONFIRMED' " +
                     "  AND (r.reservation_date > TRUNC(SYSDATE) " +
                     "       OR (r.reservation_date = TRUNC(SYSDATE) AND s.end_time > TO_CHAR(SYSDATE, 'HH24:MI'))) " +
                     "ORDER  BY r.reservation_date ASC, s.start_time ASC " +
                     "FETCH FIRST ? ROWS ONLY";

        List<ReservationDetail> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapDetailRow(rs));
                }
            }
        } catch (SQLException e) {
            // Fallback for Oracle 11g which doesn't support FETCH FIRST
            return getUpcomingReservationsFallback(limit);
        }
        return list;
    }

    private List<ReservationDetail> getUpcomingReservationsFallback(int limit) {
        String sql = "SELECT * FROM ( " +
                     "  SELECT r.reservation_id, c.customer_name, c.phone, t.table_number, r.reservation_date, " +
                     "         s.slot_label, r.guest_count, r.status, r.created_at " +
                     "  FROM   reservation r " +
                     "  JOIN   customer c         ON c.customer_id = r.customer_id " +
                     "  JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                     "  JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                     "  WHERE  r.status = 'CONFIRMED' " +
                     "    AND (r.reservation_date > TRUNC(SYSDATE) " +
                     "         OR (r.reservation_date = TRUNC(SYSDATE) AND s.end_time > TO_CHAR(SYSDATE, 'HH24:MI'))) " +
                     "  ORDER  BY r.reservation_date ASC, s.start_time ASC " +
                     ") WHERE ROWNUM <= ?";

        List<ReservationDetail> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapDetailRow(rs));
                }
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getUpcomingReservationsFallback");
        }
        return list;
    }

    /**
     * Retrieves reservation details for a specific booked slot (used by Floor view popup).
     */
    public Optional<ReservationDetail> getReservationForSlot(LocalDate date, int slotId, int tableId) {
        String sql = "SELECT r.reservation_id, c.customer_name, c.phone, t.table_number, r.reservation_date, " +
                     "       s.slot_label, r.guest_count, r.status, r.created_at " +
                     "FROM   reservation r " +
                     "JOIN   customer c         ON c.customer_id = r.customer_id " +
                     "JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                     "JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                     "WHERE  r.reservation_date = ? " +
                     "  AND  r.slot_id = ? " +
                     "  AND  r.table_id = ? " +
                     "  AND  r.status IN ('CONFIRMED', 'COMPLETED')";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ps.setInt(2, slotId);
            ps.setInt(3, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapDetailRow(rs));
                }
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getReservationForSlot");
        }
        return Optional.empty();
    }

    private Reservation mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();

        return new Reservation(
                rs.getInt("reservation_id"),
                rs.getInt("customer_id"),
                rs.getInt("table_id"),
                rs.getDate("reservation_date").toLocalDate(),
                rs.getInt("slot_id"),
                rs.getInt("guest_count"),
                ReservationStatus.valueOf(rs.getString("status")),
                createdAt
        );
    }

    private ReservationDetail mapDetailRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
        int resId = rs.getInt("reservation_id");

        return new ReservationDetail(
                resId,
                "RES" + resId,
                rs.getString("customer_name"),
                rs.getString("phone"),
                rs.getString("table_number"),
                rs.getDate("reservation_date").toLocalDate(),
                rs.getString("slot_label"),
                rs.getInt("guest_count"),
                ReservationStatus.valueOf(rs.getString("status")),
                createdAt
        );
    }
}

