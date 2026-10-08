package util;

import exception.ValidationException;
import model.Customer;
import model.RestaurantTable;
import model.TableStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilTest {

    // --- Phone Normalization Tests ---

    @Test
    @DisplayName("Normalizes Indian mobile numbers with +91, 0, spaces, and hyphens")
    void testNormalizePhone() {
        assertEquals("9876543210", ValidationUtil.normalizePhone("+91 98765-43210"));
        assertEquals("9876543210", ValidationUtil.normalizePhone("09876543210"));
        assertEquals("9876543210", ValidationUtil.normalizePhone("919876543210"));
        assertEquals("9876543210", ValidationUtil.normalizePhone("98765 43210"));
        assertEquals("9876543210", ValidationUtil.normalizePhone("9876543210"));
    }

    // --- Customer Validation Tests ---

    @Test
    @DisplayName("Valid customer passes validation without errors")
    void testValidateCustomer_Valid() {
        Customer customer = new Customer("Rahul Kumar", "9876543210", "rahul.kumar@example.com");
        assertDoesNotThrow(() -> ValidationUtil.validateCustomer(customer));
    }

    @Test
    @DisplayName("Valid customer with optional empty email passes validation")
    void testValidateCustomer_NoEmail() {
        Customer customer = new Customer("Priya Patel", "+91 9812345678", "");
        assertDoesNotThrow(() -> ValidationUtil.validateCustomer(customer));

        Customer customerNullEmail = new Customer("Priya Patel", "9812345678", null);
        assertDoesNotThrow(() -> ValidationUtil.validateCustomer(customerNullEmail));
    }

    @Test
    @DisplayName("Valid customer with Unicode/accented characters in name passes validation")
    void testValidateCustomer_UnicodeNames() {
        assertDoesNotThrow(() -> ValidationUtil.validateCustomer(new Customer("José Silva", "9876543210", "")));
        assertDoesNotThrow(() -> ValidationUtil.validateCustomer(new Customer("Müller Hans", "9876543210", "")));
        assertDoesNotThrow(() -> ValidationUtil.validateCustomer(new Customer("François Dupont", "9876543210", "")));
        assertDoesNotThrow(() -> ValidationUtil.validateCustomer(new Customer("Renée O'Connor", "9876543210", "")));
        assertDoesNotThrow(() -> ValidationUtil.validateCustomer(new Customer("Jean-Luc Picard", "9876543210", "")));
    }

    @Test
    @DisplayName("Customer validation fails on invalid name, phone, and email")
    void testValidateCustomer_InvalidFields() {
        Customer customer = new Customer("A", "12345", "invalid-email");
        ValidationException ex = assertThrows(ValidationException.class, () -> ValidationUtil.validateCustomer(customer));

        assertTrue(ex.getFieldErrors().containsKey("name"));
        assertTrue(ex.getFieldErrors().containsKey("phone"));
        assertTrue(ex.getFieldErrors().containsKey("email"));
    }

    @Test
    @DisplayName("Customer phone starting with invalid digit (< 6) is rejected")
    void testValidateCustomer_InvalidPhoneStartDigit() {
        Customer customer = new Customer("Meena Iyer", "5876543210", "meena@example.com");
        ValidationException ex = assertThrows(ValidationException.class, () -> ValidationUtil.validateCustomer(customer));

        assertTrue(ex.getFieldErrors().containsKey("phone"));
    }

    @Test
    @DisplayName("Null customer throws ValidationException")
    void testValidateCustomer_Null() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validateCustomer(null));
    }

    // --- Table Validation Tests ---

    @Test
    @DisplayName("Valid restaurant table passes validation")
    void testValidateTable_Valid() {
        RestaurantTable table = new RestaurantTable("T01", 4, "Indoor", TableStatus.AVAILABLE);
        assertDoesNotThrow(() -> ValidationUtil.validateTable(table));
    }

    @Test
    @DisplayName("Table validation fails when capacity is out of bounds (< 1 or > 20)")
    void testValidateTable_InvalidCapacity() {
        RestaurantTable tableZero = new RestaurantTable("T01", 0, "Indoor", TableStatus.AVAILABLE);
        ValidationException ex1 = assertThrows(ValidationException.class, () -> ValidationUtil.validateTable(tableZero));
        assertTrue(ex1.getFieldErrors().containsKey("capacity"));

        RestaurantTable tableTooLarge = new RestaurantTable("T01", 25, "Indoor", TableStatus.AVAILABLE);
        ValidationException ex2 = assertThrows(ValidationException.class, () -> ValidationUtil.validateTable(tableTooLarge));
        assertTrue(ex2.getFieldErrors().containsKey("capacity"));
    }

    @Test
    @DisplayName("Table validation fails when table number is blank or contains illegal characters")
    void testValidateTable_InvalidTableNumber() {
        RestaurantTable tableBlank = new RestaurantTable("", 4, "Indoor", TableStatus.AVAILABLE);
        ValidationException ex1 = assertThrows(ValidationException.class, () -> ValidationUtil.validateTable(tableBlank));
        assertTrue(ex1.getFieldErrors().containsKey("tableNumber"));

        RestaurantTable tableIllegal = new RestaurantTable("T#01@!", 4, "Indoor", TableStatus.AVAILABLE);
        ValidationException ex2 = assertThrows(ValidationException.class, () -> ValidationUtil.validateTable(tableIllegal));
        assertTrue(ex2.getFieldErrors().containsKey("tableNumber"));
    }

    @Test
    @DisplayName("Table validation fails when location is blank or status is null")
    void testValidateTable_InvalidLocationAndStatus() {
        RestaurantTable table = new RestaurantTable("T02", 6, "", null);
        ValidationException ex = assertThrows(ValidationException.class, () -> ValidationUtil.validateTable(table));
        assertTrue(ex.getFieldErrors().containsKey("location"));
        assertTrue(ex.getFieldErrors().containsKey("status"));
    }

    @Test
    @DisplayName("Null table throws ValidationException")
    void testValidateTable_Null() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validateTable(null));
    }
}
