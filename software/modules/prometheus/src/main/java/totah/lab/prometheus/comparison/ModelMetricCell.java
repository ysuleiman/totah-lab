package totah.lab.prometheus.comparison;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** One model's value or explicit non-value for one comparison row. */
public record ModelMetricCell(
        ModelReference model,
        ComparisonCellState state,
        OptionalDouble value,
        List<String> provenanceReferences,
        String reason) {

    public ModelMetricCell {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(state, "state");
        value = Objects.requireNonNull(value, "value");
        provenanceReferences = List.copyOf(
                Objects.requireNonNull(provenanceReferences, "provenanceReferences"));
        Objects.requireNonNull(reason, "reason");
        if (state == ComparisonCellState.EVALUATED && value.isEmpty()) {
            throw new IllegalArgumentException("evaluated comparison cell requires a value");
        }
        if (state != ComparisonCellState.EVALUATED && value.isPresent()) {
            throw new IllegalArgumentException("non-evaluated comparison cell must not carry a value");
        }
        if (state != ComparisonCellState.EVALUATED && reason.isBlank()) {
            throw new IllegalArgumentException("non-evaluated comparison cell requires a reason");
        }
    }
}
