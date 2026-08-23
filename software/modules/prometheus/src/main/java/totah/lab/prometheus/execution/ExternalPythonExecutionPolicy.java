package totah.lab.prometheus.execution;

/**
 * Prometheus boundary for external numerical processes. Historical adapters
 * remain disabled. The sole exception is the hardened, Java-specified TSL-RSH
 * force-cloud worker; it has no authority to select or alter a protocol.
 */
final class ExternalPythonExecutionPolicy {
    static final String DISABLED_REASON =
            "external Python execution is disabled; use a qualified Java implementation";
    static final String HARDENED_TSLRSH_WORKER = "hardened-tslrsh-pyscf-energy-gradient";

    private ExternalPythonExecutionPolicy() {
    }

    static EvidenceExecutionException disabled(String executorId) {
        return new EvidenceExecutionException(executorId + ": " + DISABLED_REASON);
    }

    static void requireAuthorizedNumericalWorker(String executorId) throws EvidenceExecutionException {
        if (!HARDENED_TSLRSH_WORKER.equals(executorId)) {
            throw disabled(executorId);
        }
    }
}
