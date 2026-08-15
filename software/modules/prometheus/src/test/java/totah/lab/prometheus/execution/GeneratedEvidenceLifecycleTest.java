package totah.lab.prometheus.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.store.GeneratedEvidenceCandidate;
import totah.lab.prometheus.store.GeneratedEvidenceRegistry;
import totah.lab.prometheus.store.GeneratedEvidenceRole;

final class GeneratedEvidenceLifecycleTest {

    @TempDir Path temporary;

    @Test
    void persistedExactIdentityPreventsExecutorInvocationAcrossRestart() throws Exception {
        var spec = ExecutionTestSpecs.withSoftware("PySCF");
        EvidenceIdentity identity = identity(spec);
        Path raw = raw();
        RawArtifact artifact = artifact(raw);
        QuantumEvidence evidence = evidence(identity, raw, artifact);
        Path registryPath = temporary.resolve("registry");
        new GeneratedEvidenceRegistry(registryPath).register("spec", evidence,
                GeneratedEvidenceRole.PRIMARY, raw, List.of(artifact), "accepted");
        AtomicInteger executions = new AtomicInteger();
        EvidenceExecutor executor = executor(executions, false);

        var result = new GeneratedEvidenceLifecycle(new GeneratedEvidenceRegistry(registryPath))
                .executeOrReuse(spec, identity, executor, raw, (ignored, base) -> List.of());

        assertThat(result.reused()).isTrue();
        assertThat(executions).hasValue(0);
    }

    @Test
    void executionFailureIsRememberedAndBlocksImplicitRetry() throws Exception {
        var spec = ExecutionTestSpecs.withSoftware("PySCF");
        EvidenceIdentity identity = identity(spec);
        Path raw = temporary.resolve("failed-raw");
        Files.createDirectories(raw);
        AtomicInteger executions = new AtomicInteger();
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        GeneratedEvidenceLifecycle lifecycle = new GeneratedEvidenceLifecycle(registry);
        assertThatThrownBy(() -> lifecycle.executeOrReuse(spec, identity,
                executor(executions, true), raw, (ignored, base) -> List.of()))
                .isInstanceOf(EvidenceExecutionException.class);
        assertThatThrownBy(() -> lifecycle.executeOrReuse(spec, identity,
                executor(executions, false), raw, (ignored, base) -> List.of()))
                .isInstanceOf(EvidenceExecutionException.class).hasMessageContaining("remembered failed");
        assertThat(executions).hasValue(1);
        assertThat(registry.failure(spec.checksum())).isPresent();
    }

    private Path raw() throws Exception {
        Path raw = temporary.resolve("raw");
        Files.createDirectories(raw);
        Files.writeString(raw.resolve("result.json"), "{}\n");
        return raw;
    }

    private static RawArtifact artifact(Path raw) throws Exception {
        return new RawArtifact("result.json", ArtifactChecksums.sha256(raw.resolve("result.json")), "result_json");
    }

    private static EvidenceIdentity identity(totah.lab.prometheus.planning.CalculationSpecification spec) {
        return new EvidenceIdentity(spec.molecule(), TslFixtures.canonicalMap().canonicalHash(), spec.geometry(),
                spec.formalCharge(), spec.multiplicity(), spec.calculationType(), spec.protocol(),
                spec.constraints(), spec.requiredOutputs());
    }

    private static QuantumEvidence evidence(EvidenceIdentity identity, Path raw, RawArtifact artifact) {
        return new QuantumEvidence(identity,
                new EvidenceProvenance(raw.resolve("result.json").toString(), artifact.sha256(),
                        Instant.EPOCH, List.of(), "generated"),
                ConvergenceStatus.CONVERGED, EvidenceAcceptanceState.ACCEPTED,
                Optional.of(-1.0), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "ok");
    }

    private static EvidenceExecutor executor(AtomicInteger count, boolean fail) {
        return new EvidenceExecutor() {
            public String executorId() { return "fixture"; }
            public boolean supports(totah.lab.prometheus.planning.CalculationSpecification spec) { return true; }
            public RawCalculationResult execute(totah.lab.prometheus.planning.CalculationSpecification spec)
                    throws EvidenceExecutionException {
                count.incrementAndGet();
                if (fail) throw new EvidenceExecutionException("fixture failure");
                return new RawCalculationResult(spec, List.of(), ConvergenceStatus.CONVERGED, "ok");
            }
        };
    }
}
