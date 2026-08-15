package totah.lab.prometheus.store;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.execution.RawArtifact;

/** Validated evidence plus its immutable raw-artifact envelope. */
public record GeneratedEvidenceCandidate(
        QuantumEvidence evidence,
        GeneratedEvidenceRole role,
        Path artifactBase,
        List<RawArtifact> artifacts,
        String note) {
    public GeneratedEvidenceCandidate {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(artifactBase, "artifactBase");
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        Objects.requireNonNull(note, "note");
    }
}
