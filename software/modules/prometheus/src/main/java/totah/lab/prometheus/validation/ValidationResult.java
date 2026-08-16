package totah.lab.prometheus.validation;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import totah.lab.prometheus.candidate.DecisionState;
import totah.lab.prometheus.candidate.ModelDecision;

/**
 * The terminal outcome of validating a frozen candidate against a preregistered
 * plan on the holdout.
 *
 * <p>A ValidationResult is <em>terminal</em>: it deliberately exposes no path
 * back to a mutable candidate — holdout validation cannot trigger an automatic
 * refit. Acting on a failed result means starting a new development cycle with
 * a new candidate, never mutating the frozen one.
 *
 * <p>Invariant (enforced in the constructor): a model is never promoted while
 * preregistered gates fail. If any gate failed, the decision state must be a
 * failure state; if all gates passed, it must be an accepting state.
 */
public record ValidationResult(
        String planChecksum,
        String freezeChecksum,
        List<GateOutcome> outcomes,
        ModelDecision decision,
        Instant executedAt) {

    private static final Set<DecisionState> FAILURE_STATES = EnumSet.of(
            DecisionState.FAILED_HOLDOUT,
            DecisionState.FUNCTIONAL_FORM_INSUFFICIENT,
            DecisionState.NONBONDED_MODEL_INSUFFICIENT,
            DecisionState.INSUFFICIENT_EVIDENCE,
            DecisionState.INCOMPATIBLE_WITH_PRODUCTION_STACK);

    private static final Set<DecisionState> ACCEPTING_STATES = EnumSet.of(
            DecisionState.VALIDATED_FOR_PRODUCTION,
            DecisionState.VALIDATED_WITH_LIMITATIONS,
            DecisionState.SUBSTANTIALLY_IMPROVED);

    public ValidationResult {
        requireNonBlank(planChecksum, "planChecksum");
        requireNonBlank(freezeChecksum, "freezeChecksum");
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        if (outcomes.isEmpty()) {
            throw new IllegalArgumentException(
                    "validation requires an outcome for every preregistered gate");
        }
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(executedAt, "executedAt");

        boolean anyFailed = outcomes.stream().anyMatch(outcome -> !outcome.passed());
        if (anyFailed && !FAILURE_STATES.contains(decision.state())) {
            throw new IllegalArgumentException(
                    "never promote while preregistered gates fail: decision state "
                            + decision.state() + " is not a failure state");
        }
        if (!anyFailed && !ACCEPTING_STATES.contains(decision.state())) {
            throw new IllegalArgumentException(
                    "all preregistered gates passed but decision state "
                            + decision.state() + " is not an accepting state");
        }
    }

    /** Builds the result of validating {@code frozen}, stamped now. */
    public static ValidationResult of(
            FrozenCandidate frozen,
            List<GateOutcome> outcomes,
            ModelDecision decision) {

        Objects.requireNonNull(frozen, "frozen");
        Objects.requireNonNull(outcomes, "outcomes");
        List<ValidationGate> gates = frozen.plan().gates();
        if (outcomes.size() != gates.size()) {
            throw new IllegalArgumentException(
                    "outcomes must match every preregistered gate exactly");
        }
        for (int index = 0; index < gates.size(); index++) {
            if (!gates.get(index).equals(outcomes.get(index).gate())) {
                throw new IllegalArgumentException(
                        "outcome gate does not match preregistered plan at index "
                                + index);
            }
        }
        return new ValidationResult(
                frozen.plan().planChecksum(),
                frozen.freezeChecksum(),
                outcomes,
                decision,
                Instant.now());
    }

    /** True when every gate outcome passed. */
    public boolean allPassed() {
        return outcomes.stream().allMatch(GateOutcome::passed);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
