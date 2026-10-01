package util;

import exception.ValidationException;
import model.Customer;
import model.RestaurantTable;
import model.TableStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pure static validation utility for customer and table data models.
 * Collects all field-level validation errors into a single ValidationException.
 */
public class ValidationUtil {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z\\s.'\\-]{2,100}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern TABLE_NUM_PATTERN = Pattern.compile("^[A-Za-z0-9\\-]{1,10}$");

    /**
     * Normalizes phone number by removing spaces, hyphens, and leading "+91" or "0".
     */
    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        String trimmed = phone.trim().replaceAll("[\\s\\-()]", "");
        if (trimmed.startsWith("+91")) {
            trimmed = trimmed.substring(3);
        } else if (trimmed.startsWith("91") && trimmed.length() == 12) {
            trimmed = trimmed.substring(2);
        } else if (trimmed.startsWith("0") && trimmed.length() == 11) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    /**
     * Validates a Customer entity. Throws ValidationException if any rule fails.
     */
    public static void validateCustomer(Customer customer) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (customer == null) {
            throw new ValidationException("Customer details cannot be empty.");
        }

        // Customer Name
        String name = customer.getName() != null ? customer.getName().trim() : "";
        if (name.isEmpty()) {
            errors.put("name", "Customer name is required.");
        } else if (!NAME_PATTERN.matcher(name).matches()) {
            errors.put("name", "Name must be 2–100 characters containing letters, spaces, or .'-");
        }

        // Phone Number
        String rawPhone = customer.getPhone();
        String normalizedPhone = normalizePhone(rawPhone);
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            errors.put("phone", "Phone number is required.");
        } else if (!PHONE_PATTERN.matcher(normalizedPhone).matches()) {
            errors.put("phone", "Phone must be a valid 10-digit Indian mobile number (starts with 6–9).");
        }

        // Email (Optional)
        String email = customer.getEmail() != null ? customer.getEmail().trim() : "";
        if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
            errors.put("email", "Email format is invalid (e.g. name@example.com).");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Validates a RestaurantTable entity. Throws ValidationException if any rule fails.
     */
    public static void validateTable(RestaurantTable table) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (table == null) {
            throw new ValidationException("Table details cannot be empty.");
        }

        // Table Number
        String tableNumber = table.getTableNumber() != null ? table.getTableNumber().trim() : "";
        if (tableNumber.isEmpty()) {
            errors.put("tableNumber", "Table number is required.");
        } else if (!TABLE_NUM_PATTERN.matcher(tableNumber).matches()) {
            errors.put("tableNumber", "Table number must be 1–10 alphanumeric characters (e.g., T01, T-12).");
        }

        // Capacity
        int capacity = table.getCapacity();
        if (capacity < 1 || capacity > 20) {
            errors.put("capacity", "Table capacity must be between 1 and 20 guests.");
        }

        // Location
        String location = table.getLocation() != null ? table.getLocation().trim() : "";
        if (location.isEmpty()) {
            errors.put("location", "Location is required (e.g., Indoor, Window, Outdoor, Hall).");
        } else if (location.length() > 50) {
            errors.put("location", "Location must not exceed 50 characters.");
        }

        // Status
        if (table.getStatus() == null) {
            errors.put("status", "Table status is required.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Validates reservation request inputs.
     */
    public static void validateReservation(int customerId, LocalDate date, model.TimeSlot slot, int guests, RestaurantTable table) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (customerId <= 0) {
            errors.put("customer", "Please select a valid customer.");
        }

        if (date == null) {
            errors.put("date", "Reservation date is required.");
        } else if (date.isBefore(LocalDate.now())) {
            errors.put("date", "Reservation date cannot be in the past.");
        }

        if (slot == null) {
            errors.put("slot", "Please select a time slot.");
        } else if (date != null && date.isEqual(LocalDate.now())) {
            if (!slot.getStartTime().isAfter(java.time.LocalTime.now())) {
                errors.put("slot", "Selected time slot for today has already passed or started.");
            }
        }

        if (guests < 1) {
            errors.put("guests", "Guest count must be at least 1.");
        } else if (guests > 20) {
            errors.put("guests", "Single party size cannot exceed 20 guests.");
        }

        if (table != null) {
            if (table.getStatus() != TableStatus.AVAILABLE) {
                errors.put("table", "Selected table is currently not available.");
            } else if (guests > table.getCapacity()) {
                errors.put("table", "Guest count (" + guests + ") exceeds table capacity (" + table.getCapacity() + ").");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}

