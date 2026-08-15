package totah.lab.prometheus.execution.quantum;

import totah.lab.prometheus.execution.EvidenceExecutionException;

/**
 * Pluggable Java execution boundary for molecular quantum calculations.
 * Implementations must be stateless and thread-safe. A backend has no authority
 * to modify the request's frozen scientific specification.
 */
public interface QuantumBackend {
    String backendId();

    QuantumBackendCapabilities capabilities();

    default boolean supports(QuantumExecutionRequest request) {
        return capabilities().satisfies(request);
    }

    QuantumResult execute(QuantumExecutionRequest request) throws EvidenceExecutionException;
}
