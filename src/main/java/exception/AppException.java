package exception;

/**
 * Base application runtime exception for all custom domain exceptions in RTRS.
 */
public class AppException extends RuntimeException {
    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
