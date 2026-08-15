package totah.lab.prometheus.variational;

import java.util.List;
import java.util.Objects;

/** Immutable solver input; scientific safeguards are explicit acceptance gates. */
public record VariationalProblem(
        DifferentiableQuantumState initialState,
        Hamiltonian hamiltonian,
        VariationalFunctional functional,
        CollocationPointSet points,
        List<String> acceptanceGates,
        String scientificIdentity) {
    public VariationalProblem {
        Objects.requireNonNull(initialState, "initialState");
        Objects.requireNonNull(hamiltonian, "hamiltonian");
        Objects.requireNonNull(functional, "functional");
        Objects.requireNonNull(points, "points");
        acceptanceGates = List.copyOf(Objects.requireNonNull(acceptanceGates, "acceptanceGates"));
        if (acceptanceGates.isEmpty()) throw new IllegalArgumentException("acceptanceGates must be non-empty");
        Objects.requireNonNull(scientificIdentity, "scientificIdentity");
        if (!scientificIdentity.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("scientificIdentity must be lowercase SHA-256");
        }
    }
}
