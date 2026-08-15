package totah.lab.prometheus.execution.quantum;

/** Scientifically distinct solver architectures; never collapse them into a generic ML backend. */
public enum QuantumSolverMode {
    CONVENTIONAL_ELECTRONIC_STRUCTURE,
    ANN_ASSISTED_CONVENTIONAL,
    POTENTIAL_ENERGY_SURROGATE,
    VARIATIONAL_QUANTUM_STATE
}
