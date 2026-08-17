package totah.lab.athena.ligand.screening;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Rejects isolated stochastic poses and repeatedly escaping pose ensembles. */
public final class PoseReproducibilityGate {

    public record Evidence(int independentAttempts,
                           int canonicalFamilyMembers,
                           boolean repeatedPocketEscape) {
        public Evidence {
            if (independentAttempts < 1 || canonicalFamilyMembers < 0
                    || canonicalFamilyMembers > independentAttempts) {
                throw new IllegalArgumentException("invalid pose-family counts");
            }
        }
    }

    public record Policy(int minimumIndependentAttempts,
                         int minimumCanonicalFamilyMembers) {
        public Policy {
            if (minimumIndependentAttempts < 1
                    || minimumCanonicalFamilyMembers < 2) {
                throw new IllegalArgumentException("invalid reproducibility policy");
            }
        }

        public static Policy mettl7Discovery() {
            return new Policy(2, 2);
        }
    }

    public record Result(boolean accepted, List<String> reasons) {
        public Result {
            reasons = List.copyOf(reasons);
        }
    }

    private final Policy policy;

    public PoseReproducibilityGate() {
        this(Policy.mettl7Discovery());
    }

    public PoseReproducibilityGate(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public Result evaluate(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        List<String> reasons = new ArrayList<>();
        if (evidence.independentAttempts() < policy.minimumIndependentAttempts()) {
            reasons.add("insufficient independent docking attempts");
        }
        if (evidence.canonicalFamilyMembers()
                < policy.minimumCanonicalFamilyMembers()) {
            reasons.add("canonical orientation is an isolated stochastic pose");
        }
        if (evidence.repeatedPocketEscape()) {
            reasons.add("poses repeatedly escape the canonical pocket");
        }
        return new Result(reasons.isEmpty(), reasons);
    }
}
