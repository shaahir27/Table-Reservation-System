package dao;

import db.DatabaseConnection;
import model.ReconfirmStatus;
import model.Reservation;
import model.ReservationDetail;
import model.ReservationSource;
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
 * Data Access Object for Reservation records (P3 Lifecycle & Service Board).
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
                     "  reservation_id, customer_id, table_id, reservation_date, slot_id, guest_count, status, created_by, " +
                     "  source, seated_at, hold_expires_at, reconfirm_status" +
                     ") VALUES (reservation_seq.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = con.prepareStatement(sql, new String[]{"RESERVATION_ID"})) {
            ps.setInt(1, reservation.getCustomerId());
            ps.setInt(2, reservation.getTableId());
            ps.setDate(3, Date.valueOf(reservation.getReservationDate()));
            ps.setInt(4, reservation.getSlotId());
            ps.setInt(5, reservation.getGuestCount());
            ps.setString(6, reservation.getStatus() != null ? reservation.getStatus().name() : "CONFIRMED");
            if (reservation.getCreatedBy() != null && reservation.getCreatedBy() > 0) {
                ps.setInt(7, reservation.getCreatedBy());
            } else {
                ps.setNull(7, java.sql.Types.NUMERIC);
            }
            ps.setString(8, reservation.getSource() != null ? reservation.getSource().name() : "BOOKING");
            if (reservation.getSeatedAt() != null) {
                ps.setTimestamp(9, Timestamp.valueOf(reservation.getSeatedAt()));
            } else {
                ps.setNull(9, java.sql.Types.TIMESTAMP);
            }
            if (reservation.getHoldExpiresAt() != null) {
                ps.setTimestamp(10, Timestamp.valueOf(reservation.getHoldExpiresAt()));
            } else {
                ps.setNull(10, java.sql.Types.TIMESTAMP);
            }
            ps.setString(11, reservation.getReconfirmStatus() != null ? reservation.getReconfirmStatus().name() : "PENDING");

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
                     "       guest_count, status, created_at, created_by, source, seated_at, hold_expires_at, reconfirm_status " +
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
     * Checks if an active (HELD, CONFIRMED, SEATED, COMPLETED) reservation exists for table, date, and slot.
     */
    public boolean isSlotTaken(Connection con, int tableId, LocalDate date, int slotId) throws SQLException {
        String sql = "SELECT 1 FROM reservation " +
                     "WHERE table_id = ? " +
                     "  AND reservation_date = ? " +
                     "  AND slot_id = ? " +
                     "  AND status IN " + SqlFragments.ACTIVE_BOOKING_STATUSES_IN;

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
     * Transitions a CONFIRMED or HELD reservation to SEATED.
     */
    public boolean seat(Connection con, int reservationId, LocalDateTime seatedAt) throws SQLException {
        String sql = "UPDATE reservation SET status = 'SEATED', seated_at = ? " +
                     "WHERE reservation_id = ? AND status IN ('CONFIRMED', 'HELD')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(seatedAt != null ? seatedAt : util.DbClock.localDateTime()));
            ps.setInt(2, reservationId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Transitions a CONFIRMED reservation to NO_SHOW.
     */
    public boolean markNoShow(Connection con, int reservationId) throws SQLException {
        String sql = "UPDATE reservation SET status = 'NO_SHOW' " +
                     "WHERE reservation_id = ? AND status = 'CONFIRMED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Transitions a SEATED reservation to COMPLETED.
     */
    public boolean markCompleted(Connection con, int reservationId) throws SQLException {
        String sql = "UPDATE reservation SET status = 'COMPLETED' " +
                     "WHERE reservation_id = ? AND status = 'SEATED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean markCompleted(int reservationId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return markCompleted(con, reservationId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.markCompleted");
        }
    }

    /**
     * Cancels a reservation. Only allowed from CONFIRMED or HELD.
     */
    public boolean cancelReservation(Connection con, int reservationId) throws SQLException {
        String sql = "UPDATE reservation SET status = 'CANCELLED' " +
                     "WHERE reservation_id = ? AND status IN ('CONFIRMED', 'HELD')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean cancelReservation(int reservationId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return cancelReservation(con, reservationId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.cancelReservation");
        }
    }

    /**
     * Updates reconfirmation status.
     */
    public boolean updateReconfirmStatus(Connection con, int reservationId, ReconfirmStatus status) throws SQLException {
        String sql = "UPDATE reservation SET reconfirm_status = ? WHERE reservation_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, reservationId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Race-safe atomic acceptance of a HELD waitlist offer.
     * Returns true if successfully transitioned before hold expiry, false if expired.
     */
    public boolean acceptHeldOffer(Connection con, int reservationId, ReservationStatus targetStatus, LocalDateTime now) throws SQLException {
        String sql = "UPDATE reservation " +
                     "   SET status = ?, hold_expires_at = NULL, " +
                     "       seated_at = CASE WHEN ? = 'SEATED' THEN ? ELSE NULL END " +
                     " WHERE reservation_id = ? " +
                     "   AND status = 'HELD' " +
                     "   AND hold_expires_at > ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, targetStatus.name());
            ps.setString(2, targetStatus.name());
            ps.setTimestamp(3, Timestamp.valueOf(now));
            ps.setInt(4, reservationId);
            ps.setTimestamp(5, Timestamp.valueOf(now));
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Cancels expired HELD reservations.
     */
    public int cancelExpiredHolds(Connection con, LocalDateTime now) throws SQLException {
        String sql = "UPDATE reservation SET status = 'CANCELLED' " +
                     "WHERE status = 'HELD' AND hold_expires_at <= ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(now));
            return ps.executeUpdate();
        }
    }

    /**
     * Finds all reservations for a given date (Service Board).
     */
    public List<ReservationDetail> getReservationsByDate(LocalDate date) {
        String sql = "SELECT r.reservation_id, c.customer_name, c.phone, t.table_number, r.reservation_date, " +
                     "       s.slot_label, r.guest_count, r.status, r.created_at, " +
                     "       r.source, r.seated_at, r.hold_expires_at, r.reconfirm_status " +
                     "FROM   reservation r " +
                     "JOIN   customer c         ON c.customer_id = r.customer_id " +
                     "JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                     "JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                     "WHERE  r.reservation_date = ? " +
                     "ORDER  BY s.start_time ASC, t.table_number ASC";

        List<ReservationDetail> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapDetailRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getReservationsByDate");
        }
    }

    /**
     * Retrieves all reservations for a specific customer (Customer 360 History).
     */
    public List<ReservationDetail> getReservationsByCustomerId(int customerId) {
        String sql = "SELECT r.reservation_id, c.customer_name, c.phone, t.table_number, r.reservation_date, " +
                     "       s.slot_label, r.guest_count, r.status, r.created_at, " +
                     "       r.source, r.seated_at, r.hold_expires_at, r.reconfirm_status " +
                     "FROM   reservation r " +
                     "JOIN   customer c         ON c.customer_id = r.customer_id " +
                     "JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                     "JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                     "WHERE  r.customer_id = ? " +
                     "ORDER  BY r.reservation_date DESC, s.start_time DESC";

        List<ReservationDetail> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapDetailRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getReservationsByCustomerId");
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
                "       s.slot_label, r.guest_count, r.status, r.created_at, " +
                "       r.source, r.seated_at, r.hold_expires_at, r.reconfirm_status " +
                "FROM   reservation r " +
                "JOIN   customer c         ON c.customer_id = r.customer_id " +
                "JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                "JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                "WHERE  1=1 "
        );

        List<Object> params = new ArrayList<>();

        if (filter != null) {
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

            if (filter.getDate() != null) {
                sql.append("AND r.reservation_date = ? ");
                params.add(Date.valueOf(filter.getDate()));
            }

            if (filter.getStatus() != null) {
                sql.append("AND r.status = ? ");
                params.add(filter.getStatus().name());
            }

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

    public record DashboardCounts(int todayCount, int upcomingCount, int cancelledCount, int recoveredCount) {}

    /**
     * Aggregated single-query dashboard metrics. Consolidates 4 separate count queries
     * into a single database roundtrip on Oracle XE for instant dashboard rendering.
     */
    public DashboardCounts getDashboardCounts() {
        String sql = "SELECT " +
                     "(SELECT COUNT(*) FROM reservation WHERE reservation_date = TRUNC(SYSDATE) AND status IN " + SqlFragments.ACTIVE_BOOKING_STATUSES_IN + "), " +
                     "(SELECT COUNT(*) FROM reservation r JOIN time_slot s ON s.slot_id = r.slot_id WHERE r.status IN ('CONFIRMED', 'HELD') AND (r.reservation_date > TRUNC(SYSDATE) OR (r.reservation_date = TRUNC(SYSDATE) AND s.end_time > TO_CHAR(SYSDATE, 'HH24:MI')))), " +
                     "(SELECT COUNT(*) FROM reservation WHERE status = 'CANCELLED' AND reservation_date >= TRUNC(SYSDATE)), " +
                     "(SELECT NVL(SUM(guest_count), 0) FROM reservation WHERE source = 'WAITLIST' AND status IN ('SEATED', 'COMPLETED')) " +
                     "FROM DUAL";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new DashboardCounts(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
            }
            return new DashboardCounts(0, 0, 0, 0);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getDashboardCounts");
        }
    }

    /**
     * Dashboard metric: count of active reservations today.
     */
    public int getTodayCount() {
        String sql = "SELECT COUNT(*) FROM reservation " +
                     "WHERE reservation_date = TRUNC(SYSDATE) AND status IN " + SqlFragments.ACTIVE_BOOKING_STATUSES_IN;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getTodayCount");
        }
    }

    /**
     * Dashboard metric: count of future confirmed/held reservations.
     */
    public int getUpcomingCount() {
        String sql = "SELECT COUNT(*) FROM reservation r " +
                     "JOIN time_slot s ON s.slot_id = r.slot_id " +
                     "WHERE r.status IN ('CONFIRMED', 'HELD') " +
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
     * Headline KPI: Recovered covers through the waitlist recovery loop.
     */
    public int getRecoveredCoversCount() {
        String sql = "SELECT NVL(SUM(guest_count), 0) FROM reservation " +
                     "WHERE source = 'WAITLIST' AND status IN ('SEATED', 'COMPLETED')";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getRecoveredCoversCount");
        }
    }

    /**
     * Dashboard feed: top N upcoming confirmed reservations.
     */
    public List<ReservationDetail> getUpcomingReservations(int limit) {
        String sql = "SELECT r.reservation_id, c.customer_name, c.phone, t.table_number, r.reservation_date, " +
                     "       s.slot_label, r.guest_count, r.status, r.created_at, " +
                     "       r.source, r.seated_at, r.hold_expires_at, r.reconfirm_status " +
                     "FROM   reservation r " +
                     "JOIN   customer c         ON c.customer_id = r.customer_id " +
                     "JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                     "JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                     "WHERE  r.status IN ('CONFIRMED', 'HELD') " +
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
            if (e.getErrorCode() == 933 || (e.getMessage() != null && e.getMessage().contains("ORA-00933"))) {
                return getUpcomingReservationsFallback(limit);
            }
            throw OracleErrorMapper.map(e, "ReservationDAO.getUpcomingReservations");
        }
        return list;
    }

    private List<ReservationDetail> getUpcomingReservationsFallback(int limit) {
        String sql = "SELECT * FROM ( " +
                     "  SELECT r.reservation_id, c.customer_name, c.phone, t.table_number, r.reservation_date, " +
                     "         s.slot_label, r.guest_count, r.status, r.created_at, " +
                     "         r.source, r.seated_at, r.hold_expires_at, r.reconfirm_status " +
                     "  FROM   reservation r " +
                     "  JOIN   customer c         ON c.customer_id = r.customer_id " +
                     "  JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                     "  JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                     "  WHERE  r.status IN ('CONFIRMED', 'HELD') " +
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
                     "       s.slot_label, r.guest_count, r.status, r.created_at, " +
                     "       r.source, r.seated_at, r.hold_expires_at, r.reconfirm_status " +
                     "FROM   reservation r " +
                     "JOIN   customer c         ON c.customer_id = r.customer_id " +
                     "JOIN   restaurant_table t ON t.table_id    = r.table_id " +
                     "JOIN   time_slot s        ON s.slot_id     = r.slot_id " +
                     "WHERE  r.reservation_date = ? " +
                     "  AND  r.slot_id = ? " +
                     "  AND  r.table_id = ? " +
                     "  AND  r.status IN " + SqlFragments.ACTIVE_BOOKING_STATUSES_IN;

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
        LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : util.DbClock.localDateTime();

        Integer createdBy = null;
        try {
            int cb = rs.getInt("created_by");
            if (!rs.wasNull()) {
                createdBy = cb;
            }
        } catch (SQLException ignored) {
        }

        ReservationSource source = ReservationSource.fromString(rs.getString("source"));
        Timestamp seatedTs = rs.getTimestamp("seated_at");
        LocalDateTime seatedAt = seatedTs != null ? seatedTs.toLocalDateTime() : null;
        Timestamp holdTs = rs.getTimestamp("hold_expires_at");
        LocalDateTime holdExpiresAt = holdTs != null ? holdTs.toLocalDateTime() : null;
        ReconfirmStatus reconfirmStatus = ReconfirmStatus.fromString(rs.getString("reconfirm_status"));

        return new Reservation(
                rs.getInt("reservation_id"),
                rs.getInt("customer_id"),
                rs.getInt("table_id"),
                rs.getDate("reservation_date").toLocalDate(),
                rs.getInt("slot_id"),
                rs.getInt("guest_count"),
                ReservationStatus.valueOf(rs.getString("status")),
                createdAt,
                createdBy,
                source,
                seatedAt,
                holdExpiresAt,
                reconfirmStatus
        );
    }

    private ReservationDetail mapDetailRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : util.DbClock.localDateTime();
        int resId = rs.getInt("reservation_id");

        ReservationSource source = ReservationSource.fromString(rs.getString("source"));
        Timestamp seatedTs = rs.getTimestamp("seated_at");
        LocalDateTime seatedAt = seatedTs != null ? seatedTs.toLocalDateTime() : null;
        Timestamp holdTs = rs.getTimestamp("hold_expires_at");
        LocalDateTime holdExpiresAt = holdTs != null ? holdTs.toLocalDateTime() : null;
        ReconfirmStatus reconfirmStatus = ReconfirmStatus.fromString(rs.getString("reconfirm_status"));

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
                createdAt,
                source,
                seatedAt,
                holdExpiresAt,
                reconfirmStatus
        );
    }

    /**
     * Finds future active reservations on a table (for D22 maintenance / inactive protection).
     */
    public List<ReservationDetail> getFutureBlockingReservationsForTable(int tableId, LocalDate fromDate) {
        String sql = "SELECT r.reservation_id, r.customer_id, r.table_id, r.reservation_date, r.slot_id, r.guest_count, " +
                     "       r.status, r.created_at, r.created_by, r.source, r.seated_at, r.hold_expires_at, r.reconfirm_status, " +
                     "       c.full_name AS customer_name, c.phone_number AS phone, " +
                     "       t.table_number, t.capacity, s.start_time, s.end_time, s.slot_label " +
                     "  FROM reservation r " +
                     "  JOIN customer c ON r.customer_id = c.customer_id " +
                     "  JOIN restaurant_table t ON r.table_id = t.table_id " +
                     "  JOIN time_slot s ON r.slot_id = s.slot_id " +
                     " WHERE r.table_id = ? AND r.reservation_date >= ? " +
                     "   AND r.status IN ('HELD', 'CONFIRMED', 'SEATED') " +
                     " ORDER BY r.reservation_date, s.start_time";

        List<ReservationDetail> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            ps.setDate(2, Date.valueOf(fromDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapDetailRow(rs));
                }
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "ReservationDAO.getFutureBlockingReservationsForTable");
        }
        return list;
    }
}
