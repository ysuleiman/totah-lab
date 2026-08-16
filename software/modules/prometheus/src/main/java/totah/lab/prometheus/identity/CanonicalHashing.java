package totah.lab.prometheus.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * Shared canonical-serialization helpers for Prometheus identity hashing.
 * Values are length-prefixed before hashing so embedded delimiters cannot make
 * distinct scientific inputs serialize identically.
 */
public final class CanonicalHashing {

    private CanonicalHashing() {
    }

    public static String format(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("canonical floating-point values must be finite");
        }
        return Double.toString(value == 0.0d ? 0.0d : value);
    }

    /** Injective serialization of an ordered sequence of strings. */
    public static String sequence(List<String> values) {
        Objects.requireNonNull(values, "values");
        StringBuilder canonical = new StringBuilder().append(values.size()).append(':');
        for (String value : values) {
            byte[] bytes = Objects.requireNonNull(value, "values must not contain null")
                    .getBytes(StandardCharsets.UTF_8);
            canonical.append(bytes.length).append(':').append(value);
        }
        return canonical.toString();
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
