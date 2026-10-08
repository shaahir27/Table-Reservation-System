package service;

import dao.AuditDAO;
import dao.SettingDAO;
import dao.UserDAO;
import exception.AccountLockedException;
import exception.AuthenticationException;
import exception.ValidationException;
import model.AppUser;
import security.PasswordHasher;
import security.PasswordPolicy;
import security.Role;
import security.Session;
import util.DbClock;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service managing user authentication, login security, and password lifecycle.
 */
public class AuthService {
    private final UserDAO userDAO;
    private final AuditDAO auditDAO;
    private final SettingDAO settingDAO;

    public AuthService() {
        this(new UserDAO(), new AuditDAO(), new SettingDAO());
    }

    public AuthService(UserDAO userDAO, AuditDAO auditDAO, SettingDAO settingDAO) {
        this.userDAO = userDAO;
        this.auditDAO = auditDAO;
        this.settingDAO = settingDAO;
    }

    public boolean hasUsers() {
        return userDAO.countUsers() > 0;
    }

    public AppUser login(String username, char[] password) {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            throw new AuthenticationException("Please enter both username and password.");
        }

        String normalizedUser = username.trim().toLowerCase();
        Optional<AppUser> userOpt = userDAO.findByUsername(normalizedUser);

        if (userOpt.isEmpty()) {
            PasswordHasher.dummyDerivation(password);
            auditDAO.log(null, "LOGIN_FAILED", "USER", null, "Unknown username: " + normalizedUser);
            throw new AuthenticationException("Invalid username or password.");
        }

        AppUser user = userOpt.get();

        if (!user.isActive()) {
            PasswordHasher.dummyDerivation(password);
            auditDAO.log(user.getUserId(), "LOGIN_FAILED", "USER", (long) user.getUserId(), "Inactive account attempt");
            throw new AuthenticationException("Invalid username or password.");
        }

        // Check DB-backed lockout status
        LocalDateTime now = DbClock.localDateTime();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            long remaining = Duration.between(now, user.getLockedUntil()).getSeconds();
            throw new AccountLockedException(Math.max(1, remaining));
        }

        boolean valid = PasswordHasher.verify(password, user.getPwdHash(), user.getPwdSalt(), user.getPwdIter());

        if (!valid) {
            int threshold = settingDAO.getInt("lockout_threshold", 5);
            int lockMinutes = settingDAO.getInt("lockout_minutes", 5);

            userDAO.recordFailedAttempt(user.getUserId(), threshold, lockMinutes);

            // Re-fetch to check if threshold was just reached
            AppUser refreshed = userDAO.findById(user.getUserId()).orElse(user);
            if (refreshed.getLockedUntil() != null && refreshed.getLockedUntil().isAfter(DbClock.localDateTime())) {
                long remaining = Duration.between(DbClock.localDateTime(), refreshed.getLockedUntil()).getSeconds();
                auditDAO.log(user.getUserId(), "LOGIN_LOCKED", "USER", (long) user.getUserId(),
                        "Account locked for " + lockMinutes + " min after " + refreshed.getFailedAttempts() + " failed attempts");
                throw new AccountLockedException(Math.max(1, remaining));
            } else {
                auditDAO.log(user.getUserId(), "LOGIN_FAILED", "USER", (long) user.getUserId(),
                        "Wrong password (attempt " + refreshed.getFailedAttempts() + ")");
                throw new AuthenticationException("Invalid username or password.");
            }
        }

        // Password verified successfully
        String newHash = null;
        String newSalt = null;
        Integer newIter = null;
        if (PasswordHasher.needsRehash(user.getPwdIter())) {
            PasswordHasher.Hashed upgraded = PasswordHasher.hash(password);
            newHash = upgraded.hashB64();
            newSalt = upgraded.saltB64();
            newIter = upgraded.iterations();
        }

        userDAO.recordSuccessfulLogin(user.getUserId(), newHash, newSalt, newIter);
        auditDAO.log(user.getUserId(), "LOGIN_OK", "USER", (long) user.getUserId(), "User logged in successfully");

        // Refresh user record with updated timestamp and reset attempts
        AppUser loggedInUser = userDAO.findById(user.getUserId()).orElse(user);
        Session.login(loggedInUser);
        return loggedInUser;
    }

    public void logout() {
        if (Session.isLoggedIn()) {
            AppUser user = Session.getInstance().currentUser();
            if (user != null) {
                auditDAO.log(user.getUserId(), "LOGOUT", "USER", (long) user.getUserId(), "User signed out");
            }
            Session.logout();
        }
    }

    public void changePassword(int userId, char[] currentPassword, char[] newPassword) {
        AppUser user = userDAO.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User account not found."));

        if (!PasswordHasher.verify(currentPassword, user.getPwdHash(), user.getPwdSalt(), user.getPwdIter())) {
            throw new AuthenticationException("Current password is incorrect.");
        }

        PasswordPolicy.PolicyResult policy = PasswordPolicy.validate(newPassword, user.getUsername());
        if (!policy.valid()) {
            throw new ValidationException(policy.getFirstError());
        }

        PasswordHasher.Hashed hashed = PasswordHasher.hash(newPassword);
        userDAO.updatePassword(userId, hashed.hashB64(), hashed.saltB64(), hashed.iterations(), false);
        auditDAO.log(userId, "PWD_CHANGED", "USER", (long) userId, "Password changed successfully");

        // Refresh user in current session if it is the current user
        if (Session.isLoggedIn() && Session.getInstance().currentUser().getUserId() == userId) {
            userDAO.findById(userId).ifPresent(Session::refreshUser);
        }
    }

    public AppUser setupFirstManager(String username, String fullName, char[] password) {
        if (hasUsers()) {
            throw new IllegalStateException("First-run setup has already been completed.");
        }

        if (username == null || username.trim().length() < 3) {
            throw new ValidationException("Username must be at least 3 characters.");
        }
        if (fullName == null || fullName.trim().length() < 2) {
            throw new ValidationException("Full name must be at least 2 characters.");
        }

        PasswordPolicy.PolicyResult policy = PasswordPolicy.validate(password, username);
        if (!policy.valid()) {
            throw new ValidationException(policy.getFirstError());
        }

        PasswordHasher.Hashed hashed = PasswordHasher.hash(password);
        AppUser manager = new AppUser(username.trim().toLowerCase(), fullName.trim(), Role.MANAGER,
                hashed.hashB64(), hashed.saltB64(), hashed.iterations());

        userDAO.insert(manager);
        auditDAO.log(manager.getUserId(), "USER_CREATED", "USER", (long) manager.getUserId(),
                "Initial Manager account created during system setup");

        return manager;
    }
}
