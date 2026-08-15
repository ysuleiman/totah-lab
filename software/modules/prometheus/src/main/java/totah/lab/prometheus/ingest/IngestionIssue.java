package totah.lab.prometheus.ingest;

import java.util.Objects;

/**
 * A problem or noteworthy condition found while ingesting: unusable or
 * incomplete evidence (empty logs, format-rejected inputs, checksum mismatches,
 * parse failures, missing artifacts). Issues are recorded, never thrown away
 * silently. Severity is one of {@code "note"}, {@code "warning"},
 * {@code "error"}.
 */
public record IngestionIssue(
        String path,
        String severity,
        String message) {

    public IngestionIssue {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    public static IngestionIssue note(String path, String message) {
        return new IngestionIssue(path, "note", message);
    }

    public static IngestionIssue warning(String path, String message) {
        return new IngestionIssue(path, "warning", message);
    }

    public static IngestionIssue error(String path, String message) {
        return new IngestionIssue(path, "error", message);
    }
}
