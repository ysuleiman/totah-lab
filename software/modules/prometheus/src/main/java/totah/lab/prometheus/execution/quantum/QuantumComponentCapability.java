package totah.lab.prometheus.execution.quantum;

import java.util.Objects;

/** Per-observable, per-component execution capability; backend-wide labels are insufficient. */
public record QuantumComponentCapability(
        QuantumObservable observable,
        QuantumObservableComponent component) {
    public QuantumComponentCapability {
        Objects.requireNonNull(observable, "observable");
        Objects.requireNonNull(component, "component");
    }
}
