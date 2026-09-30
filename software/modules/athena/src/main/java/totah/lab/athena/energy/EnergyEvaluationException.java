package totah.lab.athena.energy;

/** Checked failure: callers must not replace missing physics with a score. */
public final class EnergyEvaluationException extends Exception {
    private final FailureCode code;

    public EnergyEvaluationException(FailureCode code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    public FailureCode code() {
        return code;
    }

    public enum FailureCode {
        PARAMETERIZATION_UNAVAILABLE,
        ENGINE_UNAVAILABLE,
        STATE_INCOMPLETE,
        HAMILTONIANS_NOT_COMPARABLE,
        SAM_STATE_MISMATCH
    }
}
