package exception;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Thrown when attempting to book a table that is already reserved for the requested date and time slot.
 */
public class BookingConflictException extends AppException {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public BookingConflictException(String message) {
        super(message);
    }

    public BookingConflictException(String tableNumber, LocalDate date, String slotLabel) {
        super(String.format("Table %s is already reserved for %s at %s.", 
                tableNumber, 
                date != null ? date.format(DATE_FMT) : "", 
                slotLabel));
    }
}
