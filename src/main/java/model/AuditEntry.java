package model;

import java.time.LocalDateTime;

/**
 * Domain model representing an immutable entry in the audit trail.
 */
public class AuditEntry {
    private long auditId;
    private Integer userId;
    private String username;
    private String userFullName;
    private String action;
    private String entityType;
    private Long entityId;
    private String details;
    private LocalDateTime createdAt;

    public AuditEntry() {
    }

    public AuditEntry(long auditId, Integer userId, String username, String userFullName,
                      String action, String entityType, Long entityId, String details,
                      LocalDateTime createdAt) {
        this.auditId = auditId;
        this.userId = userId;
        this.username = username;
        this.userFullName = userFullName;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.createdAt = createdAt;
    }

    public long getAuditId() {
        return auditId;
    }

    public void setAuditId(long auditId) {
        this.auditId = auditId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
