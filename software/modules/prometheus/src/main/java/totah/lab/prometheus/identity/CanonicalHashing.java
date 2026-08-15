package totah.lab.prometheus.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Shared canonical-serialization helpers for Prometheus identity hashing.
 * Numbers are rendered with {@code String.format(Locale.ROOT, "%.8f", v)}
 * and lines are joined with {@code '\n'} before hashing with SHA-256.
 */
public final class CanonicalHashing {

    private CanonicalHashing() {
    }

    public static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
