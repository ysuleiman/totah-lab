package totah.lab.athena.landscape;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Structure;

import java.util.Map;
import java.util.Objects;

/** One ligand configuration in a declared receptor frame. */
public record LigandConfiguration(
        String configurationId,
        String receptorId,
        String ligandId,
        Point3D translationAngstroms,
        EulerRotation rotationDegrees,
        Map<String, Double> torsionDegrees,
        Structure coordinateRealization,
        Origin origin,
        Map<String, String> provenance) {

    public LigandConfiguration {
        configurationId = require(configurationId, "configurationId");
        receptorId = require(receptorId, "receptorId");
        ligandId = require(ligandId, "ligandId");
        Objects.requireNonNull(translationAngstroms, "translationAngstroms");
        Objects.requireNonNull(rotationDegrees, "rotationDegrees");
        torsionDegrees = Map.copyOf(Objects.requireNonNull(torsionDegrees,
                "torsionDegrees"));
        torsionDegrees.forEach((key, value) -> {
            require(key, "torsion key");
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("torsions must be finite");
            }
        });
        Objects.requireNonNull(coordinateRealization, "coordinateRealization");
        Objects.requireNonNull(origin, "origin");
        provenance = Map.copyOf(Objects.requireNonNull(provenance, "provenance"));
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public record EulerRotation(double x, double y, double z) {
        public EulerRotation {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("rotation must be finite");
            }
        }
    }

    public enum Origin {
        DOCKING_POSE, DETERMINISTIC_PERTURBATION, MD_SNAPSHOT,
        MONTE_CARLO_STATE, IMPORTED_COORDINATES
    }
}
