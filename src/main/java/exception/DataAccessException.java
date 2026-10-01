package exception;

/**
 * Generic runtime data access exception wrapping unexpected SQL or database errors.
 */
public class DataAccessException extends AppException {
    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
