package totah.lab.athena.pocket.evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Observed pocket residues and independently evaluated contextual channels. */
public record ResidueContextEvidence(
        EvidenceChannel<List<ResidueObservation>> pocketResidues,
        EvidenceChannel<Map<EvidenceResidueId, String>> chemistryClasses,
        EvidenceChannel<Map<EvidenceResidueId, String>> sequenceNeighborhoods,
        EvidenceChannel<Map<EvidenceResidueId, Map<String, String>>> conservation,
        EvidenceChannel<Map<EvidenceResidueId, Map<String, String>>> annotations
) {
    public ResidueContextEvidence {
        Objects.requireNonNull(pocketResidues, "pocketResidues");
        Objects.requireNonNull(chemistryClasses, "chemistryClasses");
        Objects.requireNonNull(sequenceNeighborhoods, "sequenceNeighborhoods");
        Objects.requireNonNull(conservation, "conservation");
        Objects.requireNonNull(annotations, "annotations");
        EvidenceChannel.requireOrigin(pocketResidues,
                EvidenceOrigin.SOURCE_OBSERVED, "pocketResidues");
        EvidenceChannel.requireOrigin(chemistryClasses,
                EvidenceOrigin.DERIVED, "chemistryClasses");
        EvidenceChannel.requireOrigin(sequenceNeighborhoods,
                EvidenceOrigin.DERIVED, "sequenceNeighborhoods");
        EvidenceChannel.requireOrigin(conservation,
                EvidenceOrigin.DERIVED, "conservation");
        EvidenceChannel.requireOrigin(annotations,
                EvidenceOrigin.DERIVED, "annotations");
    }
}
