package totah.lab.docking.importer;

import java.nio.file.Path;
import java.util.Objects;

public record ArchivedArtifact(
        Path path,
        String sha256,
        long size
) {
    public ArchivedArtifact {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(sha256, "sha256");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }
}
