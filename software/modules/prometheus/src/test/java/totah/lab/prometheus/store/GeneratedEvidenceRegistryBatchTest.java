package totah.lab.prometheus.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.execution.RawArtifact;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.recovery.ArtifactChecksums;

/**
 * Fail-closed falsification of the batch, conflict, and failure-ordering branches
 * of {@link GeneratedEvidenceRegistry}: empty-batch gating, in-batch duplicates,
 * mixed new/existing dispositions, same-identity/different-content conflicts,
 * recordedAt-ordered failure lookup, and batch atomicity on late conflict.
 */
final class GeneratedEvidenceRegistryBatchTest {

    @TempDir Path temporary;

    /**
     * TEST_ID: REGISTRY-BATCH-01 — an empty batch is rejected fail-closed and
     * leaves the registry untouched (no file created, no entries).
     */
    @Test
    void emptyBatchIsRejectedWithoutTouchingTheRegistry() throws Exception {
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));

        assertThatThrownBy(() -> registry.registerBatch("spec-1", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generated evidence batch is empty");

        assertThat(registry.entries()).isEmpty();
        assertThat(registry.registryFile()).doesNotExist();
    }

    /**
     * TEST_ID: REGISTRY-BATCH-02 — the same scientific identity twice in one
     * batch (byte-identical content) is rejected as an in-batch duplicate, and
     * the rejection is atomic: nothing from the batch is persisted.
     */
    @Test
    void duplicateIdentityWithinOneBatchIsRejectedAndNothingIsPersisted() throws Exception {
        Fixture f = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));

        assertThatThrownBy(() -> registry.registerBatch("spec-1", List.of(
                candidate(f, "first occurrence"), candidate(f, "duplicate occurrence"))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("duplicate identity within generated evidence batch")
                .hasMessageContaining("primary:" + f.evidence.identity().evidenceHash());

        assertThat(registry.entries()).isEmpty();
        assertThat(registry.registryFile()).doesNotExist();
    }

    /**
     * TEST_ID: REGISTRY-BATCH-03 — a batch mixing an already-registered identity
     * (identical content) with a novel identity reports per-item dispositions in
     * candidate order: reuse for the existing item, registration for the new one.
     */
    @Test
    void mixedBatchReusesExistingAndRegistersNewWithPerItemDispositions() throws Exception {
        Fixture existing = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        Fixture novel = fixture("PBE0", GeneratedEvidenceRole.PRIMARY);
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        var initial = registry.register("spec-1", existing.evidence, existing.role,
                existing.raw, existing.artifacts, "original");

        var results = registry.registerBatch("spec-1", List.of(
                candidate(existing, "identical re-submission"), candidate(novel, "novel evidence")));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).disposition())
                .isEqualTo(GeneratedEvidenceRegistry.RegistrationDisposition.REUSED_EXISTING);
        assertThat(results.get(0).entry()).isEqualTo(initial.entry());
        assertThat(results.get(1).disposition())
                .isEqualTo(GeneratedEvidenceRegistry.RegistrationDisposition.REGISTERED_NEW);
        assertThat(results.get(1).entry().scientificIdentityHash())
                .isEqualTo(novel.evidence.identity().evidenceHash());
        assertThat(registry.entries()).hasSize(2);
        assertThat(Files.readAllLines(registry.registryFile())).hasSize(2);
        assertThat(registry.reusable(novel.evidence.identity().evidenceHash())).contains(novel.evidence);
    }

    /**
     * TEST_ID: REGISTRY-BATCH-04 — re-registering a scientific identity with
     * different payload content must fail closed and leave the original
     * registration intact and reusable; a silent overwrite would be a defect.
     */
    @Test
    void registeringSameIdentityWithDifferentContentFailsClosedAndKeepsOriginal() throws Exception {
        Fixture f = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        registry.register("spec-1", f.evidence, f.role, f.raw, f.artifacts, "original");
        QuantumEvidence conflicting = withEnergy(f.evidence, -2.0);

        assertThatThrownBy(() -> registry.register("spec-1", conflicting, f.role,
                f.raw, f.artifacts, "conflicting re-submission"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("scientific identity already registered with different content")
                .hasMessageContaining("primary:" + f.evidence.identity().evidenceHash());

        assertThat(registry.entries()).hasSize(1);
        assertThat(registry.reusable(f.evidence.identity().evidenceHash())).contains(f.evidence);
        assertThat(Files.readAllLines(registry.registryFile())).hasSize(1);
    }

    /**
     * TEST_ID: REGISTRY-BATCH-05 — failure(spec) must select by recordedAt, not
     * by attempt number or insertion order: the entry carrying the latest
     * recordedAt wins even when it is attempt 1 of 2.
     */
    @Test
    void failureLookupReturnsLatestByRecordedAtNotByAttemptNumber() throws Exception {
        Path directory = temporary.resolve("registry");
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(directory);
        registry.recordFailure("spec-x", "hash-x", "first attempt");
        registry.recordFailure("spec-x", "hash-x", "second attempt");

        // Deliberately invert the time order: attempt 1 carries the LATER
        // recordedAt, attempt 2 the earlier one. recordedAt is operational
        // metadata outside the checksummed failure payload, so the rewrite is
        // checksum-clean and isolates the selection criterion.
        ObjectMapper mapper = new ObjectMapper();
        List<String> rewritten = new ArrayList<>();
        for (String line : Files.readAllLines(registry.registryFile())) {
            ObjectNode node = (ObjectNode) mapper.readTree(line);
            if (node.path("registryKey").asText().equals("failure:spec-x:1")) {
                node.put("recordedAt", "2026-06-01T00:00:00Z");
            } else {
                node.put("recordedAt", "2026-01-01T00:00:00Z");
            }
            rewritten.add(mapper.writeValueAsString(node));
        }
        Files.writeString(registry.registryFile(), String.join("\n", rewritten) + "\n");

        GeneratedEvidenceRegistry restarted = new GeneratedEvidenceRegistry(directory);
        Optional<GeneratedEvidenceEntry> failure = restarted.failure("spec-x");

        assertThat(failure).isPresent();
        assertThat(failure.orElseThrow().registryKey()).isEqualTo("failure:spec-x:1");
        assertThat(failure.orElseThrow().note()).isEqualTo("first attempt");
        assertThat(failure.orElseThrow().recordedAt()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
    }

    /**
     * TEST_ID: REGISTRY-BATCH-06 — a batch whose LAST item conflicts with an
     * already-registered identity must roll back: the earlier novel item from the
     * same batch is neither visible in memory nor persisted on disk, matching the
     * "atomically persists" contract in the registerBatch javadoc.
     */
    @Test
    void batchWithFinalConflictingItemRollsBackEarlierAdditions() throws Exception {
        Fixture existing = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        Fixture novel = fixture("PBE0", GeneratedEvidenceRole.PRIMARY);
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        registry.register("spec-1", existing.evidence, existing.role,
                existing.raw, existing.artifacts, "original");

        QuantumEvidence conflicting = withEnergy(existing.evidence, -2.0);
        GeneratedEvidenceCandidate conflictingCandidate = new GeneratedEvidenceCandidate(
                conflicting, existing.role, existing.raw, existing.artifacts, "conflicting payload");

        assertThatThrownBy(() -> registry.registerBatch("spec-1", List.of(
                candidate(novel, "novel evidence"), conflictingCandidate)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("scientific identity already registered with different content");

        assertThat(registry.entries()).hasSize(1);
        assertThat(registry.reusable(novel.evidence.identity().evidenceHash())).isEmpty();
        assertThat(Files.readAllLines(registry.registryFile())).hasSize(1);
        GeneratedEvidenceRegistry restarted = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        assertThat(restarted.entries()).hasSize(1);
        assertThat(restarted.reusable(novel.evidence.identity().evidenceHash())).isEmpty();
    }

    private Fixture fixture(String method, GeneratedEvidenceRole role) throws Exception {
        Path raw = temporary.resolve("raw-" + method + "-" + role);
        Files.createDirectories(raw);
        Path result = raw.resolve("result.json");
        Files.writeString(result, "{\"energy\":-1.0}\n");
        RawArtifact artifact = new RawArtifact("result.json", ArtifactChecksums.sha256(result), "result_json");
        EvidenceIdentity identity = new EvidenceIdentity(TslFixtures.TSL,
                TslFixtures.canonicalMap().canonicalHash(), TslFixtures.geometryIdentityA(), 0, 1,
                role == GeneratedEvidenceRole.VALIDATION_AUXILIARY
                        ? CalculationType.SINGLE_POINT : CalculationType.FORCE_EVALUATION,
                new QmProtocol(method, "def2-SVP", "D3(BJ)", "gas", false, "PySCF", "2.14.0"),
                List.of(), role == GeneratedEvidenceRole.VALIDATION_AUXILIARY
                        ? List.of("finite_difference_energy") : List.of("energy", "gradient", "forces"));
        QuantumEvidence evidence = new QuantumEvidence(identity,
                new EvidenceProvenance(result.toString(), artifact.sha256(), Instant.EPOCH, List.of(), "generated"),
                ConvergenceStatus.CONVERGED, EvidenceAcceptanceState.ACCEPTED,
                Optional.of(-1.0), Optional.of(List.of(0.0, 0.0, 0.0)), Optional.empty(),
                Optional.empty(), Optional.empty(), "converged");
        return new Fixture(evidence, role, raw, List.of(artifact));
    }

    private static GeneratedEvidenceCandidate candidate(Fixture fixture, String note) {
        return new GeneratedEvidenceCandidate(
                fixture.evidence, fixture.role, fixture.raw, fixture.artifacts, note);
    }

    /** Same scientific identity and provenance, different payload content. */
    private static QuantumEvidence withEnergy(QuantumEvidence evidence, double energyHartree) {
        return new QuantumEvidence(evidence.identity(), evidence.provenance(), evidence.convergence(),
                evidence.acceptance(), Optional.of(energyHartree), evidence.gradientHartreePerBohr(),
                evidence.hessianHartreePerBohr2(), evidence.dipoleDebye(),
                evidence.interactionEnergyKcalMol(), evidence.convergenceNote());
    }

    private record Fixture(QuantumEvidence evidence, GeneratedEvidenceRole role,
            Path raw, List<RawArtifact> artifacts) { }
}
