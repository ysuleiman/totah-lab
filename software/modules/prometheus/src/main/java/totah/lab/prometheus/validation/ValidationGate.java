package totah.lab.prometheus.validation;

import java.util.Objects;

/**
 * A single preregistered pass/fail criterion: {@code metric} must satisfy
 * {@code comparison} against {@code threshold}.
 */
public record ValidationGate(
        String gateId,
        String description,
        String metric,
        double threshold,
        Comparison comparison) {

    public ValidationGate {
        requireNonBlank(gateId, "gateId");
        requireNonBlank(description, "description");
        requireNonBlank(metric, "metric");
        Objects.requireNonNull(comparison, "comparison");
    }

    /** True when the observed metric value satisfies this gate. */
    public boolean passes(double observed) {
        return switch (comparison) {
            case AT_MOST -> observed <= threshold;
            case AT_LEAST -> observed >= threshold;
        };
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
