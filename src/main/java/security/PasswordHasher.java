package security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Standard JDK PBKDF2-HMAC-SHA256 password hashing.
 * Uses 16-byte cryptographically secure salt, 256-bit derived key, and 600,000 iterations.
 * Performs constant-time comparison via MessageDigest.isEqual to prevent timing attacks.
 */
public final class PasswordHasher {
    public static final String ALGO = "PBKDF2WithHmacSHA256";
    public static final int SALT_BYTES = 16;
    public static final int KEY_BITS = 256;
    public static final int CURRENT_ITERATIONS = 600_000;

    private static final SecureRandom RNG = new SecureRandom();
    private static final byte[] DUMMY_SALT = new byte[SALT_BYTES];

    static {
        RNG.nextBytes(DUMMY_SALT);
    }

    public record Hashed(String hashB64, String saltB64, int iterations) {}

    private PasswordHasher() {
    }

    public static Hashed hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        byte[] dk = derive(password, salt, CURRENT_ITERATIONS, KEY_BITS);
        return new Hashed(b64(dk), b64(salt), CURRENT_ITERATIONS);
    }

    public static boolean verify(char[] password, String hashB64, String saltB64, int iterations) {
        if (password == null || hashB64 == null || saltB64 == null || iterations <= 0) {
            return false;
        }
        try {
            byte[] expected = Base64.getDecoder().decode(hashB64);
            byte[] salt = Base64.getDecoder().decode(saltB64);
            byte[] actual = derive(password, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean needsRehash(int storedIterations) {
        return storedIterations < CURRENT_ITERATIONS;
    }

    /**
     * Executes a dummy derivation of equivalent cost to prevent username enumeration timing attacks.
     */
    public static void dummyDerivation(char[] dummyPassword) {
        derive(dummyPassword != null ? dummyPassword : new char[]{'d','u','m','m','y'},
                DUMMY_SALT, CURRENT_ITERATIONS, KEY_BITS);
    }

    static byte[] derive(char[] pw, byte[] salt, int iter, int bits) {
        PBEKeySpec spec = new PBEKeySpec(pw, salt, iter, bits);
        try {
            return SecretKeyFactory.getInstance(ALGO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cryptographic provider error deriving PBKDF2 key", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }
}
