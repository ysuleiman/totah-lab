package totah.lab.prometheus.execution.quantum;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Component-aware capability declaration layered over the legacy backend-wide declaration.
 * Composite dispersion methods require independently declared electronic, dispersion, and
 * total capabilities for every requested decomposable observable.
 */
public record CompositeQuantumBackendCapabilities(
        QuantumBackendCapabilities backend,
        Set<QuantumComponentCapability> components) {
    private static final Set<QuantumObservable> DECOMPOSABLE = Set.of(
            QuantumObservable.ABSOLUTE_ENERGY,
            QuantumObservable.CARTESIAN_GRADIENT,
            QuantumObservable.CARTESIAN_FORCE,
            QuantumObservable.HESSIAN);

    public CompositeQuantumBackendCapabilities {
        Objects.requireNonNull(backend, "backend");
        components = Set.copyOf(Objects.requireNonNull(components, "components"));
    }

    public boolean satisfies(QuantumExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        if (!backend.satisfies(request)) return false;
        boolean composite = !request.specification().protocol().dispersion().equalsIgnoreCase("none");
        for (QuantumObservable observable : request.requiredObservables()) {
            if (!DECOMPOSABLE.contains(observable)) continue;
            Set<QuantumObservableComponent> required = composite
                    ? EnumSet.allOf(QuantumObservableComponent.class)
                    : EnumSet.of(QuantumObservableComponent.ELECTRONIC);
            for (QuantumObservableComponent component : required) {
                if (!components.contains(new QuantumComponentCapability(observable, component))) return false;
            }
        }
        return true;
    }
}
