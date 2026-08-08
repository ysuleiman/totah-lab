package totah.lab.athena.pocket.evidence;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Provider-reported confidence values for a predicted structure model. */
public record PredictedModelConfidence(
        String confidenceType,
        Map<String, Double> reportedValues
) {
    public PredictedModelConfidence {
        confidenceType = requireText(confidenceType, "confidenceType");
        reportedValues = Map.copyOf(new TreeMap<>(
                Objects.requireNonNull(reportedValues, "reportedValues")));
        reportedValues.forEach((name, value) -> {
            requireText(name, "confidence value name");
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "confidence values must be finite");
            }
        });
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
