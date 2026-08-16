package totah.lab.prometheus.execution;

/**
 * Permanent production boundary: Prometheus must not launch Python processes.
 * Historical artifacts may be read for provenance, but they cannot authorize
 * or trigger Python execution.
 */
final class ExternalPythonExecutionPolicy {
    static final String DISABLED_REASON =
            "external Python execution is disabled; use a qualified Java implementation";

    private ExternalPythonExecutionPolicy() {
    }

    static EvidenceExecutionException disabled(String executorId) {
        return new EvidenceExecutionException(executorId + ": " + DISABLED_REASON);
    }
}
