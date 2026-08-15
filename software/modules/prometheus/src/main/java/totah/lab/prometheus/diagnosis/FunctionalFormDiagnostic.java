package totah.lab.prometheus.diagnosis;

import java.util.List;
import java.util.Objects;

/**
 * A single functional-form diagnosis. Following the Athena convention, every
 * diagnostic carries at least one explicit reason.
 *
 * <p>Diagnosis precedes fitting: Prometheus must be able to say "another value
 * for the same parameter type cannot solve this"
 * ({@link FunctionalFormClassification#HARMONIC_FORM_INSUFFICIENT} and friends)
 * as a first-class outcome, so effort is not wasted refitting parameters of a
 * functional form that is structurally incapable of matching the evidence.
 */
public record FunctionalFormDiagnostic(
        FunctionalFormClassification classification,
        List<String> reasons,
        List<String> supportingEvidenceHashes,
        String diagnosticVersion) {

    public FunctionalFormDiagnostic {
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(reasons, "reasons");
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("at least one reason is required");
        }
        for (String reason : reasons) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reasons must be non-blank");
            }
        }
        reasons = List.copyOf(reasons);
        supportingEvidenceHashes = List.copyOf(
                Objects.requireNonNull(supportingEvidenceHashes, "supportingEvidenceHashes"));
        requireNonBlank(diagnosticVersion, "diagnosticVersion");
    }

    /** Diagnosis stating that the evidence base is too thin to conclude anything. */
    public static FunctionalFormDiagnostic insufficientEvidence(String reason) {
        return new FunctionalFormDiagnostic(
                FunctionalFormClassification.INSUFFICIENT_EVIDENCE,
                List.of(Objects.requireNonNull(reason, "reason")),
                List.of(),
                "prometheus-0.1");
    }

    /** Diagnosis stating that the current model is acceptable as-is. */
    public static FunctionalFormDiagnostic modelAcceptable(String reason) {
        return new FunctionalFormDiagnostic(
                FunctionalFormClassification.MODEL_ACCEPTABLE,
                List.of(Objects.requireNonNull(reason, "reason")),
                List.of(),
                "prometheus-0.1");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
