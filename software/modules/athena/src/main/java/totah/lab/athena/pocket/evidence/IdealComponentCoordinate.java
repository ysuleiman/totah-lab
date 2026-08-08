package totah.lab.athena.pocket.evidence;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/** CCD idealized coordinate, deliberately distinct from bound observations. */
public record IdealComponentCoordinate(String atomId, Point3D position) {
    public IdealComponentCoordinate {
        Objects.requireNonNull(atomId, "atomId");
        Objects.requireNonNull(position, "position");
        if (atomId.isBlank()) {
            throw new IllegalArgumentException("atomId must not be blank");
        }
        atomId = atomId.trim();
    }
}
