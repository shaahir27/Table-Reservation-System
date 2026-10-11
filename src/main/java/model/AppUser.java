package model;

import security.Role;

import java.time.LocalDateTime;

/**
 * Domain model representing an authenticated staff user.
 */
public class AppUser {
    private int userId;
    private String username;
    private String fullName;
    private Role role;
    private String pwdHash;
    private String pwdSalt;
    private int pwdIter;
    private boolean active;
    private boolean mustChangePwd;
    private int failedAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    public AppUser() {
    }

    public AppUser(int userId, String username, String fullName, Role role,
                   String pwdHash, String pwdSalt, int pwdIter,
                   boolean active, boolean mustChangePwd, int failedAttempts,
                   LocalDateTime lockedUntil, LocalDateTime lastLoginAt, LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.pwdHash = pwdHash;
        this.pwdSalt = pwdSalt;
        this.pwdIter = pwdIter;
        this.active = active;
        this.mustChangePwd = mustChangePwd;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
    }

    // Convenience constructor for creating a new user prior to persistence
    public AppUser(String username, String fullName, Role role, String pwdHash, String pwdSalt, int pwdIter) {
        this(0, username, fullName, role, pwdHash, pwdSalt, pwdIter, true, false, 0, null, null, null);
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getPwdHash() {
        return pwdHash;
    }

    public void setPwdHash(String pwdHash) {
        this.pwdHash = pwdHash;
    }

    public String getPwdSalt() {
        return pwdSalt;
    }

    public void setPwdSalt(String pwdSalt) {
        this.pwdSalt = pwdSalt;
    }

    public int getPwdIter() {
        return pwdIter;
    }

    public void setPwdIter(int pwdIter) {
        this.pwdIter = pwdIter;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isMustChangePwd() {
        return mustChangePwd;
    }

    public void setMustChangePwd(boolean mustChangePwd) {
        this.mustChangePwd = mustChangePwd;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getInitials() {
        if (fullName == null || fullName.isBlank()) {
            return username != null && !username.isBlank() ? username.substring(0, 1).toUpperCase() : "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    @Override
    public String toString() {
        return fullName + " (" + username + " - " + role + ")";
    }
}
