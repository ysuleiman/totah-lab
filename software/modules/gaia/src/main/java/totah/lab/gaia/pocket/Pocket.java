package totah.lab.gaia.pocket;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public record Pocket(
        PocketId id,
        String name,
        PocketSource source,
        Point3D center,
        List<ResidueId> residues,
        List<PocketMetric> metrics,
        Optional<BoundingBox> bounds,
        Optional<AlphaSphereSet> alphaSphereSet,
        Map<String, String> metadata) {

    public Pocket {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(center, "center");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Pocket name must not be blank.");
        }
        residues = copyWithoutNulls(residues, "residues");
        metrics = copyWithoutNulls(metrics, "metrics");
        bounds = Objects.requireNonNull(bounds, "bounds");
        alphaSphereSet = Objects.requireNonNull(alphaSphereSet, "alphaSphereSet");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    public OptionalDouble metric(PocketMetricType type) {
        Objects.requireNonNull(type, "type");
        return metrics.stream()
                .filter(metric -> metric.type() == type)
                .mapToDouble(PocketMetric::value)
                .findFirst();
    }

    private static <T> List<T> copyWithoutNulls(
            List<T> values,
            String fieldName) {

        List<T> copy = List.copyOf(
                Objects.requireNonNull(values, fieldName));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    fieldName + " must not contain null elements.");
        }
        return copy;
    }
}
