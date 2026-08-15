package totah.lab.prometheus.execution.quantum;

import java.util.Objects;

import totah.lab.prometheus.execution.EvidenceExecutionException;

/** Clean domain-to-execution boundary; it cannot alter or rebuild a request. */
public final class QuantumExecutionService {
    private final QuantumBackendSelector selector;

    public QuantumExecutionService(QuantumBackendSelector selector) {
        this.selector = Objects.requireNonNull(selector, "selector");
    }

    public QuantumResult execute(QuantumExecutionRequest request) throws EvidenceExecutionException {
        Objects.requireNonNull(request, "request");
        QuantumBackend backend = selector.select(request);
        QuantumResult result = backend.execute(request);
        if (!result.scientificIdentity().equals(request.scientificIdentity())) {
            throw new EvidenceExecutionException("backend returned a result for a different scientific identity");
        }
        return result;
    }
}
