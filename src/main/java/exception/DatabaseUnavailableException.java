package exception;

/**
 * Thrown when the database is unreachable, times out, or credentials fail.
 */
public class DatabaseUnavailableException extends AppException {
    public DatabaseUnavailableException(String message) {
        super(message);
    }

    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
