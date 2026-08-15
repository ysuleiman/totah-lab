package totah.lab.prometheus.variational;

/** Representation-independent Hamiltonian action on a state. */
public interface Hamiltonian {
    String scientificIdentity();

    QuantumAmplitude apply(QuantumState state, QuantumCoordinates coordinates);
}
