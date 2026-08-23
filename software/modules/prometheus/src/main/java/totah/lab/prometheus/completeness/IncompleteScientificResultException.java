package totah.lab.prometheus.completeness;

/** Raised by publication/qualification paths when the persisted result bundle is incomplete. */
public final class IncompleteScientificResultException extends IllegalStateException {
    private final ScientificResultCompletenessValidator.ValidationResult validationResult;

    public IncompleteScientificResultException(String resultId,
            ScientificResultCompletenessValidator.ValidationResult validationResult) {
        super("scientific result " + resultId + " is not reproducible: "
                + validationResult.status() + " " + validationResult.issues());
        this.validationResult = validationResult;
    }

    public ScientificResultCompletenessValidator.ValidationResult validationResult() {
        return validationResult;
    }
}
