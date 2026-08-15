package totah.lab.prometheus.recovery;

import java.util.Objects;

/** Exact artifact and record location supporting one recovered field. */
public record FieldSourceProvenance(
        String sourcePath,
        String sha256,
        String locator,
        String extractionMethod) {

    public FieldSourceProvenance {
        sourcePath = requireNonBlank(sourcePath, "sourcePath");
        sha256 = requireNonBlank(sha256, "sha256");
        locator = requireNonBlank(locator, "locator");
        extractionMethod = requireNonBlank(extractionMethod, "extractionMethod");
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
