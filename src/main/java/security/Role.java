package security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Roles supported in RTRS v2:
 * - HOST: operational front-of-house staff (reservations, waitlist, seating, customer editing).
 * - MANAGER: administrative control (reports, table layouts, staff accounts, audit log, settings).
 */
public enum Role {
    HOST(EnumSet.of(
            Permission.RESERVATION_CREATE,
            Permission.RESERVATION_CANCEL,
            Permission.RESERVATION_SEAT,
            Permission.RESERVATION_NOSHOW,
            Permission.WAITLIST_MANAGE,
            Permission.CUSTOMER_EDIT
    )),
    MANAGER(EnumSet.allOf(Permission.class));

    private final Set<Permission> perms;

    Role(Set<Permission> perms) {
        this.perms = Collections.unmodifiableSet(perms);
    }

    public boolean can(Permission permission) {
        return permission != null && perms.contains(permission);
    }

    public Set<Permission> getPermissions() {
        return perms;
    }

    public static Role fromString(String roleStr) {
        if (roleStr == null) return null;
        try {
            return Role.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
