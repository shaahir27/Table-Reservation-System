package security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RolePermissionMatrixTest {

    @Test
    @DisplayName("MANAGER role holds every permission in the system")
    void testManagerHasAllPermissions() {
        for (Permission p : Permission.values()) {
            assertTrue(Role.MANAGER.can(p), "MANAGER must have permission: " + p);
        }
    }

    @Test
    @DisplayName("HOST role has operational permissions but lacks administrative permissions")
    void testHostPermissionsMatrix() {
        // Allowed for HOST
        assertTrue(Role.HOST.can(Permission.RESERVATION_CREATE));
        assertTrue(Role.HOST.can(Permission.RESERVATION_CANCEL));
        assertTrue(Role.HOST.can(Permission.RESERVATION_SEAT));
        assertTrue(Role.HOST.can(Permission.RESERVATION_NOSHOW));
        assertTrue(Role.HOST.can(Permission.WAITLIST_MANAGE));
        assertTrue(Role.HOST.can(Permission.CUSTOMER_EDIT));

        // Forbidden for HOST
        assertFalse(Role.HOST.can(Permission.CUSTOMER_DELETE));
        assertFalse(Role.HOST.can(Permission.TABLE_MANAGE));
        assertFalse(Role.HOST.can(Permission.FLOOR_LAYOUT_EDIT));
        assertFalse(Role.HOST.can(Permission.REPORT_VIEW));
        assertFalse(Role.HOST.can(Permission.USER_MANAGE));
        assertFalse(Role.HOST.can(Permission.AUDIT_VIEW));
        assertFalse(Role.HOST.can(Permission.SETTINGS_EDIT));
    }

    @Test
    @DisplayName("Role fromString safely parses uppercase, lowercase, and rejects invalid strings")
    void testRoleFromString() {
        assertEquals(Role.MANAGER, Role.fromString("manager"));
        assertEquals(Role.MANAGER, Role.fromString("MANAGER"));
        assertEquals(Role.HOST, Role.fromString("host"));
        assertEquals(Role.HOST, Role.fromString("HOST"));
        assertNull(Role.fromString("UNKNOWN"));
        assertNull(Role.fromString(null));
    }
}
