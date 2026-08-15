package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Objects;

/**
 * The planner's verdict on a single {@link EvidenceRequirement}, with the
 * evidence hashes that can be reused (empty unless the decision is
 * {@link PlanDecision#REUSE_EXISTING}) and a non-blank human-readable reason.
 */
public record RequirementResolution(
        EvidenceRequirement requirement,
        PlanDecision decision,
        List<String> reusableEvidenceHashes,
        String reason) {

    public RequirementResolution {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(decision, "decision");
        reusableEvidenceHashes = List.copyOf(
                Objects.requireNonNull(reusableEvidenceHashes, "reusableEvidenceHashes"));
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must be non-blank");
        }
    }
}
