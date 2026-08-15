package totah.lab.prometheus.execution.quantum;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import totah.lab.prometheus.ingest.authoritative.CartesianGeometry;
import totah.lab.prometheus.planning.CalculationSpecification;

/** Immutable domain-to-execution handoff for one already-authorized calculation. */
public record QuantumExecutionRequest(
        CalculationSpecification specification,
        CartesianGeometry geometry,
        String canonicalAtomMapHash,
        QuantumSolverMode solverMode,
        Set<QuantumObservable> requiredObservables,
        QuantumExecutionOptions options) {

    public QuantumExecutionRequest {
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(geometry, "geometry");
        requireSha256(canonicalAtomMapHash, "canonicalAtomMapHash");
        Objects.requireNonNull(solverMode, "solverMode");
        requiredObservables = Set.copyOf(Objects.requireNonNull(requiredObservables, "requiredObservables"));
        Objects.requireNonNull(options, "options");
        if (requiredObservables.isEmpty()) {
            throw new IllegalArgumentException("requiredObservables must be non-empty");
        }
        if (geometry.atoms().size() != specification.geometry().atomCount()) {
            throw new IllegalArgumentException("geometry atom count does not match specification");
        }
    }

    public String scientificIdentity() {
        return QuantumScientificIdentity.calculate(
                specification, canonicalAtomMapHash, solverMode, requiredObservables);
    }

    public static Set<QuantumObservable> energyAndForces() {
        return Set.copyOf(EnumSet.of(QuantumObservable.ABSOLUTE_ENERGY,
                QuantumObservable.CARTESIAN_GRADIENT, QuantumObservable.CARTESIAN_FORCE));
    }

    private static void requireSha256(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }
}
