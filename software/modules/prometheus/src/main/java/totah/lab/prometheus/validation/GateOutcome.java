package totah.lab.prometheus.validation;

import java.util.Objects;

/** Outcome of evaluating one preregistered gate against an observed value. */
public record GateOutcome(
        ValidationGate gate,
        double observedValue,
        boolean passed,
        String detail) {

    public GateOutcome {
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(detail, "detail");
        if (detail.isBlank()) {
            throw new IllegalArgumentException("detail must be non-blank");
        }
        boolean evaluated = gate.passes(observedValue);
        if (passed != evaluated) {
            throw new IllegalArgumentException(
                    "passed must equal gate.passes(observedValue)");
        }
    }
}
