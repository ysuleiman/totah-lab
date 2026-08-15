package totah.lab.prometheus.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Predicate;

/** Deterministic fingerprint of selected immutable source files. */
public final class SourceTreeFingerprint {

    private SourceTreeFingerprint() {
    }

    public static String calculate(Path sourceRoot, Predicate<Path> include) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(include, "include");
        Path root = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("source root is not a directory: " + sourceRoot);
        }
        MessageDigest digest = sha256Digest();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(include)
                    .sorted((left, right) -> root.relativize(left).toString()
                            .compareTo(root.relativize(right).toString()))
                    .toList()) {
                digest.update(root.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (InputStream input = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        digest.update(buffer, 0, read);
                    }
                }
                digest.update((byte) '\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String calculateAll(Path sourceRoot) throws IOException {
        return calculate(sourceRoot, ignored -> true);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
