package totah.lab.athena.landscape;

import totah.lab.gaia.geometry.Point3D;

import java.util.Map;
import java.util.Objects;

/** Reproducible coordinate displacement descriptor. */
public record Perturbation(
        String id,
        Point3D translationAngstroms,
        LigandConfiguration.EulerRotation rotationDegrees,
        Map<String, Double> torsionDeltaDegrees) {
    public Perturbation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(translationAngstroms, "translationAngstroms");
        Objects.requireNonNull(rotationDegrees, "rotationDegrees");
        torsionDeltaDegrees = Map.copyOf(Objects.requireNonNull(
                torsionDeltaDegrees, "torsionDeltaDegrees"));
        if (torsionDeltaDegrees.values().stream()
                .anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("torsion deltas must be finite");
        }
        if (torsionDeltaDegrees.keySet().stream()
                .anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("torsion identifiers must not be blank");
        }
    }
}
