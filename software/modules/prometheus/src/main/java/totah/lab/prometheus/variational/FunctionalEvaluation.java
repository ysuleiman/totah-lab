package totah.lab.prometheus.variational;

import java.util.Map;
import java.util.Objects;

/** Immutable objective value and separately reported scientific terms. */
public record FunctionalEvaluation(double objective, Map<String, Double> terms) {
    public FunctionalEvaluation {
        if (!Double.isFinite(objective)) throw new IllegalArgumentException("objective must be finite");
        terms = Map.copyOf(Objects.requireNonNull(terms, "terms"));
    }
}
