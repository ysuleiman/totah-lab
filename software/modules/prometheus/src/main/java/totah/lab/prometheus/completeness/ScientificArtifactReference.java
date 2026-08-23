package totah.lab.prometheus.completeness;

import java.nio.file.Path;
import java.util.Objects;

/** A bundle-relative artifact and its independently recorded SHA-256. */
public record ScientificArtifactReference(Path path, String sha256) {
    public ScientificArtifactReference {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(sha256, "sha256");
        if (path.isAbsolute() || path.normalize().startsWith("..")) {
            throw new IllegalArgumentException("scientific artifact path must stay inside its bundle");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid lowercase SHA-256");
        }
    }
}
