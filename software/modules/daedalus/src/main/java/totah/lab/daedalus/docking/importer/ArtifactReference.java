package totah.lab.daedalus.docking.importer;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public record ArtifactReference(
        UUID sourceId,
        URI sourceUri,
        String format,
        Path resolvedPath
) {
    public ArtifactReference {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(sourceUri, "sourceUri");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(resolvedPath, "resolvedPath");
    }
}
