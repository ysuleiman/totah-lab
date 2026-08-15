package totah.lab.prometheus.inventory;

import java.util.Map;
import java.util.Objects;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;

/** Independent counts for one evidence dimension. No combined score is defined. */
public record EvidenceDimensionSummary(
        int totalCount,
        Map<String, Long> countsByMolecule,
        Map<String, Long> countsByProtocol,
        Map<CalculationType, Long> countsByCalculationType,
        Map<EvidenceAcceptanceState, Long> countsByAcceptance) {

    public EvidenceDimensionSummary {
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must be >= 0");
        }
        countsByMolecule = Map.copyOf(Objects.requireNonNull(countsByMolecule, "countsByMolecule"));
        countsByProtocol = Map.copyOf(Objects.requireNonNull(countsByProtocol, "countsByProtocol"));
        countsByCalculationType = Map.copyOf(
                Objects.requireNonNull(countsByCalculationType, "countsByCalculationType"));
        countsByAcceptance = Map.copyOf(
                Objects.requireNonNull(countsByAcceptance, "countsByAcceptance"));
    }
}
