package service;

import exception.ValidationException;
import model.AppUser;
import model.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dao.CustomerDAO;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CustomerServiceTest {

    private CustomerService customerService;
    private Map<Integer, Customer> inMemoryDb;

    @BeforeEach
    void setUp() {
        inMemoryDb = new HashMap<>();

        // Fake DAO subclass avoiding external DB connection
        CustomerDAO fakeDao = new CustomerDAO() {
            @Override
            public Customer getCustomerById(int customerId) {
                return inMemoryDb.get(customerId);
            }

            @Override
            public void update(Customer customer) {
                inMemoryDb.put(customer.getCustomerId(), customer);
            }

            @Override
            public boolean existsByPhone(String phone, int excludeCustomerId) {
                return inMemoryDb.values().stream()
                        .filter(c -> c.getCustomerId() != excludeCustomerId)
                        .anyMatch(c -> phone.equals(c.getPhone()));
            }
        };

        AppUser manager = new AppUser("manager", "Manager Staff", security.Role.MANAGER, "hash", "salt", 600000);
        customerService = new CustomerService(fakeDao, null, security.AuthContext.fixed(manager));
    }

    @Test
    @DisplayName("getCustomerTags and setCustomerTags update customer natively")
    void testCustomerTagsRoundtrip() {
        Customer c = new Customer(101, "Alice Walker", "9876543210", "alice@example.com");
        inMemoryDb.put(101, c);

        assertEquals("", customerService.getCustomerTags(101));

        customerService.setCustomerTags(101, "VIP, Window Seat");
        assertEquals("VIP, Window Seat", customerService.getCustomerTags(101));
        assertEquals("VIP, Window Seat", inMemoryDb.get(101).getTags());
    }

    @Test
    @DisplayName("getCustomerNotes and setCustomerNotes handle null and empty strings safely without throwing")
    void testCustomerNotesSafety() {
        Customer c = new Customer(102, "Bob Smith", "9876543211", "bob@example.com");
        inMemoryDb.put(102, c);

        // Initially null -> returns empty string
        assertEquals("", customerService.getCustomerNotes(102));

        // Save empty string -> does not crash or throw ORA-01400
        assertDoesNotThrow(() -> customerService.setCustomerNotes(102, ""));
        assertEquals("", customerService.getCustomerNotes(102));

        // Save real notes
        customerService.setCustomerNotes(102, "Allergic to shellfish. Prefers booth.");
        assertEquals("Allergic to shellfish. Prefers booth.", customerService.getCustomerNotes(102));

        // Clear notes back to null
        assertDoesNotThrow(() -> customerService.setCustomerNotes(102, null));
        assertEquals("", customerService.getCustomerNotes(102));
    }

    @Test
    @DisplayName("updateCustomer validates required fields before updating")
    void testUpdateCustomerValidation() {
        Customer c = new Customer(103, "Charlie Davis", "9876543212", "charlie@example.com");
        inMemoryDb.put(103, c);

        // Blank name should fail
        c.setName("");
        assertThrows(ValidationException.class, () -> customerService.updateCustomer(c));

        // Invalid phone should fail
        c.setName("Charlie");
        c.setPhone("123");
        assertThrows(ValidationException.class, () -> customerService.updateCustomer(c));

        // Valid update should pass
        c.setPhone("9876543212");
        c.setNotes("Quiet table");
        c.setTags("VIP");
        assertDoesNotThrow(() -> customerService.updateCustomer(c));
        assertEquals("Quiet table", inMemoryDb.get(103).getNotes());
        assertEquals("VIP", inMemoryDb.get(103).getTags());
    }

    @Test
    @DisplayName("getCustomerTags and notes return empty string for non-existent customer")
    void testNonExistentCustomer() {
        assertEquals("", customerService.getCustomerTags(999));
        assertEquals("", customerService.getCustomerNotes(999));
        assertDoesNotThrow(() -> customerService.setCustomerTags(999, "VIP"));
        assertDoesNotThrow(() -> customerService.setCustomerNotes(999, "Notes"));
    }
}
