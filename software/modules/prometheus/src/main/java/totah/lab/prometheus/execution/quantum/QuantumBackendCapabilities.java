package totah.lab.prometheus.execution.quantum;

import java.util.Objects;
import java.util.Set;

import totah.lab.prometheus.evidence.CalculationType;

/** Immutable declaration of what a backend can execute without substitution. */
public record QuantumBackendCapabilities(
        Set<QuantumSolverMode> solverModes,
        Set<CalculationType> calculationTypes,
        Set<QuantumObservable> observables,
        boolean supportsConstraints) {

    public QuantumBackendCapabilities {
        solverModes = Set.copyOf(Objects.requireNonNull(solverModes, "solverModes"));
        calculationTypes = Set.copyOf(Objects.requireNonNull(calculationTypes, "calculationTypes"));
        observables = Set.copyOf(Objects.requireNonNull(observables, "observables"));
        if (solverModes.isEmpty()) {
            throw new IllegalArgumentException("solverModes must be non-empty");
        }
        if (calculationTypes.isEmpty()) {
            throw new IllegalArgumentException("calculationTypes must be non-empty");
        }
        if (observables.isEmpty()) {
            throw new IllegalArgumentException("observables must be non-empty");
        }
    }

    public boolean satisfies(QuantumExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        return solverModes.contains(request.solverMode())
                && calculationTypes.contains(request.specification().calculationType())
                && observables.containsAll(request.requiredObservables())
                && (supportsConstraints || request.specification().constraints().isEmpty());
    }
}
