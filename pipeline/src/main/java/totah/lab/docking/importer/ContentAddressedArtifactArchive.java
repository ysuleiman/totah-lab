package totah.lab.docking.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Copies immutable artifacts into sha256/aa/bb/&lt;digest&gt;.&lt;format&gt;.
 * Re-importing identical content reuses the same file.
 */
public final class ContentAddressedArtifactArchive {

    private final Path archiveRoot;

    public ContentAddressedArtifactArchive(Path archiveRoot) {
        this.archiveRoot = Objects.requireNonNull(
                archiveRoot,
                "archiveRoot"
        ).toAbsolutePath().normalize();
    }

    public ArchivedArtifact archive(Path source, String format)
            throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(format, "format");

        Path normalizedSource = source.toAbsolutePath().normalize();
        String digest = sha256(normalizedSource);
        String safeFormat = validateFormat(format);
        Path destination = archiveRoot.resolve("sha256")
                .resolve(digest.substring(0, 2))
                .resolve(digest.substring(2, 4))
                .resolve(digest + "." + safeFormat);

        if (!Files.exists(destination)) {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(
                    destination.getParent(),
                    digest,
                    ".tmp"
            );
            try {
                Files.copy(
                        normalizedSource,
                        temporary,
                        StandardCopyOption.REPLACE_EXISTING
                );
                if (!digest.equals(sha256(temporary))) {
                    throw new IOException(
                            "Artifact checksum changed while copying: "
                                    + normalizedSource
                    );
                }
                try {
                    Files.move(
                            temporary,
                            destination,
                            StandardCopyOption.ATOMIC_MOVE
                    );
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // An identical concurrent import won the race.
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        return new ArchivedArtifact(
                destination,
                digest,
                Files.size(destination)
        );
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }

        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String validateFormat(String format) {
        String normalized = format.toLowerCase();
        if (!normalized.matches("[a-z0-9]+")) {
            throw new IllegalArgumentException(
                    "Unsafe artifact format: " + format
            );
        }
        return normalized;
    }
}
