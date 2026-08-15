package totah.lab.prometheus.variational;

import java.util.List;
import java.util.Objects;

/** Immutable integration/collocation samples with explicit non-negative weights. */
public record CollocationPointSet(List<WeightedPoint> points, String provenanceHash) {
    public CollocationPointSet {
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        if (points.isEmpty()) throw new IllegalArgumentException("points must be non-empty");
        Objects.requireNonNull(provenanceHash, "provenanceHash");
        if (!provenanceHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("provenanceHash must be lowercase SHA-256");
        }
    }

    public record WeightedPoint(QuantumCoordinates coordinates, double weight) {
        public WeightedPoint {
            Objects.requireNonNull(coordinates, "coordinates");
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("weight must be finite and non-negative");
            }
        }
    }
}
