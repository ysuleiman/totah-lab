package totah.lab.web.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.api.StructureReader;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.GZIPInputStream;

@Service
public class StructureArtifactService {

    private final Path artifactRoot;
    private final Path externalRoot;
    private final StructureReader structureReader =
            new PdbReader();

    private final ConcurrentMap<Long, CachedStructure> cache =
            new ConcurrentHashMap<>();

    public StructureArtifactService(String artifactRoot) {
        this(artifactRoot, "");
    }

    @Autowired
    public StructureArtifactService(
            @Value("${totah.artifacts.root}") String artifactRoot,
            @Value("${totah.artifacts.external-root:}")
                    String externalRoot) {
        Objects.requireNonNull(artifactRoot, "artifactRoot");
        if (artifactRoot.isBlank()) {
            throw new IllegalArgumentException(
                    "Artifact storage root must not be blank");
        }
        this.artifactRoot = Path.of(artifactRoot)
                .toAbsolutePath()
                .normalize();
        this.externalRoot = externalRoot == null || externalRoot.isBlank()
                ? null
                : Path.of(externalRoot).toAbsolutePath().normalize();
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

        Structure loaded = readStructure(path);
        cache.put(artifactId, new CachedStructure(path, loaded));
        return loaded;
    }

    private Structure readStructure(Path path) throws IOException {
        if (!path.getFileName().toString().endsWith(".gz")) {
            return structureReader.read(path);
        }

        Path decompressed = Files.createTempFile(
                "structure-artifact-", ".pdb");
        try {
            try (InputStream input = new GZIPInputStream(
                    Files.newInputStream(path));
                 OutputStream output = Files.newOutputStream(
                         decompressed)) {
                input.transferTo(output);
            }
            return structureReader.read(decompressed);
        } finally {
            Files.deleteIfExists(decompressed);
        }
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
            Path absolute = relative.toAbsolutePath().normalize();
            if (externalRoot == null
                    || !absolute.startsWith(externalRoot)) {
                throw new IOException(
                        "Artifact storage location is outside the"
                                + " allowed roots: "
                                + storageLocation);
            }
            return absolute;
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
