package totah.lab.prometheus.variational;

import java.util.List;
import java.util.Objects;

/** Immutable, ordered many-particle configuration in bohr. */
public record QuantumCoordinates(List<ParticleCoordinate> particles) {
    public QuantumCoordinates {
        particles = List.copyOf(Objects.requireNonNull(particles, "particles"));
        if (particles.isEmpty()) throw new IllegalArgumentException("particles must be non-empty");
        for (int i = 0; i < particles.size(); i++) {
            if (particles.get(i).particleIndex() != i) {
                throw new IllegalArgumentException("particle indices must be contiguous and ordered");
            }
        }
    }

    public record ParticleCoordinate(int particleIndex, double xBohr, double yBohr, double zBohr,
            SpinProjection spin) {
        public ParticleCoordinate {
            if (particleIndex < 0) throw new IllegalArgumentException("particleIndex must be non-negative");
            Objects.requireNonNull(spin, "spin");
        }
    }
}
