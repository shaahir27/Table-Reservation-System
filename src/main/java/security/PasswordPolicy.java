package security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Modern password policy based on NIST SP 800-63B guidelines:
 * - Minimum 10 characters, maximum 128 characters.
 * - Password must not be identical or too similar to the username.
 * - Password must not appear in the common passwords blocklist.
 * - Evaluates strength score (0-4) for UI visual feedback.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON_PASSWORDS = new HashSet<>();

    static {
        String[] common = {
                "password", "password123", "1234567890", "qwertyuiop", "admin12345",
                "restaurant", "restaurant1", "welcome123", "letmein123", "iloveyou123",
                "changeme123", "tablemaster", "manager123", "rtrspassword", "oracle1234",
                "abc1234567", "monkey1234", "dragon1234", "master1234", "football12",
                "sunshine12", "princess12", "shadow1234", "superman12", "batman1234",
                "trustno111", "keyboard12", "starwars12", "liverpool1", "arsenal123",
                "chelsea123", "barcelona1", "realmadrid1", "passw0rd123", "secret1234",
                "login12345", "test123456", "guest12345", "system1234", "root123456"
        };
        for (String p : common) {
            COMMON_PASSWORDS.add(p.toLowerCase());
        }
    }

    public record PolicyResult(boolean valid, List<String> errors, int strengthScore) {
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }
    }

    private PasswordPolicy() {
    }

    public static PolicyResult validate(char[] password, String username) {
        if (password == null) {
            return new PolicyResult(false, List.of("Password cannot be null."), 0);
        }

        List<String> errors = new ArrayList<>();
        int length = password.length;

        if (length < MIN_LENGTH) {
            errors.add("Password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (length > MAX_LENGTH) {
            errors.add("Password must not exceed " + MAX_LENGTH + " characters.");
        }

        String pwdStr = new String(password);
        String lowerPwd = pwdStr.toLowerCase();

        if (username != null && !username.isBlank()) {
            String cleanUser = username.trim().toLowerCase();
            if (lowerPwd.equals(cleanUser) || (cleanUser.length() >= 3 && lowerPwd.contains(cleanUser))) {
                errors.add("Password must not contain or match your username.");
            }
        }

        if (COMMON_PASSWORDS.contains(lowerPwd)) {
            errors.add("Password is too common or easily guessable.");
        }

        int score = calculateStrengthScore(password);

        return new PolicyResult(errors.isEmpty(), Collections.unmodifiableList(errors), score);
    }

    public static int calculateStrengthScore(char[] password) {
        if (password == null || password.length == 0) return 0;
        int score = 0;
        if (password.length >= 10) score++;
        if (password.length >= 14) score++;

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }

        int variety = 0;
        if (hasUpper) variety++;
        if (hasLower) variety++;
        if (hasDigit) variety++;
        if (hasSpecial) variety++;

        if (variety >= 3) score++;
        if (variety >= 4 && password.length >= 12) score++;

        return Math.min(4, Math.max(0, score));
    }
}
