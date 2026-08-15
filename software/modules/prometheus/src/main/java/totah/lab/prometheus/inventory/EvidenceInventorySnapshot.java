package totah.lab.prometheus.inventory;

import java.util.List;
import java.util.Objects;

/** Immutable inventory snapshot with quantum/classical summaries kept separate. */
public record EvidenceInventorySnapshot(
        EvidenceDimensionSummary quantum,
        EvidenceDimensionSummary classical,
        List<ProvenanceGap> provenanceGaps) {

    public EvidenceInventorySnapshot {
        Objects.requireNonNull(quantum, "quantum");
        Objects.requireNonNull(classical, "classical");
        provenanceGaps = List.copyOf(Objects.requireNonNull(provenanceGaps, "provenanceGaps"));
    }
}
