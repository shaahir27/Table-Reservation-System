package service;

import dao.AuditDAO;
import model.AuditEntry;
import security.AuthContext;
import security.Permission;
import security.Session;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for viewing the system audit trail. Requires AUDIT_VIEW permission.
 */
public class AuditService {
    private final AuditDAO auditDAO;
    private final AuthContext auth;

    public AuditService() {
        this(new AuditDAO(), Session.getInstance());
    }

    public AuditService(AuditDAO auditDAO, AuthContext auth) {
        this.auditDAO = auditDAO;
        this.auth = auth != null ? auth : Session.getInstance();
    }

    public List<AuditEntry> getAuditLogs(int limit, int offset, String actionFilter, Integer userFilter, LocalDate fromDate, LocalDate toDate) {
        auth.require(Permission.AUDIT_VIEW);
        return auditDAO.findRecent(limit, offset, actionFilter, userFilter, fromDate, toDate);
    }

    public int getAuditCount(String actionFilter, Integer userFilter, LocalDate fromDate, LocalDate toDate) {
        auth.require(Permission.AUDIT_VIEW);
        return auditDAO.count(actionFilter, userFilter, fromDate, toDate);
    }
}
