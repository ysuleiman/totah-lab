package totah.lab.daedalus.docking.importer;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves Chemflow's local://artifact-storage/... URIs against the configured
 * artifact store root without assuming where Chemflow is installed.
 */
public final class LocalArtifactUriResolver {

    private static final String SCHEME = "local";

    private final Path artifactRoot;

    public LocalArtifactUriResolver(Path artifactRoot) {
        this.artifactRoot = Objects.requireNonNull(
                artifactRoot,
                "artifactRoot"
        ).toAbsolutePath().normalize();
    }

    public Path resolve(URI uri) {
        Objects.requireNonNull(uri, "uri");
        if (!SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "Unsupported artifact URI scheme: " + uri
            );
        }
        if (!"artifact-storage".equals(uri.getHost())) {
            throw new IllegalArgumentException(
                    "Unsupported local artifact authority: " + uri
            );
        }

        String relative = uri.getPath();
        if (relative == null || relative.isBlank() || "/".equals(relative)) {
            throw new IllegalArgumentException(
                    "Artifact URI has no relative path: " + uri
            );
        }

        Path resolved = artifactRoot.resolve(relative.substring(1)).normalize();
        if (!resolved.startsWith(artifactRoot)) {
            throw new IllegalArgumentException(
                    "Artifact URI escapes configured root: " + uri
            );
        }
        return resolved;
    }
}
