package totah.lab.prometheus.ingest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Index over one {@code SHA256SUMS} file (lines of the form
 * {@code <sha256>  <relative path>}). Lookups tolerate entries whose recorded
 * path is anchored differently than the queried path (e.g. entries recorded
 * against a staging directory): exact match first, then suffix match on path
 * segments.
 */
public final class Sha256Index {

    private final Path baseDir;
    private final Map<String, String> hashByPath;

    private Sha256Index(Path baseDir, Map<String, String> hashByPath) {
        this.baseDir = baseDir;
        this.hashByPath = Map.copyOf(hashByPath);
    }

    /** Parses a {@code SHA256SUMS}-format file; the index is anchored at its parent directory. */
    public static Sha256Index parse(Path sha256SumsFile) throws IOException {
        Objects.requireNonNull(sha256SumsFile, "sha256SumsFile");
        Map<String, String> entries = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(sha256SumsFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                int split = indexOfWhitespaceRun(line);
                if (split < 0) {
                    continue;
                }
                String hash = line.substring(0, split).trim();
                String path = line.substring(split).trim();
                // coreutils may prefix the path with '*' (binary mode)
                if (path.startsWith("*")) {
                    path = path.substring(1);
                }
                if (!hash.isEmpty() && !path.isEmpty()) {
                    entries.put(normalize(path), hash.toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        Path base = sha256SumsFile.toAbsolutePath().getParent();
        return new Sha256Index(base, entries);
    }

    private static int indexOfWhitespaceRun(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (Character.isWhitespace(line.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }

    /** The directory recorded paths are relative to (the SHA256SUMS parent). */
    public Path baseDir() {
        return baseDir;
    }

    public int size() {
        return hashByPath.size();
    }

    /**
     * Expected hash for {@code relativePath}: exact match, else the entry whose
     * recorded path ends with the queried path (or vice versa), preferring the
     * longest recorded match.
     */
    public Optional<String> expectedHash(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        String query = normalize(relativePath);
        String direct = hashByPath.get(query);
        if (direct != null) {
            return Optional.of(direct);
        }
        String best = null;
        for (Map.Entry<String, String> entry : hashByPath.entrySet()) {
            String recorded = entry.getKey();
            boolean recordedEndsWithQuery = recorded.endsWith("/" + query);
            boolean queryEndsWithRecorded = query.endsWith("/" + recorded);
            if (recordedEndsWithQuery || queryEndsWithRecorded) {
                if (best == null || recorded.length() > best.length()) {
                    best = recorded;
                }
            }
        }
        return Optional.ofNullable(best).map(hashByPath::get);
    }

    /** Streams {@code file} and returns its lowercase hex SHA-256. */
    public static String hashFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
