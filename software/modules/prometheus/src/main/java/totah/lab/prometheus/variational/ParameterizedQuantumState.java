package totah.lab.prometheus.variational;

/** Immutable parameterized state; updates produce a new state instance. */
public interface ParameterizedQuantumState extends QuantumState {
    ParameterVector parameters();

    ParameterizedQuantumState withParameters(ParameterVector parameters);
}
