package dev.bwdesigngroup.flint.common.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Shared authentication primitives used by both the Designer WebSocket bridge and the Gateway HTTP
 * transport.
 */
public final class AuthUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String HEX_CHARS = "0123456789abcdef";

    private AuthUtil() {
        // Prevent instantiation
    }

    /**
     * Constant-time string comparison to prevent timing attacks. Returns false if either argument
     * is null or the lengths differ.
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    /**
     * Generates a cryptographically random lowercase-hex secret of the given length (in
     * characters), sourced from {@link SecureRandom}. 32 characters ~= 128 bits of entropy.
     */
    public static String generateHexSecret(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(HEX_CHARS.charAt(SECURE_RANDOM.nextInt(HEX_CHARS.length())));
        }
        return sb.toString();
    }
}
