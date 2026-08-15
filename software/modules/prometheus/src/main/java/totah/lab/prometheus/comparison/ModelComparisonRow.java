package totah.lab.prometheus.comparison;

import java.util.List;
import java.util.Objects;

/** One scientific metric row; no cross-dimension aggregate is provided. */
public record ModelComparisonRow(
        ValidationMetricDefinition metric,
        List<ModelMetricCell> cells) {

    public ModelComparisonRow {
        Objects.requireNonNull(metric, "metric");
        cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
    }
}
