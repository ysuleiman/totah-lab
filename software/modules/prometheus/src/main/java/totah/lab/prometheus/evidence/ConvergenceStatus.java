package totah.lab.prometheus.evidence;

/** Outcome of a calculation's convergence / output completeness. */
public enum ConvergenceStatus {
    CONVERGED,
    NOT_CONVERGED,
    FAILED,
    EMPTY_OUTPUT,
    UNKNOWN
}
