package totah.lab.prometheus.variational;

/** Objective independent of the state representation and optimizer implementation. */
public interface VariationalFunctional {
    String functionalId();

    FunctionalEvaluation evaluate(DifferentiableQuantumState state, Hamiltonian hamiltonian,
            CollocationPointSet points);
}
