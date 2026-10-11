package security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    @DisplayName("Password shorter than 10 characters is rejected")
    void testPasswordTooShort() {
        PasswordPolicy.PolicyResult res = PasswordPolicy.validate("short9!".toCharArray(), "manager");
        assertFalse(res.valid());
        assertTrue(res.getFirstError().contains("at least 10 characters"));
    }

    @Test
    @DisplayName("Password longer than 128 characters is rejected")
    void testPasswordTooLong() {
        String longPwd = "A".repeat(129);
        PasswordPolicy.PolicyResult res = PasswordPolicy.validate(longPwd.toCharArray(), "manager");
        assertFalse(res.valid());
        assertTrue(res.getFirstError().contains("not exceed 128 characters"));
    }

    @Test
    @DisplayName("Password containing or matching username is rejected")
    void testPasswordContainsUsername() {
        PasswordPolicy.PolicyResult res1 = PasswordPolicy.validate("manager12345".toCharArray(), "manager");
        assertFalse(res1.valid());
        assertTrue(res1.getFirstError().contains("username"));

        PasswordPolicy.PolicyResult res2 = PasswordPolicy.validate("adminsecretpass".toCharArray(), "admin");
        assertFalse(res2.valid());
        assertTrue(res2.getFirstError().contains("username"));
    }

    @Test
    @DisplayName("Common dictionary passwords are rejected")
    void testCommonPasswordsBlocked() {
        PasswordPolicy.PolicyResult res = PasswordPolicy.validate("password123".toCharArray(), "john");
        assertFalse(res.valid());
        assertTrue(res.getFirstError().contains("too common"));
    }

    @Test
    @DisplayName("Strong unique password passes policy validation")
    void testValidStrongPassword() {
        PasswordPolicy.PolicyResult res = PasswordPolicy.validate("Correct-Horse-Battery-2026!".toCharArray(), "john");
        assertTrue(res.valid());
        assertTrue(res.errors().isEmpty());
        assertTrue(res.strengthScore() >= 3);
    }

    @Test
    @DisplayName("Strength calculation reflects entropy and character classes")
    void testStrengthCalculation() {
        assertEquals(0, PasswordPolicy.calculateStrengthScore("".toCharArray()));
        assertEquals(1, PasswordPolicy.calculateStrengthScore("alllowercase".toCharArray()));
        assertTrue(PasswordPolicy.calculateStrengthScore("MediumPass12".toCharArray()) >= 2);
        assertTrue(PasswordPolicy.calculateStrengthScore("Complex!P@ss2026".toCharArray()) >= 3);
    }
}
