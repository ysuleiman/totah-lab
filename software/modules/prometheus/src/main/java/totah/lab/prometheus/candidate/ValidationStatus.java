package totah.lab.prometheus.candidate;

/** Validation lifecycle of a derived parameter. */
public enum ValidationStatus {
    UNVALIDATED,
    IN_VALIDATION,
    VALIDATED,
    REJECTED
}
