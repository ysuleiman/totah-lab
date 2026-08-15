package totah.lab.prometheus.variational;

import java.util.List;
import java.util.Objects;

/** Three complex spatial derivatives per ordered particle. */
public record StateGradient(List<Vector3> particleGradients) {
    public StateGradient {
        particleGradients = List.copyOf(Objects.requireNonNull(particleGradients, "particleGradients"));
    }

    public record Vector3(QuantumAmplitude x, QuantumAmplitude y, QuantumAmplitude z) {
        public Vector3 {
            Objects.requireNonNull(x, "x"); Objects.requireNonNull(y, "y"); Objects.requireNonNull(z, "z");
        }
    }
}
