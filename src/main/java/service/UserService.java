package service;

import dao.AuditDAO;
import dao.UserDAO;
import db.DatabaseConnection;
import exception.AppException;
import exception.ValidationException;
import model.AppUser;
import security.AuthContext;
import security.PasswordHasher;
import security.PasswordPolicy;
import security.Permission;
import security.Role;
import security.Session;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Service for managing staff accounts and access privileges.
 * Requires USER_MANAGE permission for all administrative actions.
 */
public class UserService {
    private final UserDAO userDAO;
    private final AuditDAO auditDAO;
    private final AuthContext auth;

    public UserService() {
        this(new UserDAO(), new AuditDAO(), Session.getInstance());
    }

    public UserService(UserDAO userDAO, AuditDAO auditDAO, AuthContext auth) {
        this.userDAO = userDAO;
        this.auditDAO = auditDAO;
        this.auth = auth != null ? auth : Session.getInstance();
    }

    public List<AppUser> getAllUsers() {
        auth.require(Permission.USER_MANAGE);
        return userDAO.findAll();
    }

    public AppUser createUser(String username, String fullName, Role role, char[] initialPassword, boolean mustChange) {
        auth.require(Permission.USER_MANAGE);

        if (username == null || username.trim().length() < 3) {
            throw new ValidationException("Username must be at least 3 characters.");
        }
        if (fullName == null || fullName.trim().length() < 2) {
            throw new ValidationException("Full name must be at least 2 characters.");
        }
        if (role == null) {
            throw new ValidationException("Please select a role.");
        }

        String normUser = username.trim().toLowerCase();
        if (userDAO.findByUsername(normUser).isPresent()) {
            throw new ValidationException("Username '" + normUser + "' is already in use.");
        }

        PasswordPolicy.PolicyResult policy = PasswordPolicy.validate(initialPassword, normUser);
        if (!policy.valid()) {
            throw new ValidationException(policy.getFirstError());
        }

        PasswordHasher.Hashed hashed = PasswordHasher.hash(initialPassword);
        AppUser newUser = new AppUser(normUser, fullName.trim(), role, hashed.hashB64(), hashed.saltB64(), hashed.iterations());
        newUser.setMustChangePwd(mustChange);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                userDAO.insert(con, newUser);
                auditDAO.log(con, auth.currentUser() != null ? auth.currentUser().getUserId() : null,
                        "USER_CREATED", "USER", (long) newUser.getUserId(),
                        "Created staff account: " + newUser.getUsername() + " (" + role + ")");
                con.commit();
                return newUser;
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "UserService.createUser");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserService.createUser.conn");
        }
    }

    public void changeRole(int userId, Role newRole) {
        auth.require(Permission.USER_MANAGE);
        if (newRole == null) {
            throw new ValidationException("Role cannot be null.");
        }

        AppUser user = userDAO.findById(userId)
                .orElseThrow(() -> new ValidationException("User not found."));

        if (user.getRole() == newRole) return;

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // Last active manager guard
                if (user.getRole() == Role.MANAGER && newRole != Role.MANAGER && user.isActive()) {
                    if (userDAO.countActiveManagers(con) <= 1) {
                        throw new AppException("Cannot change role: At least one active MANAGER account must remain.");
                    }
                }

                userDAO.updateRole(con, userId, newRole);
                auditDAO.log(con, auth.currentUser() != null ? auth.currentUser().getUserId() : null,
                        "USER_ROLE_CHANGED", "USER", (long) userId,
                        "Changed role of " + user.getUsername() + " from " + user.getRole() + " to " + newRole);
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "UserService.changeRole");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserService.changeRole.conn");
        }
    }

    public void resetPassword(int userId, char[] tempPassword) {
        auth.require(Permission.USER_MANAGE);

        AppUser user = userDAO.findById(userId)
                .orElseThrow(() -> new ValidationException("User not found."));

        PasswordPolicy.PolicyResult policy = PasswordPolicy.validate(tempPassword, user.getUsername());
        if (!policy.valid()) {
            throw new ValidationException(policy.getFirstError());
        }

        PasswordHasher.Hashed hashed = PasswordHasher.hash(tempPassword);
        userDAO.updatePassword(userId, hashed.hashB64(), hashed.saltB64(), hashed.iterations(), true);
        auditDAO.log(auth.currentUser() != null ? auth.currentUser().getUserId() : null,
                "PWD_RESET", "USER", (long) userId,
                "Temporary password generated for " + user.getUsername());
    }

    public void deactivateUser(int userId) {
        auth.require(Permission.USER_MANAGE);

        AppUser current = auth.currentUser();
        if (current != null && current.getUserId() == userId) {
            throw new AppException("You cannot deactivate your own account.");
        }

        AppUser target = userDAO.findById(userId)
                .orElseThrow(() -> new ValidationException("User not found."));

        if (!target.isActive()) return;

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                if (target.getRole() == Role.MANAGER) {
                    if (userDAO.countActiveManagers(con) <= 1) {
                        throw new AppException("Cannot deactivate the only active MANAGER account.");
                    }
                }

                userDAO.updateStatus(con, userId, false);
                auditDAO.log(con, current != null ? current.getUserId() : null,
                        "USER_DEACTIVATED", "USER", (long) userId,
                        "Deactivated account: " + target.getUsername());
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "UserService.deactivateUser");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserService.deactivateUser.conn");
        }
    }

    public void reactivateUser(int userId) {
        auth.require(Permission.USER_MANAGE);

        AppUser target = userDAO.findById(userId)
                .orElseThrow(() -> new ValidationException("User not found."));

        if (target.isActive()) return;

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                userDAO.updateStatus(con, userId, true);
                auditDAO.log(con, auth.currentUser() != null ? auth.currentUser().getUserId() : null,
                        "USER_REACTIVATED", "USER", (long) userId,
                        "Reactivated account: " + target.getUsername());
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw OracleErrorMapper.map(e, "UserService.reactivateUser");
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "UserService.reactivateUser.conn");
        }
    }
}
