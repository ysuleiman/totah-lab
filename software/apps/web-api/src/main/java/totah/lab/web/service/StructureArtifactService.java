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
import java.nio.charset.StandardCharsets;
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
        this.artifactRoot = resolveArtifactRoot(artifactRoot);
        this.externalRoot = externalRoot == null || externalRoot.isBlank()
                ? null
                : Path.of(externalRoot).toAbsolutePath().normalize();
    }

    private static Path resolveArtifactRoot(String configuredRoot) {
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            Path configured = Path.of(configuredRoot)
                    .toAbsolutePath()
                    .normalize();
            Path repositoryResources = configured.resolve(
                    "resources/shared-resources/src/main/resources");
            return Files.isDirectory(repositoryResources)
                    ? repositoryResources
                    : configured;
        }

        Path discovered = discoverArtifactRoot(Path.of(
                System.getProperty("user.dir")));
        if (discovered == null) {
            throw new IllegalArgumentException(
                    "Artifact storage root is not configured and could not "
                            + "be discovered from the working directory");
        }
        return discovered;
    }

    static Path discoverArtifactRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(
                    "resources/shared-resources/src/main/resources");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
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

    public String readText(String storageLocation) throws IOException {
        Path path = resolveStorageLocation(storageLocation);
        try (InputStream fileInput = Files.newInputStream(path);
             InputStream input = path.getFileName().toString().endsWith(".gz")
                     ? new GZIPInputStream(fileInput)
                     : fileInput) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String readExternalText(Path relativePath) throws IOException {
        if (externalRoot == null) {
            throw new IOException("External artifact root is not configured");
        }
        if (relativePath.isAbsolute()) {
            throw new IOException("External artifact path must be relative");
        }
        Path resolved = externalRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(externalRoot)) {
            throw new IOException("External artifact path escapes configured root");
        }
        return readText(resolved.toString());
    }

    public String readClasspathText(String resourcePath) throws IOException {
        try (InputStream input = StructureArtifactService.class
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException(
                        "Classpath artifact was not found: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
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
