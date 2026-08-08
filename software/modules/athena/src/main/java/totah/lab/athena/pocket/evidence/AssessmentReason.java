package totah.lab.athena.pocket.evidence;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One explicit, machine-identifiable reason supporting an assessment. */
public record AssessmentReason(
        String code,
        String dimension,
        String explanation,
        Map<String, String> details
) {
    public AssessmentReason {
        code = requireText(code, "code");
        dimension = requireText(dimension, "dimension");
        explanation = requireText(explanation, "explanation");
        details = Map.copyOf(new TreeMap<>(
                Objects.requireNonNull(details, "details")));
    }

    public AssessmentReason(String code, String dimension, String explanation) {
        this(code, dimension, explanation, Map.of());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
