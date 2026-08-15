package totah.lab.prometheus.variational;

/** Representation-independent quantum state evaluated at one configuration. */
public interface QuantumState {
    String representationId();

    QuantumAmplitude value(QuantumCoordinates coordinates);
}
