package totah.lab.prometheus.execution.quantum;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import totah.lab.prometheus.evidence.ConvergenceStatus;

/** Deeply immutable, unit-explicit result returned by a quantum backend. */
public record QuantumResult(
        String scientificIdentity,
        String backendId,
        String backendVersion,
        ConvergenceStatus convergence,
        Optional<Energy> energy,
        Optional<CartesianField> gradient,
        Optional<CartesianField> force,
        Map<String, String> artifactChecksums,
        Map<String, String> executionProvenance,
        Instant completedAt) {

    public QuantumResult {
        requireSha256(scientificIdentity, "scientificIdentity");
        requireNonBlank(backendId, "backendId");
        requireNonBlank(backendVersion, "backendVersion");
        Objects.requireNonNull(convergence, "convergence");
        energy = Objects.requireNonNull(energy, "energy");
        gradient = Objects.requireNonNull(gradient, "gradient");
        force = Objects.requireNonNull(force, "force");
        artifactChecksums = Map.copyOf(Objects.requireNonNull(artifactChecksums, "artifactChecksums"));
        executionProvenance = Map.copyOf(Objects.requireNonNull(executionProvenance, "executionProvenance"));
        Objects.requireNonNull(completedAt, "completedAt");
        artifactChecksums.values().forEach(value -> requireSha256(value, "artifact checksum"));
        if (gradient.isPresent() && force.isPresent()
                && gradient.orElseThrow().vectors().size() != force.orElseThrow().vectors().size()) {
            throw new IllegalArgumentException("gradient and force atom counts differ");
        }
    }

    public record Energy(double value, EnergyUnit unit) {
        public Energy { Objects.requireNonNull(unit, "unit"); }
    }

    public enum EnergyUnit { HARTREE, KCAL_PER_MOL }

    public record CartesianField(List<Vector3> vectors, CartesianUnit unit) {
        public CartesianField {
            vectors = List.copyOf(Objects.requireNonNull(vectors, "vectors"));
            Objects.requireNonNull(unit, "unit");
            if (vectors.isEmpty()) throw new IllegalArgumentException("Cartesian field must be non-empty");
        }
    }

    public enum CartesianUnit { HARTREE_PER_BOHR, KCAL_PER_MOL_ANGSTROM }

    public record Vector3(double x, double y, double z) { }

    public boolean forceIsNegativeGradient(double tolerance) {
        if (tolerance < 0.0) throw new IllegalArgumentException("tolerance must be non-negative");
        if (gradient.isEmpty() || force.isEmpty()) return false;
        CartesianField gradients = gradient.orElseThrow();
        CartesianField forces = force.orElseThrow();
        if (gradients.unit() != forces.unit()) return false;
        for (int i = 0; i < gradients.vectors().size(); i++) {
            Vector3 g = gradients.vectors().get(i); Vector3 f = forces.vectors().get(i);
            if (Math.abs(g.x() + f.x()) > tolerance || Math.abs(g.y() + f.y()) > tolerance
                    || Math.abs(g.z() + f.z()) > tolerance) return false;
        }
        return true;
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
    }

    private static void requireSha256(String value, String name) {
        requireNonBlank(value, name);
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be lowercase SHA-256");
    }
}
