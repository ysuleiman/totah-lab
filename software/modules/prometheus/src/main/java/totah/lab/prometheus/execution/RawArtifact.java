package totah.lab.prometheus.execution;

import java.util.Objects;

/**
 * One raw output artifact produced by an executor: where it lives relative to
 * the calculation's working directory, its SHA-256 checksum, and its class
 * (e.g. "log", "geometry", "result_json", "hessian").
 */
public record RawArtifact(
        String relativePath,
        String sha256,
        String artifactClass) {

    public RawArtifact {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must be non-blank");
        }
        Objects.requireNonNull(sha256, "sha256");
        if (sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 must be non-blank");
        }
        Objects.requireNonNull(artifactClass, "artifactClass");
        if (artifactClass.isBlank()) {
            throw new IllegalArgumentException("artifactClass must be non-blank");
        }
    }
}
