package totah.lab.pocket.visualization.analysis;

import totah.lab.gaia.geometry.Point3D;

public record PocketOpening(
        Kind kind,
        Point3D center,
        Point3D direction,
        double radius,
        double clearance) {

    public enum Kind {
        MOUTH,
        SECONDARY_OPENING
    }
}
