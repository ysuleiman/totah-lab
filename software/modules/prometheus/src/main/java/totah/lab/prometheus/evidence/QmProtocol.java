package totah.lab.prometheus.evidence;

import java.util.Objects;

/**
 * The QM (or force-field) protocol under which a calculation was run.
 *
 * <p>{@code method}, {@code basis} and {@code software} must be non-blank;
 * {@code dispersion} and {@code environment} may be {@code "none"} when the
 * calculation uses neither. For classical evidence this record carries force-field
 * identification instead: {@code method} = force-field id (e.g. "GAFF2"),
 * {@code basis} = "none", {@code software} e.g. "AmberTools".
 */
public record QmProtocol(
        String method,
        String basis,
        String dispersion,
        String environment,
        boolean counterpoise,
        String software,
        String softwareVersion) {

    public QmProtocol {
        requireNonBlank(method, "method");
        requireNonBlank(basis, "basis");
        requireNonBlank(software, "software");
        Objects.requireNonNull(dispersion, "dispersion");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(softwareVersion, "softwareVersion");
    }

    /**
     * Canonical string form of every protocol field. Delimiters inside fields
     * are escaped, preserving the established readable key for ordinary values.
     * Two protocols with the same key are considered the same protocol.
     */
    public String protocolKey() {
        return String.join("|",
                escape(method),
                escape(basis),
                escape(dispersion),
                escape(environment),
                Boolean.toString(counterpoise),
                escape(software),
                escape(softwareVersion));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\|");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
