package dao;

import db.DatabaseConnection;
import model.AppUser;
import security.Role;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for AppUser entities.
 */
public class UserDAO {

    public Optional<AppUser> findByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        String sql = "SELECT user_id, username, full_name, role, pwd_hash, pwd_salt, pwd_iter, " +
                     "is_active, must_change_pwd, failed_attempts, locked_until, last_login_at, created_at " +
                     "FROM app_user WHERE username = LOWER(?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserDAO.findByUsername");
        }
    }

    public Optional<AppUser> findById(int userId) {
        String sql = "SELECT user_id, username, full_name, role, pwd_hash, pwd_salt, pwd_iter, " +
                     "is_active, must_change_pwd, failed_attempts, locked_until, last_login_at, created_at " +
                     "FROM app_user WHERE user_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserDAO.findById");
        }
    }

    public List<AppUser> findAll() {
        String sql = "SELECT user_id, username, full_name, role, pwd_hash, pwd_salt, pwd_iter, " +
                     "is_active, must_change_pwd, failed_attempts, locked_until, last_login_at, created_at " +
                     "FROM app_user ORDER BY user_id";
        List<AppUser> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserDAO.findAll");
        }
    }

    public int countUsers() {
        String sql = "SELECT COUNT(*) FROM app_user";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserDAO.countUsers");
        }
    }

    public int countActiveManagers() {
        try (Connection con = DatabaseConnection.getConnection()) {
            return countActiveManagers(con);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserDAO.countActiveManagers");
        }
    }

    public int countActiveManagers(Connection con) throws SQLException {
        String sql = "SELECT COUNT(*) FROM app_user WHERE role = 'MANAGER' AND is_active = 1";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    public void insert(AppUser user) {
        try (Connection con = DatabaseConnection.getConnection()) {
            insert(con, user);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserDAO.insert");
        }
    }

    public void insert(Connection con, AppUser user) throws SQLException {
        String sql = "INSERT INTO app_user (user_id, username, full_name, role, pwd_hash, pwd_salt, pwd_iter, " +
                     "is_active, must_change_pwd, failed_attempts, locked_until, last_login_at, created_at) " +
                     "VALUES (user_seq.NEXTVAL, LOWER(?), ?, ?, ?, ?, ?, ?, ?, 0, NULL, NULL, SYSTIMESTAMP)";
        try (PreparedStatement ps = con.prepareStatement(sql, new String[]{"USER_ID"})) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getRole().name());
            ps.setString(4, user.getPwdHash());
            ps.setString(5, user.getPwdSalt());
            ps.setInt(6, user.getPwdIter());
            ps.setInt(7, user.isActive() ? 1 : 0);
            ps.setInt(8, user.isMustChangePwd() ? 1 : 0);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs != null && rs.next()) {
                    user.setUserId(rs.getInt(1));
                }
            }
        }
    }

    public void updatePassword(int userId, String hash, String salt, int iter, boolean mustChange) {
        String sql = "UPDATE app_user SET pwd_hash = ?, pwd_salt = ?, pwd_iter = ?, must_change_pwd = ?, " +
                     "failed_attempts = 0, locked_until = NULL WHERE user_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setInt(3, iter);
            ps.setInt(4, mustChange ? 1 : 0);
            ps.setInt(5, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserDAO.updatePassword");
        }
    }

    public void recordFailedAttempt(int userId, int threshold, int lockMinutes) {
        String sql = "UPDATE app_user " +
                     "   SET failed_attempts = failed_attempts + 1, " +
                     "       locked_until    = CASE WHEN failed_attempts + 1 >= ? " +
                     "                              THEN SYSTIMESTAMP + NUMTODSINTERVAL(?, 'MINUTE') " +
                     "                              ELSE locked_until END " +
                     " WHERE user_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            ps.setInt(2, lockMinutes);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserDAO.recordFailedAttempt");
        }
    }

    public void recordSuccessfulLogin(int userId, String newHash, String newSalt, Integer newIter) {
        String sql;
        if (newHash != null && newSalt != null && newIter != null) {
            sql = "UPDATE app_user SET failed_attempts = 0, locked_until = NULL, last_login_at = SYSTIMESTAMP, " +
                  "pwd_hash = ?, pwd_salt = ?, pwd_iter = ? WHERE user_id = ?";
        } else {
            sql = "UPDATE app_user SET failed_attempts = 0, locked_until = NULL, last_login_at = SYSTIMESTAMP " +
                  "WHERE user_id = ?";
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (newHash != null && newSalt != null && newIter != null) {
                ps.setString(1, newHash);
                ps.setString(2, newSalt);
                ps.setInt(3, newIter);
                ps.setInt(4, userId);
            } else {
                ps.setInt(1, userId);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserDAO.recordSuccessfulLogin");
        }
    }

    public void updateRole(Connection con, int userId, Role role) throws SQLException {
        String sql = "UPDATE app_user SET role = ? WHERE user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, role.name());
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void updateStatus(Connection con, int userId, boolean isActive) throws SQLException {
        String sql = "UPDATE app_user SET is_active = ? WHERE user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, isActive ? 1 : 0);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private AppUser mapRow(ResultSet rs) throws SQLException {
        int userId = rs.getInt("user_id");
        String username = rs.getString("username");
        String fullName = rs.getString("full_name");
        String roleStr = rs.getString("role");
        Role role = Role.fromString(roleStr);
        String pwdHash = rs.getString("pwd_hash");
        String pwdSalt = rs.getString("pwd_salt");
        int pwdIter = rs.getInt("pwd_iter");
        boolean active = rs.getInt("is_active") == 1;
        boolean mustChangePwd = rs.getInt("must_change_pwd") == 1;
        int failedAttempts = rs.getInt("failed_attempts");

        Timestamp lockedUntilTs = rs.getTimestamp("locked_until");
        LocalDateTime lockedUntil = lockedUntilTs != null ? lockedUntilTs.toLocalDateTime() : null;

        Timestamp lastLoginTs = rs.getTimestamp("last_login_at");
        LocalDateTime lastLogin = lastLoginTs != null ? lastLoginTs.toLocalDateTime() : null;

        Timestamp createdTs = rs.getTimestamp("created_at");
        LocalDateTime createdAt = createdTs != null ? createdTs.toLocalDateTime() : null;

        return new AppUser(userId, username, fullName, role, pwdHash, pwdSalt, pwdIter,
                active, mustChangePwd, failedAttempts, lockedUntil, lastLogin, createdAt);
    }
}
