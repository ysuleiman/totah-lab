package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.store.GeneratedEvidenceCandidate;
import totah.lab.prometheus.store.GeneratedEvidenceRegistry;
import totah.lab.prometheus.store.GeneratedFailureClassification;
import totah.lab.prometheus.recovery.ArtifactChecksums;

/** Mandatory execute-or-reuse boundary for generated scientific evidence. */
public final class GeneratedEvidenceLifecycle {

    private final GeneratedEvidenceRegistry registry;

    public GeneratedEvidenceLifecycle(GeneratedEvidenceRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public LifecycleResult executeOrReuse(
            CalculationSpecification specification,
            EvidenceIdentity intendedIdentity,
            EvidenceExecutor executor,
            Path artifactBase,
            GeneratedEvidenceMapper mapper) throws EvidenceExecutionException, IOException {
        return execute(specification, intendedIdentity, executor, artifactBase, mapper, false);
    }

    /** Explicit operator-authorized retry; prior failure memory is preserved. */
    public LifecycleResult executeExplicitRetry(
            CalculationSpecification specification,
            EvidenceIdentity intendedIdentity,
            EvidenceExecutor executor,
            Path artifactBase,
            GeneratedEvidenceMapper mapper) throws EvidenceExecutionException, IOException {
        return execute(specification, intendedIdentity, executor, artifactBase, mapper, true);
    }

    private LifecycleResult execute(
            CalculationSpecification specification,
            EvidenceIdentity intendedIdentity,
            EvidenceExecutor executor,
            Path artifactBase,
            GeneratedEvidenceMapper mapper,
            boolean explicitRetry) throws EvidenceExecutionException, IOException {
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(intendedIdentity, "intendedIdentity");
        Optional<QuantumEvidence> reusable = registry.reusable(intendedIdentity.evidenceHash());
        if (reusable.isPresent()) {
            return new LifecycleResult(reusable.get(), true, List.of());
        }
        if (!explicitRetry && registry.failure(specification.checksum()).isPresent()) {
            throw new EvidenceExecutionException("remembered failed calculation; refusing automatic rerun: "
                    + specification.checksum());
        }
        RawCalculationResult raw;
        try {
            raw = executor.execute(specification);
            forceArtifactsToDisk(artifactBase);
        } catch (EvidenceExecutionException failure) {
            registry.recordFailure(specification.checksum(), intendedIdentity.evidenceHash(),
                    GeneratedFailureClassification.EXECUTION_FAILED, Optional.of(artifactBase),
                    existingArtifacts(artifactBase), failure.getMessage());
            throw failure;
        }
        List<GeneratedEvidenceCandidate> candidates;
        try {
            candidates = mapper.validateAndMap(raw, artifactBase);
            GeneratedEvidenceCandidate primary = candidates.stream()
                    .filter(candidate -> candidate.role() == totah.lab.prometheus.store.GeneratedEvidenceRole.PRIMARY)
                    .findFirst().orElseThrow(() -> new IOException("validated result has no PRIMARY evidence"));
            if (!primary.evidence().identity().isExactDuplicateOf(intendedIdentity)) {
                throw new IOException("generated primary result differs from intended scientific identity");
            }
        } catch (IOException | RuntimeException failure) {
            registry.recordFailure(specification.checksum(), intendedIdentity.evidenceHash(),
                    GeneratedFailureClassification.RESULT_VALIDATION_FAILED,
                    Optional.of(artifactBase), existingArtifacts(artifactBase),
                    "result validation failed: " + failure.getMessage());
            throw failure;
        }
        try {
            registry.registerBatch(specification.checksum(), candidates);
            GeneratedEvidenceCandidate primary = candidates.stream()
                    .filter(candidate -> candidate.role() == totah.lab.prometheus.store.GeneratedEvidenceRole.PRIMARY)
                    .findFirst().orElseThrow();
            return new LifecycleResult(primary.evidence(), false, candidates);
        } catch (IOException | RuntimeException failure) {
            registry.recordFailure(specification.checksum(), intendedIdentity.evidenceHash(),
                    GeneratedFailureClassification.REGISTRATION_FAILED,
                    Optional.of(artifactBase), existingArtifacts(artifactBase),
                    "result registration failed: " + failure.getMessage());
            throw failure;
        }
    }

    /** A worker cannot enter validation until every file it produced is durably flushed. */
    private static void forceArtifactsToDisk(Path base) throws IOException {
        if (!Files.isDirectory(base)) return;
        try (var paths = Files.walk(base)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
            }
        }
    }

    private static List<RawArtifact> existingArtifacts(Path base) throws IOException {
        if (!Files.isDirectory(base)) return List.of();
        List<RawArtifact> artifacts = new ArrayList<>();
        try (var paths = Files.walk(base)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                artifacts.add(new RawArtifact(base.relativize(path).toString(),
                        ArtifactChecksums.sha256(path), "failure_artifact"));
            }
        }
        return List.copyOf(artifacts);
    }

    public record LifecycleResult(
            QuantumEvidence primaryEvidence,
            boolean reused,
            List<GeneratedEvidenceCandidate> registeredCandidates) {
        public LifecycleResult {
            registeredCandidates = List.copyOf(registeredCandidates);
        }
    }
}
