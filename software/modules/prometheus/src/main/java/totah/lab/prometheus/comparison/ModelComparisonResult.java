package totah.lab.prometheus.comparison;

import java.util.List;
import java.util.Objects;

/** Immutable comparison matrix. It intentionally has no score, rank, or winner. */
public record ModelComparisonResult(
        List<ModelReference> models,
        List<ModelComparisonRow> rows) {

    public ModelComparisonResult {
        models = List.copyOf(Objects.requireNonNull(models, "models"));
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    }

    public List<ModelComparisonRow> rowsFor(MetricDimension dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return rows.stream().filter(row -> row.metric().dimension() == dimension).toList();
    }
}
