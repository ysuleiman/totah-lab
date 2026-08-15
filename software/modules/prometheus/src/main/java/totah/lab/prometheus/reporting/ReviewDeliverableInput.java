package totah.lab.prometheus.reporting;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.diagnosis.DiagnosisReport;
import totah.lab.prometheus.inventory.EvidenceInventorySnapshot;
import totah.lab.prometheus.planning.EvidenceGenerationPlan;
import totah.lab.prometheus.store.EvidenceMemoryIndex;

/** All precomputed inputs required to render the named TSL evidence package. */
public record ReviewDeliverableInput(
        EvidenceMemoryIndex evidence,
        EvidenceInventorySnapshot inventory,
        List<ProtocolGroupRow> protocolGroups,
        DiagnosisReport diagnosis,
        List<StrategyComparisonRow> strategyComparisons,
        EvidenceGenerationPlan missingEvidencePlan,
        List<String> costNotes,
        ExecutionDecisionRecord executionDecision) {

    public ReviewDeliverableInput {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(inventory, "inventory");
        protocolGroups = List.copyOf(Objects.requireNonNull(protocolGroups, "protocolGroups"));
        Objects.requireNonNull(diagnosis, "diagnosis");
        strategyComparisons = List.copyOf(
                Objects.requireNonNull(strategyComparisons, "strategyComparisons"));
        Objects.requireNonNull(missingEvidencePlan, "missingEvidencePlan");
        costNotes = List.copyOf(Objects.requireNonNull(costNotes, "costNotes"));
        if (costNotes.stream().anyMatch(note -> note == null || note.isBlank())) {
            throw new IllegalArgumentException("cost notes must be non-blank");
        }
        Objects.requireNonNull(executionDecision, "executionDecision");
    }
}
