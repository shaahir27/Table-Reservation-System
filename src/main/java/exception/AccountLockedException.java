package exception;

public class AccountLockedException extends AuthenticationException {
    private final long secondsRemaining;

    public AccountLockedException(long secondsRemaining) {
        super(buildMessage(secondsRemaining));
        this.secondsRemaining = secondsRemaining;
    }

    public AccountLockedException(String message, long secondsRemaining) {
        super(message);
        this.secondsRemaining = secondsRemaining;
    }

    public long getSecondsRemaining() {
        return secondsRemaining;
    }

    private static String buildMessage(long seconds) {
        if (seconds <= 0) {
            return "Account is temporarily locked. Please try again shortly.";
        }
        long minutes = seconds / 60;
        long remSec = seconds % 60;
        if (minutes > 0) {
            return String.format("Account temporarily locked. Try again in %d min %d sec.", minutes, remSec);
        }
        return String.format("Account temporarily locked. Try again in %d seconds.", remSec);
    }
}
