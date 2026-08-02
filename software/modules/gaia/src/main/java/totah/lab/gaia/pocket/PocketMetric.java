package totah.lab.gaia.pocket;

import java.util.Objects;

public record PocketMetric(
        PocketMetricType type,
        double value) {

    public PocketMetric {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Pocket metric value must be finite.");
        }
    }
}
