package exception;

/**
 * Thrown when attempting to delete an entity (customer or table) that is referenced by active or historical reservations.
 */
public class ReferenceInUseException extends AppException {
    public ReferenceInUseException(String message) {
        super(message);
    }

    public ReferenceInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
