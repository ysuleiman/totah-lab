package totah.lab.prometheus.store;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.execution.RawArtifact;

/** One immutable entry in the append-log-equivalent generated evidence registry. */
public record GeneratedEvidenceEntry(
        String registryKey,
        String specificationChecksum,
        String scientificIdentityHash,
        GeneratedEvidenceRole role,
        GeneratedEvidenceStatus status,
        Optional<QuantumEvidence> evidence,
        Optional<String> artifactBase,
        List<RawArtifact> artifacts,
        Optional<GeneratedFailureClassification> failureClassification,
        List<GeneratedLifecycleState> lifecycle,
        String payloadSha256,
        Instant recordedAt,
        String note) {

    public GeneratedEvidenceEntry {
        Objects.requireNonNull(registryKey, "registryKey");
        Objects.requireNonNull(specificationChecksum, "specificationChecksum");
        Objects.requireNonNull(scientificIdentityHash, "scientificIdentityHash");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        evidence = Objects.requireNonNull(evidence, "evidence");
        artifactBase = Objects.requireNonNull(artifactBase, "artifactBase");
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        failureClassification = Objects.requireNonNull(failureClassification, "failureClassification");
        lifecycle = List.copyOf(Objects.requireNonNull(lifecycle, "lifecycle"));
        Objects.requireNonNull(payloadSha256, "payloadSha256");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(note, "note");
        if (status == GeneratedEvidenceStatus.FAILED && evidence.isPresent()) {
            throw new IllegalArgumentException("failed entry cannot contain accepted/rejected evidence");
        }
        if (status == GeneratedEvidenceStatus.FAILED && failureClassification.isEmpty()) {
            throw new IllegalArgumentException("failed entry requires failure classification");
        }
        if (status != GeneratedEvidenceStatus.FAILED && evidence.isEmpty()) {
            throw new IllegalArgumentException("non-failed entry requires evidence");
        }
    }
}
