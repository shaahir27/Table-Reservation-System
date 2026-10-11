package util;

import exception.AppException;
import exception.BookingConflictException;
import exception.DataAccessException;
import exception.DatabaseUnavailableException;
import exception.ReferenceInUseException;
import exception.ValidationException;

import java.sql.SQLException;

/**
 * Translates low-level Oracle SQLExceptions and error codes into friendly domain AppExceptions.
 */
public class OracleErrorMapper {

    public static AppException map(SQLException e, String context) {
        AppLog.error("SQLException in context [" + context + "]: " + e.getMessage() + " (ErrorCode: " + e.getErrorCode() + ")", e);
        int errorCode = e.getErrorCode();
        String message = e.getMessage() != null ? e.getMessage().toUpperCase() : "";

        switch (errorCode) {
            case 1: // ORA-00001: Unique constraint violated
                if (message.contains("UQ_ACTIVE_BOOKING")) {
                    return new BookingConflictException("The requested table is already reserved for this date and time slot.");
                }
                if (message.contains("UQ_ACTIVE_WAITLIST")) {
                    return new ValidationException("Customer already has an active waitlist entry for this date and time slot.");
                }
                if (message.contains("UQ_CUSTOMER_PHONE")) {
                    return new ValidationException("Phone number is already registered to another customer.");
                }
                if (message.contains("UQ_TABLE_NUMBER")) {
                    return new ValidationException("Table number already exists.");
                }
                return new ValidationException("A record with these unique details already exists.");

            case 2292: // ORA-02292: Integrity constraint violated - child record found
                return new ReferenceInUseException(
                        "Cannot delete record because it is referenced by existing reservations. Cancel active reservations or set status to INACTIVE instead."
                );

            case 20001: // Trigger rule: guest_count > capacity
                return new ValidationException("Guest count exceeds the selected table capacity.");

            case 20002: // Trigger rule: table is not AVAILABLE
                return new ValidationException("The selected table is currently not available for booking.");

            case 20003: // Trigger rule: illegal reservation status transition
                return new ValidationException("Illegal reservation status change: " + e.getMessage());

            case 20010: // Trigger rule: audit_log is append-only
                return new ValidationException("Audit log is append-only and cannot be updated or deleted.");

            case 30006: // ORA-30006: Resource busy; acquire with WAIT timeout expired
                return new BookingConflictException("The requested table is currently locked by another booking operation. Please retry in a few moments.");

            case 1017:  // ORA-01017: Invalid username/password
            case 1045:  // ORA-01045: User lacks CREATE SESSION privilege
            case 12170: // ORA-12170: Connect timeout
            case 12514: // ORA-12514: TNS listener cannot resolve service name
            case 12541: // ORA-12541: TNS: no listener
            case 17002: // Vendor error code for I/O Exception / connection refused
            case 28000: // ORA-28000: The account is locked
            case 28001: // ORA-28001: The password has expired
                return new DatabaseUnavailableException(
                        "Cannot connect to the Oracle database (" + (errorCode == 28000 ? "account is locked" : errorCode == 28001 ? "password has expired" : "listener/service unavailable") + "). Please verify Oracle service and configuration.",
                        e
                );

            default:
                return new DataAccessException("A database error occurred: " + e.getMessage(), e);
        }
    }
}
