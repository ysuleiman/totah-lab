package totah.lab.prometheus.comparison;

import java.util.Objects;

/** One preregistered metric under one protocol and validation definition. */
public record ValidationMetricDefinition(
        MetricDimension dimension,
        String metricId,
        String unit,
        String protocolKey,
        String validationDefinitionChecksum) {

    public ValidationMetricDefinition {
        Objects.requireNonNull(dimension, "dimension");
        metricId = requireNonBlank(metricId, "metricId");
        unit = requireNonBlank(unit, "unit");
        protocolKey = requireNonBlank(protocolKey, "protocolKey");
        validationDefinitionChecksum = requireNonBlank(
                validationDefinitionChecksum, "validationDefinitionChecksum");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
