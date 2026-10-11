package security;

import exception.AccessDeniedException;
import model.AppUser;
import model.RestaurantTable;
import model.TableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.CustomerService;
import service.ReportService;
import service.TableService;
import service.UserService;

import static org.junit.jupiter.api.Assertions.*;

class ServiceAuthorizationTest {
    private AppUser hostUser;
    private AppUser managerUser;

    @BeforeEach
    void setUp() {
        hostUser = new AppUser("host1", "Host Staff", Role.HOST, "hash", "salt", 600000);
        hostUser.setUserId(10);

        managerUser = new AppUser("manager1", "Admin Manager", Role.MANAGER, "hash", "salt", 600000);
        managerUser.setUserId(1);
    }

    @Test
    @DisplayName("HOST calling TableService.createTable throws AccessDeniedException")
    void testHostCannotCreateTable() {
        TableService tableService = new TableService(null, null, AuthContext.fixed(hostUser));
        RestaurantTable table = new RestaurantTable("T01", 4, "Indoor", TableStatus.AVAILABLE);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> tableService.createTable(table));
        assertTrue(ex.getMessage().contains("TABLE_MANAGE"));
    }

    @Test
    @DisplayName("HOST calling TableService.deleteTable throws AccessDeniedException")
    void testHostCannotDeleteTable() {
        TableService tableService = new TableService(null, null, AuthContext.fixed(hostUser));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> tableService.deleteTable(1));
        assertTrue(ex.getMessage().contains("TABLE_MANAGE"));
    }

    @Test
    @DisplayName("HOST calling CustomerService.deleteCustomer throws AccessDeniedException")
    void testHostCannotDeleteCustomer() {
        CustomerService customerService = new CustomerService(null, null, AuthContext.fixed(hostUser));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> customerService.deleteCustomer(5));
        assertTrue(ex.getMessage().contains("CUSTOMER_DELETE"));
    }

    @Test
    @DisplayName("HOST calling ReportService.getTodaysReservationsReport throws AccessDeniedException")
    void testHostCannotViewReports() {
        ReportService reportService = new ReportService(null, AuthContext.fixed(hostUser));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, reportService::getTodaysReservationsReport);
        assertTrue(ex.getMessage().contains("REPORT_VIEW"));
    }

    @Test
    @DisplayName("HOST calling UserService.getAllUsers throws AccessDeniedException")
    void testHostCannotManageUsers() {
        UserService userService = new UserService(null, null, AuthContext.fixed(hostUser));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, userService::getAllUsers);
        assertTrue(ex.getMessage().contains("USER_MANAGE"));
    }

    @Test
    @DisplayName("Unauthenticated context (null user) throws AccessDeniedException")
    void testUnauthenticatedAccessDenied() {
        TableService tableService = new TableService(null, null, AuthContext.fixed(null));
        RestaurantTable table = new RestaurantTable("T01", 4, "Indoor", TableStatus.AVAILABLE);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> tableService.createTable(table));
        assertTrue(ex.getMessage().contains("Authentication required"));
    }

    @Test
    @DisplayName("AuthContext.require succeeds when user possesses the required permission")
    void testAuthorizedUserSucceeds() {
        AuthContext managerContext = AuthContext.fixed(managerUser);
        assertDoesNotThrow(() -> managerContext.require(Permission.TABLE_MANAGE));
        assertDoesNotThrow(() -> managerContext.require(Permission.REPORT_VIEW));
        assertDoesNotThrow(() -> managerContext.require(Permission.USER_MANAGE));
    }
}
