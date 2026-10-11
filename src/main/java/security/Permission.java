package security;

/**
 * Granular permissions governing operations across RTRS v2.
 * Enforced directly at the service boundary via AuthContext.require(Permission).
 */
public enum Permission {
    RESERVATION_CREATE,
    RESERVATION_CANCEL,
    RESERVATION_SEAT,
    RESERVATION_NOSHOW,
    WAITLIST_MANAGE,
    CUSTOMER_EDIT,
    CUSTOMER_DELETE,
    TABLE_MANAGE,
    FLOOR_LAYOUT_EDIT,
    REPORT_VIEW,
    USER_MANAGE,
    AUDIT_VIEW,
    SETTINGS_EDIT
}
