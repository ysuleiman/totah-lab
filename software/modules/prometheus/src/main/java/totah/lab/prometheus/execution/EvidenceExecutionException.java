package totah.lab.prometheus.execution;

/**
 * Checked exception for execution-boundary failures: no executor supports a
 * specification, an engine is not installed/configured, or an execution fails.
 */
public final class EvidenceExecutionException extends Exception {

    public EvidenceExecutionException(String message) {
        super(message);
    }

    public EvidenceExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
