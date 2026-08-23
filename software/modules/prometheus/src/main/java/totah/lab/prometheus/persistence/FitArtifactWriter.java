package totah.lab.prometheus.persistence;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic, checksum-verified persistence boundary for successful fits. */
public final class FitArtifactWriter {
    public static final String ARTIFACT = "fit-artifact.json";
    public static final String MANIFEST = "SHA256SUMS";
    public static final String ARTIFACT_SHA256 = "ARTIFACT_SHA256";
    private static final List<String> MIRRORS = List.of(
            "basis-order.json", "parameter-names.json", "initial-parameter-vector.json",
            "final-parameter-vector.json", "training-ids.json", "validation-ids.json",
            "optimizer-state.json", "predictions.json", "residuals.json");

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

    public Receipt persistSuccessful(Path targetDirectory, FitArtifact artifact) throws IOException {
        if (artifact.convergenceStatus() != FitArtifact.ConvergenceStatus.SUCCESS) {
            throw new IllegalArgumentException("only a converged SUCCESS fit can be published");
        }
        Path absolute = targetDirectory.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("fit artifact directory has no parent");
        Files.createDirectories(parent);
        if (Files.exists(absolute)) throw new IOException("fit artifact target already exists: " + absolute);
        Path staging = Files.createTempDirectory(parent, ".fit-artifact-");
        try {
            writeJson(staging.resolve(ARTIFACT), artifact);
            writeJson(staging.resolve(MIRRORS.get(0)), artifact.basisOrder());
            writeJson(staging.resolve(MIRRORS.get(1)), artifact.parameterNames());
            writeJson(staging.resolve(MIRRORS.get(2)), artifact.initialParameterVector());
            writeJson(staging.resolve(MIRRORS.get(3)), artifact.finalParameterVector());
            writeJson(staging.resolve(MIRRORS.get(4)), artifact.trainingIds());
            writeJson(staging.resolve(MIRRORS.get(5)), artifact.validationIds());
            writeJson(staging.resolve(MIRRORS.get(6)), artifact.optimizerState());
            writeJson(staging.resolve(MIRRORS.get(7)), artifact.predictions());
            writeJson(staging.resolve(MIRRORS.get(8)), artifact.residuals());
            Map<String, String> checksums = new LinkedHashMap<>();
            checksums.put(ARTIFACT, sha256(staging.resolve(ARTIFACT)));
            for (String mirror : MIRRORS) checksums.put(mirror, sha256(staging.resolve(mirror)));
            StringBuilder manifest = new StringBuilder();
            checksums.forEach((name, checksum) -> manifest.append(checksum).append("  ").append(name).append('\n'));
            Files.writeString(staging.resolve(MANIFEST), manifest, StandardCharsets.UTF_8);
            String bundleIdentity = sha256(staging.resolve(MANIFEST));
            Files.writeString(staging.resolve(ARTIFACT_SHA256), bundleIdentity + "  " + MANIFEST + "\n",
                    StandardCharsets.UTF_8);
            moveAtomically(staging, absolute);
            Loaded loaded = readVerified(absolute);
            return new Receipt(loaded.artifact(), absolute, loaded.artifactSha256());
        } catch (IOException | RuntimeException exception) {
            deleteStaging(staging);
            throw exception;
        }
    }

    public Loaded readVerified(Path directory) throws IOException {
        Path root = directory.toRealPath();
        Map<String, String> checksums = readManifest(root.resolve(MANIFEST));
        for (String required : concat(ARTIFACT, MIRRORS)) {
            String expected = checksums.get(required);
            Path path = root.resolve(required);
            if (expected == null || !Files.isRegularFile(path)) {
                throw new IOException("fit artifact component missing: " + required);
            }
            if (!expected.equals(sha256(path))) throw new IOException("fit artifact checksum mismatch: " + required);
        }
        String identityLine = Files.readString(root.resolve(ARTIFACT_SHA256), StandardCharsets.UTF_8).trim();
        String expectedIdentity = identityLine.split("\\s+", 2)[0];
        String actualIdentity = sha256(root.resolve(MANIFEST));
        if (!expectedIdentity.equals(actualIdentity)) throw new IOException("fit artifact manifest checksum mismatch");
        FitArtifact artifact = mapper.readValue(root.resolve(ARTIFACT).toFile(), FitArtifact.class);
        verifyMirrors(root, artifact);
        return new Loaded(artifact, actualIdentity);
    }

    private void verifyMirrors(Path root, FitArtifact artifact) throws IOException {
        requireEqual(MIRRORS.get(0), artifact.basisOrder(), readList(root.resolve(MIRRORS.get(0)), String.class));
        requireEqual(MIRRORS.get(1), artifact.parameterNames(), readList(root.resolve(MIRRORS.get(1)), String.class));
        requireEqual(MIRRORS.get(2), artifact.initialParameterVector(), readList(root.resolve(MIRRORS.get(2)), Double.class));
        requireEqual(MIRRORS.get(3), artifact.finalParameterVector(), readList(root.resolve(MIRRORS.get(3)), Double.class));
        requireEqual(MIRRORS.get(4), artifact.trainingIds(), readList(root.resolve(MIRRORS.get(4)), String.class));
        requireEqual(MIRRORS.get(5), artifact.validationIds(), readList(root.resolve(MIRRORS.get(5)), String.class));
        requireEqual(MIRRORS.get(7), artifact.predictions(), readList(root.resolve(MIRRORS.get(7)), Double.class));
        requireEqual(MIRRORS.get(8), artifact.residuals(), readList(root.resolve(MIRRORS.get(8)), Double.class));
        @SuppressWarnings("unchecked") Map<String, String> optimizer = mapper.readValue(
                root.resolve(MIRRORS.get(6)).toFile(), Map.class);
        requireEqual(MIRRORS.get(6), artifact.optimizerState(), optimizer);
    }

    private <T> List<T> readList(Path path, Class<T> type) throws IOException {
        return mapper.readValue(path.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, type));
    }

    private static void requireEqual(String component, Object expected, Object actual) throws IOException {
        if (!expected.equals(actual)) throw new IOException("fit artifact component disagrees with metadata: " + component);
    }

    private void writeJson(Path path, Object value) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private static Map<String, String> readManifest(Path path) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String[] fields = line.trim().split("\\s+", 2);
            if (fields.length != 2 || !fields[0].matches("[0-9a-f]{64}")) {
                throw new IOException("invalid fit artifact checksum manifest");
            }
            result.put(fields[1], fields[0]);
        }
        return Map.copyOf(result);
    }

    private static List<String> concat(String first, List<String> rest) {
        var result = new java.util.ArrayList<String>();
        result.add(first); result.addAll(rest); return result;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static void deleteStaging(Path staging) {
        if (!Files.exists(staging)) return;
        try (var paths = Files.walk(staging)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original persistence failure; a stale dot-directory is never a qualified artifact.
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) >= 0;) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Receipt(FitArtifact artifact, Path directory, String artifactSha256) { }
    public record Loaded(FitArtifact artifact, String artifactSha256) { }
}
