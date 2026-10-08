package dao;

import db.DatabaseConnection;
import model.AuditEntry;
import security.Session;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for audit_log.
 * Provides transactional logging within existing database transactions,
 * as well as standalone logging for authentication and system events.
 */
public class AuditDAO {

    public void log(Connection con, Integer userId, String action, String entityType, Long entityId, String details) throws SQLException {
        String sql = "INSERT INTO audit_log (audit_id, user_id, action, entity_type, entity_id, details, created_at) " +
                     "VALUES (audit_seq.NEXTVAL, ?, ?, ?, ?, ?, SYSTIMESTAMP)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (userId != null && userId > 0) {
                ps.setInt(1, userId);
            } else {
                ps.setNull(1, java.sql.Types.NUMERIC);
            }
            ps.setString(2, action != null ? action.trim().toUpperCase() : "UNKNOWN");
            ps.setString(3, entityType != null ? entityType.trim().toUpperCase() : null);
            if (entityId != null) {
                ps.setLong(4, entityId);
            } else {
                ps.setNull(4, java.sql.Types.NUMERIC);
            }
            ps.setString(5, details != null && details.length() > 400 ? details.substring(0, 400) : details);
            ps.executeUpdate();
        }
    }

    public void log(String action, String entityType, Long entityId, String details) {
        Integer userId = null;
        if (Session.isLoggedIn() && Session.getInstance().currentUser() != null) {
            userId = Session.getInstance().currentUser().getUserId();
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            log(con, userId, action, entityType, entityId, details);
        } catch (SQLException e) {
            // Mapping or logging
            throw OracleErrorMapper.map(e, "AuditDAO.log");
        }
    }

    public void log(Integer userId, String action, String entityType, Long entityId, String details) {
        try (Connection con = DatabaseConnection.getConnection()) {
            log(con, userId, action, entityType, entityId, details);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "AuditDAO.log");
        }
    }

    public List<AuditEntry> findRecent(int limit, int offset, String actionFilter, Integer userFilter, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder(
                "SELECT a.audit_id, a.user_id, u.username, u.full_name, a.action, a.entity_type, a.entity_id, a.details, a.created_at " +
                "FROM audit_log a " +
                "LEFT JOIN app_user u ON a.user_id = u.user_id " +
                "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (actionFilter != null && !actionFilter.isBlank() && !actionFilter.equalsIgnoreCase("ALL")) {
            sql.append("AND a.action = ? ");
            params.add(actionFilter.trim().toUpperCase());
        }
        if (userFilter != null && userFilter > 0) {
            sql.append("AND a.user_id = ? ");
            params.add(userFilter);
        }
        if (fromDate != null) {
            sql.append("AND a.created_at >= ? ");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            sql.append("AND a.created_at <= ? ");
            params.add(Timestamp.valueOf(toDate.atTime(LocalTime.MAX)));
        }

        sql.append("ORDER BY a.audit_id DESC ");
        sql.append("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(Math.max(0, offset));
        params.add(Math.max(1, limit));

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String s) {
                    ps.setString(i + 1, s);
                } else if (p instanceof Integer num) {
                    ps.setInt(i + 1, num);
                } else if (p instanceof Timestamp ts) {
                    ps.setTimestamp(i + 1, ts);
                }
            }

            List<AuditEntry> entries = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long auditId = rs.getLong("audit_id");
                    int uid = rs.getInt("user_id");
                    Integer userId = rs.wasNull() ? null : uid;
                    String username = rs.getString("username");
                    String fullName = rs.getString("full_name");
                    String action = rs.getString("action");
                    String entityType = rs.getString("entity_type");
                    long eid = rs.getLong("entity_id");
                    Long entityId = rs.wasNull() ? null : eid;
                    String details = rs.getString("details");
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : null;

                    entries.add(new AuditEntry(auditId, userId, username, fullName, action, entityType, entityId, details, createdAt));
                }
            }
            return entries;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "AuditDAO.findRecent");
        }
    }

    public int count(String actionFilter, Integer userFilter, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_log a WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (actionFilter != null && !actionFilter.isBlank() && !actionFilter.equalsIgnoreCase("ALL")) {
            sql.append("AND a.action = ? ");
            params.add(actionFilter.trim().toUpperCase());
        }
        if (userFilter != null && userFilter > 0) {
            sql.append("AND a.user_id = ? ");
            params.add(userFilter);
        }
        if (fromDate != null) {
            sql.append("AND a.created_at >= ? ");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            sql.append("AND a.created_at <= ? ");
            params.add(Timestamp.valueOf(toDate.atTime(LocalTime.MAX)));
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String s) {
                    ps.setString(i + 1, s);
                } else if (p instanceof Integer num) {
                    ps.setInt(i + 1, num);
                } else if (p instanceof Timestamp ts) {
                    ps.setTimestamp(i + 1, ts);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "AuditDAO.count");
        }
    }
}
