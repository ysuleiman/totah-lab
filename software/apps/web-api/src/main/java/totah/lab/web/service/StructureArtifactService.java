package totah.lab.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.reader.BioJavaStructureReader;
import totah.lab.hermes.file.reader.StructureReader;


import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class StructureArtifactService {

    private final Path artifactRoot;
    private final StructureReader structureReader =
            new BioJavaStructureReader();

    private final ConcurrentMap<Long, CachedStructure> cache =
            new ConcurrentHashMap<>();

    public StructureArtifactService(
            @Value("${totah.artifacts.root}") String artifactRoot) {
        Objects.requireNonNull(artifactRoot, "artifactRoot");
        if (artifactRoot.isBlank()) {
            throw new IllegalArgumentException(
                    "Artifact storage root must not be blank");
        }
        this.artifactRoot = Path.of(artifactRoot)
                .toAbsolutePath()
                .normalize();
    }

    public Structure load(
            long artifactId,
            String storageLocation
    ) throws IOException {
        Path path = resolveStorageLocation(storageLocation);
        CachedStructure cached = cache.get(artifactId);
        if (cached != null && cached.path().equals(path)) {
            return cached.structure();
        }

        Structure loaded = structureReader.read(path);
        cache.put(artifactId, new CachedStructure(path, loaded));
        return loaded;
    }

    Path resolveStorageLocation(String storageLocation) throws IOException {
        if (storageLocation == null || storageLocation.isBlank()) {
            throw new IOException("Artifact storage location is required");
        }

        final Path relative;
        try {
            relative = Path.of(storageLocation);
        } catch (InvalidPathException exception) {
            throw new IOException(
                    "Invalid artifact storage location", exception);
        }
        if (relative.isAbsolute()) {
            throw new IOException(
                    "Artifact storage location must be relative: "
                            + storageLocation);
        }

        Path resolved = artifactRoot.resolve(relative).normalize();
        if (!resolved.startsWith(artifactRoot)) {
            throw new IOException(
                    "Artifact storage location escapes configured root: "
                            + storageLocation);
        }
        return resolved;
    }

    private record CachedStructure(
            Path path,
            Structure structure
    ) {
    }
}
