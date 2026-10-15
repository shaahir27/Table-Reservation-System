package util;

import dao.AuditDAO;
import dao.UserDAO;
import db.DatabaseConnection;
import model.AppUser;
import security.PasswordHasher;
import security.Role;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Seeds and updates authentication accounts, executes canonical sql/02_seed.sql,
 * links reservations to staff, and records baseline audit events in Oracle 21c XE.
 */
public class SeedAuthData {

    public static void main(String[] args) {
        System.out.println("=== RTRS Database & Authentication Seeder ===");
        UserDAO userDAO = new UserDAO();
        AuditDAO auditDAO = new AuditDAO();

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            // 1. Ensure/Update demo accounts with known credentials
            System.out.println("1. Setting up demo staff accounts...");
            upsertDemoUser(con, userDAO, "manager", "Operations General Manager", Role.MANAGER, "Manager123!");
            upsertDemoUser(con, userDAO, "host", "Head Host / Front Desk", Role.HOST, "HostPass123!");
            upsertDemoUser(con, userDAO, "priya", "Priya Patel", Role.HOST, "StaffPass123!");

            // 2. Execute sql/02_seed.sql
            Path seedPath = Path.of("sql", "02_seed.sql");
            if (Files.exists(seedPath)) {
                System.out.println("2. Executing canonical seed script: " + seedPath.toAbsolutePath() + "...");
                executeSqlScript(con, seedPath);
                System.out.println("   ✓ Successfully loaded seed data from sql/02_seed.sql");
            } else {
                System.out.println("   ! Notice: sql/02_seed.sql not found at " + seedPath.toAbsolutePath());
            }

            // 3. Backfill reservations with created_by if null
            System.out.println("3. Verifying author attribution on reservations...");
            String backfillSql = "UPDATE reservation SET created_by = (SELECT MIN(user_id) FROM app_user) WHERE created_by IS NULL";
            try (PreparedStatement ps = con.prepareStatement(backfillSql)) {
                int updatedRes = ps.executeUpdate();
                if (updatedRes > 0) {
                    auditDAO.log(con, 1, "DATA_MIGRATION", "RESERVATION", null,
                            "Backfilled " + updatedRes + " reservations with author attribution");
                    System.out.println("   ✓ Backfilled " + updatedRes + " reservations with author attribution.");
                } else {
                    System.out.println("   ✓ All reservations already attributed to staff.");
                }
            }

            con.commit();
            System.out.println("\nSUCCESS: All rich demo data and staff accounts successfully committed to Oracle XE!");

        } catch (Exception e) {
            System.err.println("\nERROR during seeding: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void upsertDemoUser(Connection con, UserDAO userDAO, String username, String fullName, Role role, String password) throws SQLException {
        PasswordHasher.Hashed hash = PasswordHasher.hash(password.toCharArray());
        Optional<AppUser> existing = userDAO.findByUsername(username);

        if (existing.isEmpty()) {
            AppUser user = new AppUser(
                    0, username, fullName, role,
                    hash.hashB64(), hash.saltB64(), hash.iterations(),
                    true, false, 0, null, null, null
            );
            userDAO.insert(con, user);
            System.out.println("   + Created " + role + ": " + username + " (password: " + password + ")");
        } else {
            // Update password hash, ensure active, must_change_pwd = false, failed_attempts = 0
            String sql = "UPDATE app_user SET full_name = ?, role = ?, pwd_hash = ?, pwd_salt = ?, pwd_iter = ?, " +
                         "is_active = 1, must_change_pwd = 0, failed_attempts = 0, locked_until = NULL WHERE username = LOWER(?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, fullName);
                ps.setString(2, role.name());
                ps.setString(3, hash.hashB64());
                ps.setString(4, hash.saltB64());
                ps.setInt(5, hash.iterations());
                ps.setString(6, username);
                ps.executeUpdate();
                System.out.println("   ✓ Verified " + role + ": " + username + " (password: " + password + ")");
            }
        }
    }

    private static void executeSqlScript(Connection con, Path scriptPath) throws IOException, SQLException {
        String content = Files.readString(scriptPath);
        StringBuilder currentStmt = new StringBuilder();

        try (Statement st = con.createStatement()) {
            for (String rawLine : content.split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.startsWith("--") || line.isBlank()) {
                    continue;
                }

                currentStmt.append(" ").append(line);
                if (line.endsWith(";")) {
                    String stmtStr = currentStmt.toString().trim();
                    stmtStr = stmtStr.substring(0, stmtStr.length() - 1).trim(); // remove trailing ;
                    currentStmt.setLength(0);

                    if (!stmtStr.equalsIgnoreCase("COMMIT")) {
                        try {
                            st.execute(stmtStr);
                        } catch (SQLException e) {
                            // If a table or record already exists or minor non-fatal error, print and continue or throw
                            System.err.println("   ! SQL Warning on statement: " + stmtStr.substring(0, Math.min(60, stmtStr.length())) + "... -> " + e.getMessage());
                            throw e;
                        }
                    }
                }
            }
        }
    }
}
