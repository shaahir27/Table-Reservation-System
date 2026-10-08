package security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    @DisplayName("PasswordHasher correctly verifies valid password and rejects incorrect password")
    void testHashAndVerify() {
        char[] password = "SecretMasterPassword2026!".toCharArray();
        PasswordHasher.Hashed hashed = PasswordHasher.hash(password);

        assertNotNull(hashed);
        assertNotNull(hashed.hashB64());
        assertNotNull(hashed.saltB64());
        assertEquals(PasswordHasher.CURRENT_ITERATIONS, hashed.iterations());

        // Verify correct password
        assertTrue(PasswordHasher.verify(password, hashed.hashB64(), hashed.saltB64(), hashed.iterations()));

        // Verify wrong password
        assertFalse(PasswordHasher.verify("WrongPassword123".toCharArray(), hashed.hashB64(), hashed.saltB64(), hashed.iterations()));

        // Verify null / empty inputs
        assertFalse(PasswordHasher.verify(null, hashed.hashB64(), hashed.saltB64(), hashed.iterations()));
        assertFalse(PasswordHasher.verify(password, null, hashed.saltB64(), hashed.iterations()));
        assertFalse(PasswordHasher.verify(password, hashed.hashB64(), null, hashed.iterations()));
    }

    @Test
    @DisplayName("needsRehash identifies iteration upgrades")
    void testNeedsRehash() {
        assertTrue(PasswordHasher.needsRehash(100_000));
        assertTrue(PasswordHasher.needsRehash(300_000));
        assertFalse(PasswordHasher.needsRehash(600_000));
    }

    @Test
    @DisplayName("dummyDerivation runs without error for unknown user timing mitigation")
    void testDummyDerivation() {
        assertDoesNotThrow(() -> PasswordHasher.dummyDerivation("test".toCharArray()));
        assertDoesNotThrow(() -> PasswordHasher.dummyDerivation(null));
    }
}
