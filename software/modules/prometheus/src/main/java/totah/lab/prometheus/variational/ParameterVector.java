package totah.lab.prometheus.variational;

import java.util.List;
import java.util.Objects;

/** Immutable parameters of a trial-state representation. */
public record ParameterVector(List<Double> values) {
    public ParameterVector {
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("parameter values must not contain null");
        }
    }
}
