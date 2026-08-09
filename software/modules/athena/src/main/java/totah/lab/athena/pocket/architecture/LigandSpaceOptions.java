package totah.lab.athena.pocket.architecture;

/**
 * Thresholds for the ligand-space analysis and comparison. All values
 * are calibration-pending geometric conventions, documented on the
 * result records.
 *
 * <p>Sphere occupancy: a sphere counts as occupied when a ligand
 * heavy-atom CENTER lies within
 * {@code sphereOccupancyRadiusFraction * radius
 * + sphereOccupancyToleranceAngstroms} of the sphere center. The
 * defaults (fraction 1.0, tolerance 0.0) mean "atom inside the
 * sphere" — a discriminating criterion that stays informative at the
 * large fpocket sphere radii, where the former surface-distance
 * criterion (surface within 2 A) saturated: with ~4 A spheres, nearly
 * every sphere of an occupied pocket satisfied it. The old behavior
 * remains available via fraction 1.0 + tolerance 2.0.
 */
public record LigandSpaceOptions(
        double probeRadiusAngstroms,
        double sphereOccupancyRadiusFraction,
        double sphereOccupancyToleranceAngstroms,
        double lateralShiftAngstroms,
        double depthDifferenceAngstroms,
        double mouthDifferenceAngstroms,
        double wallShiftAngstroms
) {

    public LigandSpaceOptions {
        if (!Double.isFinite(probeRadiusAngstroms)
                || probeRadiusAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "probeRadiusAngstroms must be finite and greater "
                            + "than zero"
            );
        }

        if (!Double.isFinite(sphereOccupancyRadiusFraction)
                || sphereOccupancyRadiusFraction <= 0.0) {
            throw new IllegalArgumentException(
                    "sphereOccupancyRadiusFraction must be finite and "
                            + "greater than zero"
            );
        }

        if (!Double.isFinite(sphereOccupancyToleranceAngstroms)
                || sphereOccupancyToleranceAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "sphereOccupancyToleranceAngstroms must be finite "
                            + "and non-negative"
            );
        }

        if (!Double.isFinite(lateralShiftAngstroms)
                || lateralShiftAngstroms < 0.0
                || !Double.isFinite(depthDifferenceAngstroms)
                || depthDifferenceAngstroms < 0.0
                || !Double.isFinite(mouthDifferenceAngstroms)
                || mouthDifferenceAngstroms < 0.0
                || !Double.isFinite(wallShiftAngstroms)
                || wallShiftAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "Difference thresholds must be finite and "
                            + "non-negative"
            );
        }
    }

    public static LigandSpaceOptions defaults() {
        return new LigandSpaceOptions(
                1.4,
                1.0,
                0.0,
                1.5,
                1.5,
                1.5,
                1.0
        );
    }
}
