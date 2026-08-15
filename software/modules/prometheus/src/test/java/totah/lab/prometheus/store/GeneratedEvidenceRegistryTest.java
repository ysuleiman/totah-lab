package totah.lab.prometheus.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

final class GeneratedEvidenceRegistryTest {

    @TempDir Path temporary;

    @Test
    void successfulResultIsImmediatelyReusableAndPersisted() throws Exception {
        Fixture f = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        var result = registry.register("spec-1", f.evidence, f.role, f.raw, f.artifacts, "accepted");
        assertThat(result.disposition()).isEqualTo(GeneratedEvidenceRegistry.RegistrationDisposition.REGISTERED_NEW);
        assertThat(registry.reusable(f.evidence.identity().evidenceHash())).contains(f.evidence);
        assertThat(Files.readAllLines(registry.registryFile())).hasSize(1);
    }

    @Test
    void restartLoadsSameReusableEvidenceWithoutSourceParsing() throws Exception {
        Fixture f = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        new GeneratedEvidenceRegistry(temporary.resolve("registry"))
                .register("spec", f.evidence, f.role, f.raw, f.artifacts, "accepted");
        GeneratedEvidenceRegistry restarted = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        assertThat(restarted.reusable(f.evidence.identity().evidenceHash())).contains(f.evidence);
    }

    @Test
    void duplicateScientificIdentityReusesRatherThanDuplicates() throws Exception {
        Fixture f = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        registry.register("spec", f.evidence, f.role, f.raw, f.artifacts, "accepted");
        var second = registry.register("spec", f.evidence, f.role, f.raw, f.artifacts, "accepted again");
        assertThat(second.disposition()).isEqualTo(GeneratedEvidenceRegistry.RegistrationDisposition.REUSED_EXISTING);
        assertThat(registry.entries()).hasSize(1);
    }

    @Test
    void rawArtifactCorruptionIsDetectedOnRestart() throws Exception {
        Fixture f = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        new GeneratedEvidenceRegistry(temporary.resolve("registry"))
                .register("spec", f.evidence, f.role, f.raw, f.artifacts, "accepted");
        Files.writeString(f.raw.resolve("result.json"), "corrupted");
        assertThatThrownBy(() -> new GeneratedEvidenceRegistry(temporary.resolve("registry")))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("checksum mismatch");
    }

    @Test
    void sameGeometryAtDifferentMethodIsDistinctEvidence() throws Exception {
        Fixture pbe = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        Fixture pbe0 = fixture("PBE0", GeneratedEvidenceRole.PRIMARY);
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        registry.register("pbe", pbe.evidence, pbe.role, pbe.raw, pbe.artifacts, "pbe");
        registry.register("pbe0", pbe0.evidence, pbe0.role, pbe0.raw, pbe0.artifacts, "pbe0");
        assertThat(registry.entries()).hasSize(2);
        assertThat(pbe.evidence.identity().sameGeometryDifferentProtocol(pbe0.evidence.identity())).isTrue();
    }

    @Test
    void auxiliaryFiniteDifferenceEvidenceCannotBecomeTrainingTarget() throws Exception {
        Fixture auxiliary = fixture("PBE", GeneratedEvidenceRole.VALIDATION_AUXILIARY);
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        registry.register("spec", auxiliary.evidence, auxiliary.role,
                auxiliary.raw, auxiliary.artifacts, "finite difference audit");
        assertThat(registry.reusable(auxiliary.evidence.identity().evidenceHash())).isEmpty();
        assertThat(FrozenQmTargetDataset.from(registry).size()).isZero();
    }

    @Test
    void failurePersistsClassificationReasonSpecificationAndRawChecksums() throws Exception {
        Fixture f = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        registry.recordFailure("failed-spec", f.evidence.identity().evidenceHash(),
                GeneratedFailureClassification.EXECUTION_FAILED, Optional.of(f.raw), f.artifacts,
                "SCF failed to converge");
        GeneratedEvidenceEntry remembered = new GeneratedEvidenceRegistry(temporary.resolve("registry"))
                .failure("failed-spec").orElseThrow();
        assertThat(remembered.failureClassification()).contains(GeneratedFailureClassification.EXECUTION_FAILED);
        assertThat(remembered.note()).contains("SCF failed");
        assertThat(remembered.artifacts()).hasSize(1);
        assertThat(remembered.lifecycle()).containsExactly(GeneratedLifecycleState.MISSING,
                GeneratedLifecycleState.AUTHORIZED, GeneratedLifecycleState.RUNNING, GeneratedLifecycleState.FAILED);
    }

    @Test
    void repeatedConsumersShareOneImmutableQmRecord() throws Exception {
        Fixture f = fixture("PBE", GeneratedEvidenceRole.PRIMARY);
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        registry.register("spec", f.evidence, f.role, f.raw, f.artifacts, "accepted");
        FrozenQmTargetDataset targets = FrozenQmTargetDataset.from(registry);
        QuantumEvidence first = targets.target(f.evidence.identity().evidenceHash()).orElseThrow();
        QuantumEvidence second = targets.target(f.evidence.identity().evidenceHash()).orElseThrow();
        assertThat(second).isSameAs(first);
        assertThat(targets.targets()).hasSize(1);
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

    private record Fixture(QuantumEvidence evidence, GeneratedEvidenceRole role,
            Path raw, List<RawArtifact> artifacts) { }
}
