package totah.lab.athena.tmt;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/** Geometry for S(substrate) ... C(methyl) - S(SAM), computed from Gaia coordinates. */
public record NearAttackGeometry(
        double substrateSulfurToMethylCarbonAngstrom,
        double substrateSulfurMethylCarbonSamSulfurAngleDegrees,
        double methylCarbonToSamSulfurAngstrom,
        int severeClashCount) {

    public NearAttackGeometry {
        requireFiniteNonNegative(substrateSulfurToMethylCarbonAngstrom, "substrate distance");
        requireFiniteNonNegative(substrateSulfurMethylCarbonSamSulfurAngleDegrees, "attack angle");
        requireFiniteNonNegative(methylCarbonToSamSulfurAngstrom, "donor bond distance");
        if (substrateSulfurMethylCarbonSamSulfurAngleDegrees > 180.0) {
            throw new IllegalArgumentException("attack angle cannot exceed 180 degrees");
        }
        if (severeClashCount < 0) {
            throw new IllegalArgumentException("severeClashCount must be non-negative");
        }
    }

    public static NearAttackGeometry from(
            Point3D substrateSulfur,
            Point3D methylCarbon,
            Point3D samSulfur,
            int severeClashCount) {
        Objects.requireNonNull(substrateSulfur, "substrateSulfur");
        Objects.requireNonNull(methylCarbon, "methylCarbon");
        Objects.requireNonNull(samSulfur, "samSulfur");
        double distance = substrateSulfur.distance(methylCarbon);
        double donorDistance = methylCarbon.distance(samSulfur);
        double angle = angleDegrees(
                methylCarbon.vectorTo(substrateSulfur).x(),
                methylCarbon.vectorTo(substrateSulfur).y(),
                methylCarbon.vectorTo(substrateSulfur).z(),
                methylCarbon.vectorTo(samSulfur).x(),
                methylCarbon.vectorTo(samSulfur).y(),
                methylCarbon.vectorTo(samSulfur).z());
        return new NearAttackGeometry(distance, angle, donorDistance, severeClashCount);
    }

    private static double angleDegrees(double ax, double ay, double az, double bx, double by, double bz) {
        double aMagnitude = Math.sqrt(ax * ax + ay * ay + az * az);
        double bMagnitude = Math.sqrt(bx * bx + by * by + bz * bz);
        if (aMagnitude == 0.0 || bMagnitude == 0.0) {
            throw new IllegalArgumentException("angle vectors must have non-zero magnitude");
        }
        double cosine = (ax * bx + ay * by + az * bz) / (aMagnitude * bMagnitude);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
