package totah.lab.prometheus.variational;

import java.util.Objects;

/** Sum of spatial second derivatives of a state at one configuration. */
public record StateLaplacian(QuantumAmplitude value) {
    public StateLaplacian { Objects.requireNonNull(value, "value"); }
}
