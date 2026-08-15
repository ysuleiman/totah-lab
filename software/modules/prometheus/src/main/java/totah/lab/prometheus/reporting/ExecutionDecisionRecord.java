package totah.lab.prometheus.reporting;

import java.util.List;
import java.util.Objects;

/** Explicit execution decision serialized verbatim; the renderer never authorizes work. */
public record ExecutionDecisionRecord(
        String decisionId,
        String status,
        boolean authorized,
        List<String> reasons,
        List<String> evidenceReferences) {

    public ExecutionDecisionRecord {
        decisionId = requireNonBlank(decisionId, "decisionId");
        status = requireNonBlank(status, "status");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        evidenceReferences = List.copyOf(
                Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
        if (reasons.isEmpty() || reasons.stream().anyMatch(reason -> reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("at least one non-blank reason is required");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
