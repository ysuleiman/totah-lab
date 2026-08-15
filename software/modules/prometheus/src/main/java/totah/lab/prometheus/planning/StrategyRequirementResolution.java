package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Objects;

/** Evidence matcher result with explicit scientific and infrastructure reasoning. */
public record StrategyRequirementResolution(
        StrategyEvidenceRequirement requirement,
        EvidenceReuseDecision decision,
        List<String> evidenceHashes,
        String scientificReason,
        String infrastructureReason) {
    public StrategyRequirementResolution {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(decision, "decision");
        evidenceHashes = List.copyOf(Objects.requireNonNull(evidenceHashes, "evidenceHashes"));
        scientificReason = require(scientificReason, "scientificReason");
        infrastructureReason = Objects.requireNonNull(infrastructureReason, "infrastructureReason");
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
        return value;
    }
}
