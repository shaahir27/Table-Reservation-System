package dao;

import db.DatabaseConnection;
import model.WaitlistEntry;
import model.WaitlistStatus;
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
 * Data Access Object for waitlist_entry records (P4 Slot Recovery Loop).
 */
public class WaitlistDAO {

    public WaitlistEntry insert(WaitlistEntry entry) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return insert(con, entry);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "WaitlistDAO.insert");
        }
    }

    public WaitlistEntry insert(Connection con, WaitlistEntry entry) throws SQLException {
        String sql = "INSERT INTO waitlist_entry (" +
                     "  entry_id, customer_id, service_date, slot_id, party_size, pref_location, " +
                     "  status, offered_reservation_id, created_by, created_at, note" +
                     ") VALUES (waitlist_seq.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP, ?)";

        try (PreparedStatement ps = con.prepareStatement(sql, new String[]{"ENTRY_ID"})) {
            ps.setInt(1, entry.getCustomerId());
            ps.setDate(2, Date.valueOf(entry.getServiceDate()));
            if (entry.getSlotId() != null && entry.getSlotId() > 0) {
                ps.setInt(3, entry.getSlotId());
            } else {
                ps.setNull(3, java.sql.Types.NUMERIC);
            }
            ps.setInt(4, entry.getPartySize());
            ps.setString(5, entry.getPrefLocation());
            ps.setString(6, entry.getStatus() != null ? entry.getStatus().name() : "WAITING");
            if (entry.getOfferedReservationId() != null && entry.getOfferedReservationId() > 0) {
                ps.setInt(7, entry.getOfferedReservationId());
            } else {
                ps.setNull(7, java.sql.Types.NUMERIC);
            }
            if (entry.getCreatedBy() != null && entry.getCreatedBy() > 0) {
                ps.setInt(8, entry.getCreatedBy());
            } else {
                ps.setNull(8, java.sql.Types.NUMERIC);
            }
            ps.setString(9, entry.getNote());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs != null && rs.next()) {
                    entry.setEntryId(rs.getInt(1));
                }
            }
        }
        return entry;
    }

    public Optional<WaitlistEntry> findById(int entryId) {
        String sql = "SELECT w.entry_id, w.customer_id, c.customer_name, c.phone, w.service_date, " +
                     "       w.slot_id, s.slot_label, w.party_size, w.pref_location, w.status, " +
                     "       w.offered_reservation_id, w.created_by, w.created_at, w.note " +
                     "FROM   waitlist_entry w " +
                     "JOIN   customer c   ON c.customer_id = w.customer_id " +
                     "LEFT JOIN time_slot s ON s.slot_id   = w.slot_id " +
                     "WHERE  w.entry_id = ?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "WaitlistDAO.findById");
        }
    }

    public List<WaitlistEntry> findByDate(LocalDate serviceDate) {
        String sql = "SELECT w.entry_id, w.customer_id, c.customer_name, c.phone, w.service_date, " +
                     "       w.slot_id, s.slot_label, w.party_size, w.pref_location, w.status, " +
                     "       w.offered_reservation_id, w.created_by, w.created_at, w.note " +
                     "FROM   waitlist_entry w " +
                     "JOIN   customer c   ON c.customer_id = w.customer_id " +
                     "LEFT JOIN time_slot s ON s.slot_id   = w.slot_id " +
                     "WHERE  w.service_date = ? " +
                     "ORDER  BY w.status ASC, w.created_at ASC";

        List<WaitlistEntry> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(serviceDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "WaitlistDAO.findByDate");
        }
    }

    /**
     * Finds and locks active WAITING entries matching date and slot with FOR UPDATE WAIT 5.
     */
    public List<WaitlistEntry> findActiveWaitingLocked(Connection con, LocalDate serviceDate, int slotId) throws SQLException {
        String sql = "SELECT w.entry_id, w.customer_id, c.customer_name, c.phone, w.service_date, " +
                     "       w.slot_id, s.slot_label, w.party_size, w.pref_location, w.status, " +
                     "       w.offered_reservation_id, w.created_by, w.created_at, w.note " +
                     "FROM   waitlist_entry w " +
                     "JOIN   customer c   ON c.customer_id = w.customer_id " +
                     "LEFT JOIN time_slot s ON s.slot_id   = w.slot_id " +
                     "WHERE  w.service_date = ? " +
                     "  AND (w.slot_id = ? OR w.slot_id IS NULL) " +
                     "  AND  w.status = 'WAITING' " +
                     "ORDER  BY w.created_at ASC " +
                     "FOR UPDATE OF w.entry_id WAIT 5";

        List<WaitlistEntry> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(serviceDate));
            ps.setInt(2, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<WaitlistEntry> findActiveOffers(LocalDate serviceDate) {
        String sql = "SELECT w.entry_id, w.customer_id, c.customer_name, c.phone, w.service_date, " +
                     "       w.slot_id, s.slot_label, w.party_size, w.pref_location, w.status, " +
                     "       w.offered_reservation_id, w.created_by, w.created_at, w.note " +
                     "FROM   waitlist_entry w " +
                     "JOIN   customer c   ON c.customer_id = w.customer_id " +
                     "LEFT JOIN time_slot s ON s.slot_id   = w.slot_id " +
                     "WHERE  w.service_date = ? AND w.status = 'OFFERED' " +
                     "ORDER  BY w.created_at ASC";

        List<WaitlistEntry> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(serviceDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "WaitlistDAO.findActiveOffers");
        }
    }

    public boolean updateStatus(Connection con, int entryId, WaitlistStatus status, Integer offeredReservationId) throws SQLException {
        String sql = "UPDATE waitlist_entry SET status = ?, offered_reservation_id = ? WHERE entry_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status.name());
            if (offeredReservationId != null && offeredReservationId > 0) {
                ps.setInt(2, offeredReservationId);
            } else {
                ps.setNull(2, java.sql.Types.NUMERIC);
            }
            ps.setInt(3, entryId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Atomic sweep: lapses OFFERED entries where the held reservation has expired.
     */
    public int lapseExpiredOffers(Connection con, LocalDateTime now) throws SQLException {
        String sql = "UPDATE waitlist_entry w SET status = 'LAPSED' " +
                     "WHERE w.status = 'OFFERED' " +
                     "  AND EXISTS (SELECT 1 FROM reservation r " +
                     "               WHERE r.reservation_id = w.offered_reservation_id " +
                     "                 AND r.status = 'HELD' " +
                     "                 AND r.hold_expires_at <= ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(now));
            return ps.executeUpdate();
        }
    }

    public int countWaiting(LocalDate date) {
        String sql = "SELECT COUNT(*) FROM waitlist_entry WHERE service_date = ? AND status = 'WAITING'";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "WaitlistDAO.countWaiting");
        }
    }

    private WaitlistEntry mapRow(ResultSet rs) throws SQLException {
        int entryId = rs.getInt("entry_id");
        int custId = rs.getInt("customer_id");
        String name = rs.getString("customer_name");
        String phone = rs.getString("phone");
        LocalDate sDate = rs.getDate("service_date").toLocalDate();
        int sid = rs.getInt("slot_id");
        Integer slotId = rs.wasNull() ? null : sid;
        String slotLabel = rs.getString("slot_label");
        int party = rs.getInt("party_size");
        String pref = rs.getString("pref_location");
        WaitlistStatus st = WaitlistStatus.fromString(rs.getString("status"));
        int offRes = rs.getInt("offered_reservation_id");
        Integer offeredResId = rs.wasNull() ? null : offRes;
        int crBy = rs.getInt("created_by");
        Integer createdBy = rs.wasNull() ? null : crBy;
        Timestamp crTs = rs.getTimestamp("created_at");
        LocalDateTime createdAt = crTs != null ? crTs.toLocalDateTime() : util.DbClock.localDateTime();
        String note = rs.getString("note");

        return new WaitlistEntry(entryId, custId, name, phone, sDate, slotId, slotLabel,
                party, pref, st, offeredResId, createdBy, createdAt, note);
    }
}
