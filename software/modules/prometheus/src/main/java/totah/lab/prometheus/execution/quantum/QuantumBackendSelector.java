package totah.lab.prometheus.execution.quantum;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.execution.EvidenceExecutionException;

/** Immutable, capability-based selector; no global registry and no backend enum. */
public final class QuantumBackendSelector {
    private final List<QuantumBackend> backends;

    public QuantumBackendSelector(List<QuantumBackend> backends) {
        this.backends = List.copyOf(Objects.requireNonNull(backends, "backends"));
        if (this.backends.stream().map(QuantumBackend::backendId).distinct().count() != this.backends.size()) {
            throw new IllegalArgumentException("backend ids must be unique");
        }
    }

    public QuantumBackend select(QuantumExecutionRequest request) throws EvidenceExecutionException {
        Objects.requireNonNull(request, "request");
        List<String> preferred = request.options().preferredBackendIds();
        return backends.stream()
                .filter(backend -> backend.supports(request))
                .min(Comparator.comparingInt(backend -> preferenceIndex(preferred, backend.backendId())))
                .orElseThrow(() -> new EvidenceExecutionException(
                        "no Java quantum backend satisfies specification " + request.specification().specificationId()));
    }

    private static int preferenceIndex(List<String> preferred, String backendId) {
        int index = preferred.indexOf(backendId);
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
