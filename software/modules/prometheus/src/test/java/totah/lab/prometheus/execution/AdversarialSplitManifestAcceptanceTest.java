package totah.lab.prometheus.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * TEST_ID: E5 — split manifests are persisted, complete, and sealed.
 *
 * <p>Read-only acceptance test over the actual frozen TSL-RSH campaign output
 * at {@code analysis/mettl7-phase2/execution-unit-05O/force-cloud-qm/}
 * (60 snapshots, 45/15 split). The three split artifacts are
 * {@code FROZEN_QM_TARGET_DATASET.json} (full manifest with per-snapshot
 * {@code dataset_role}), {@code FORCE_FITTING_TRAINING_TARGETS.json} (45),
 * and {@code SEALED_HOLDOUT_IDENTITIES.json} (15). Integrity coverage is via
 * per-artifact {@code .sha256} sidecars; the seal binding is
 * {@code PHASE4_QM_CAMPAIGN_REPORT.json} → {@code frozen_artifacts}, which
 * records the SHA-256 of each split file (the report contains no field
 * literally named {@code split_manifest_sha256}; the three
 * {@code *_sha256} entries in {@code frozen_artifacts} are the binding that
 * exists, and are what this test verifies).
 *
 * <p>The test never writes into the analysis tree; the tamper sub-case works
 * on a {@link TempDir} copy.
 */
final class AdversarialSplitManifestAcceptanceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DATASET = "FROZEN_QM_TARGET_DATASET.json";
    private static final String TRAINING = "FORCE_FITTING_TRAINING_TARGETS.json";
    private static final String HOLDOUT = "SEALED_HOLDOUT_IDENTITIES.json";
    private static final String REPORT = "PHASE4_QM_CAMPAIGN_REPORT.json";

    @TempDir
    Path temporaryDirectory;

    /**
     * E5 — the three artifacts exist, parse, match their sidecar checksums,
     * partition the manifest snapshot ids exactly (disjoint, union-complete,
     * role labels in the manifest agree with the two views), the holdout view
     * exposes no target values, and the campaign report's
     * {@code frozen_artifacts} hashes match the files on disk.
     */
    @Test
    void e5FrozenSplitIsCompleteChecksummedAndSealed() throws IOException {
        Path root = forceCloudRoot();

        JsonNode dataset = readJson(root.resolve(DATASET));
        JsonNode training = readJson(root.resolve(TRAINING));
        JsonNode holdout = readJson(root.resolve(HOLDOUT));

        assertSidecarMatches(root, DATASET);
        assertSidecarMatches(root, TRAINING);
        assertSidecarMatches(root, HOLDOUT);

        assertEquals(60, dataset.path("target_count").asInt());
        assertEquals(45, dataset.path("training_count").asInt());
        assertEquals(15, dataset.path("holdout_count").asInt());
        assertEquals(45, training.path("target_count").asInt());
        assertFalse(training.path("holdout_targets_included").asBoolean(true),
                "the training view must declare that no holdout targets are included");
        assertEquals(15, holdout.path("target_count").asInt());
        assertFalse(holdout.path("target_values_exposed").asBoolean(true),
                "the sealed holdout view must not expose target values");

        Set<String> manifestIds = new LinkedHashSet<>();
        Set<String> manifestTrain = new HashSet<>();
        Set<String> manifestHoldout = new HashSet<>();
        for (JsonNode target : dataset.path("targets")) {
            String id = target.path("snapshot_id").asText();
            assertTrue(manifestIds.add(id), "duplicate snapshot id in manifest: " + id);
            switch (target.path("dataset_role").asText()) {
                case "TRAIN" -> manifestTrain.add(id);
                case "HOLDOUT" -> manifestHoldout.add(id);
                default -> fail("unknown dataset_role for " + id);
            }
        }
        assertEquals(60, manifestIds.size());

        Set<String> trainingIds = idsOf(training);
        Set<String> holdoutIds = idsOf(holdout);
        assertEquals(45, trainingIds.size());
        assertEquals(15, holdoutIds.size());
        assertPartitionsExactly(trainingIds, holdoutIds, manifestIds,
                "the frozen split must partition the manifest ids");
        assertEquals(manifestTrain, trainingIds,
                "the training view must equal the manifest's TRAIN role set");
        assertEquals(manifestHoldout, holdoutIds,
                "the holdout view must equal the manifest's HOLDOUT role set");

        for (JsonNode target : holdout.path("targets")) {
            assertFalse(target.has("energy_hartree"),
                    "sealed holdout record leaks an energy: " + target);
            assertFalse(target.has("gradient_hartree_per_bohr"),
                    "sealed holdout record leaks a gradient: " + target);
        }

        JsonNode frozen = readJson(root.resolve(REPORT)).path("frozen_artifacts");
        assertEquals(sha256(root.resolve(DATASET)),
                frozen.path("authoritative_dataset_sha256").asText(),
                "seal binding for the authoritative dataset");
        assertEquals(sha256(root.resolve(TRAINING)),
                frozen.path("training_only_view_sha256").asText(),
                "seal binding for the training view");
        assertEquals(sha256(root.resolve(HOLDOUT)),
                frozen.path("sealed_holdout_identity_view_sha256").asText(),
                "seal binding for the sealed holdout view");
        assertEquals(45, frozen.path("training_count").asInt());
        assertEquals(15, frozen.path("sealed_holdout_count").asInt());
    }

    /**
     * E5 tamper sub-case — moving one snapshot id from the holdout into the
     * training view (on a TempDir copy) must break both the partition and the
     * checksum seal: the recomputed hash no longer matches the recorded
     * sidecar/report value, and the two views no longer partition the
     * manifest. If either detection path went silent, a re-derived split
     * could pass as the sealed one.
     */
    @Test
    void e5FlippingOneIdBetweenSplitViewsBreaksPartitionAndSeal() throws IOException {
        Path root = forceCloudRoot();
        String trainingText = Files.readString(root.resolve(TRAINING));

        JsonNode training = readJson(root.resolve(TRAINING));
        JsonNode holdout = readJson(root.resolve(HOLDOUT));
        String trainedId = training.path("targets").get(0).path("snapshot_id").asText();
        String heldOutId = holdout.path("targets").get(0).path("snapshot_id").asText();

        String tamperedTraining = trainingText.replaceFirst(
                java.util.regex.Pattern.quote(trainedId), heldOutId);
        assertNotEquals(trainingText, tamperedTraining,
                "the tamper actually changed the training view");
        Path tamperedFile = temporaryDirectory.resolve(TRAINING);
        Files.writeString(tamperedFile, tamperedTraining, StandardCharsets.UTF_8);

        assertNotEquals(sha256(root.resolve(TRAINING)), sha256(tamperedFile),
                "the tampered file must not match the sealed checksum");
        assertNotEquals(
                readJson(root.resolve(REPORT)).path("frozen_artifacts")
                        .path("training_only_view_sha256").asText(),
                sha256(tamperedFile),
                "the seal's recorded hash must reject the tampered split file");

        JsonNode tampered = readJson(tamperedFile);
        Set<String> tamperedIds = idsOf(tampered);
        Set<String> holdoutIds = idsOf(holdout);
        Set<String> manifestIds = idsOf(readJson(root.resolve(DATASET)));
        assertThrowsAssertion(() -> assertPartitionsExactly(
                tamperedIds, holdoutIds, manifestIds, "tampered split"));
    }

    private static JsonNode readJson(Path path) throws IOException {
        return JSON.readTree(path.toFile());
    }

    private static void assertPartitionsExactly(
            Set<String> trainingIds, Set<String> holdoutIds, Set<String> manifestIds,
            String message) {
        Set<String> overlap = new HashSet<>(trainingIds);
        overlap.retainAll(holdoutIds);
        assertTrue(overlap.isEmpty(), message + ": not disjoint, overlap " + overlap);
        Set<String> union = new HashSet<>(trainingIds);
        union.addAll(holdoutIds);
        assertEquals(manifestIds, union, message + ": not union-complete");
    }

    private static void assertThrowsAssertion(Runnable check) {
        try {
            check.run();
        } catch (AssertionError expected) {
            return;
        }
        fail("the tampered split was not detected by the partition check");
    }

    private static Set<String> idsOf(JsonNode view) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode target : view.path("targets")) {
            assertTrue(ids.add(target.path("snapshot_id").asText()),
                    "duplicate snapshot id in view");
        }
        return ids;
    }

    private static void assertSidecarMatches(Path root, String artifact) throws IOException {
        Path sidecar = root.resolve(artifact.replace(".json", ".sha256"));
        assertTrue(Files.isRegularFile(sidecar), "missing sidecar " + sidecar);
        String recorded = Files.readString(sidecar).trim().split("\\s+")[0];
        assertEquals(recorded, sha256(root.resolve(artifact)),
                "sidecar checksum does not cover the current artifact bytes");
    }

    /** Locates the frozen campaign tree by walking up from the working directory. */
    private static Path forceCloudRoot() {
        Path directory = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && directory != null; depth++) {
            Path candidate = directory.resolve(
                    "analysis/mettl7-phase2/execution-unit-05O/force-cloud-qm");
            if (Files.isRegularFile(candidate.resolve(DATASET))) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException(
                "E5 fixture absent: analysis/mettl7-phase2/execution-unit-05O/"
                        + "force-cloud-qm/" + DATASET + " not found above the"
                        + " working directory — the frozen TSL-RSH campaign output"
                        + " is required for this acceptance test");
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
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
