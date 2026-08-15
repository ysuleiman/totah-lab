package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Objects;

/**
 * Electronic-state and output details layered onto the stable legacy
 * {@link EvidenceRequirement} API. This makes planning molecule-agnostic while
 * retaining source compatibility for existing neutral-singlet callers.
 */
public record ScientificEvidenceRequirement(
        EvidenceRequirement requirement,
        int formalCharge,
        int multiplicity,
        List<String> constraints,
        List<String> requestedOutputs,
        List<String> acceptanceGates) {

    public ScientificEvidenceRequirement {
        Objects.requireNonNull(requirement, "requirement");
        if (multiplicity < 1) {
            throw new IllegalArgumentException("multiplicity must be >= 1");
        }
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        requestedOutputs = List.copyOf(Objects.requireNonNull(requestedOutputs, "requestedOutputs"));
        acceptanceGates = List.copyOf(Objects.requireNonNull(acceptanceGates, "acceptanceGates"));
        if (requestedOutputs.isEmpty()) {
            throw new IllegalArgumentException("requestedOutputs must not be empty");
        }
        if (acceptanceGates.isEmpty()) {
            throw new IllegalArgumentException("acceptanceGates must not be empty");
        }
    }

    public static ScientificEvidenceRequirement neutralSinglet(EvidenceRequirement requirement) {
        return new ScientificEvidenceRequirement(
                requirement,
                0,
                1,
                EvidencePlanner.DEFAULT_CONSTRAINTS,
                EvidencePlanner.DEFAULT_REQUESTED_OUTPUTS,
                EvidencePlanner.DEFAULT_ACCEPTANCE_GATES);
    }
}
