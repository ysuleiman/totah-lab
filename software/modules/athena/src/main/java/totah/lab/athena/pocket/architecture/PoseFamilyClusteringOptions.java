package totah.lab.athena.pocket.architecture;

/**
 * Thresholds for {@link PoseFamilyClusterer}. Calibration-pending
 * conventions: the RMSD family threshold, and the sphere-occupancy
 * criterion used for the occupancy-overlap matrix (same defaults as
 * {@link LigandSpaceOptions}: a sphere is occupied when a ligand
 * heavy-atom center lies within
 * {@code fraction * radius + tolerance} of its center).
 */
public record PoseFamilyClusteringOptions(
        double rmsdThresholdAngstroms,
        double sphereOccupancyRadiusFraction,
        double sphereOccupancyToleranceAngstroms
) {

    public PoseFamilyClusteringOptions {
        if (!Double.isFinite(rmsdThresholdAngstroms)
                || rmsdThresholdAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "rmsdThresholdAngstroms must be finite and "
                            + "greater than zero"
            );
        }

        if (!Double.isFinite(sphereOccupancyRadiusFraction)
                || sphereOccupancyRadiusFraction <= 0.0
                || !Double.isFinite(sphereOccupancyToleranceAngstroms)
                || sphereOccupancyToleranceAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "Occupancy thresholds must be finite and positive "
                            + "(tolerance non-negative)"
            );
        }
    }

    public static PoseFamilyClusteringOptions defaults() {
        return new PoseFamilyClusteringOptions(
                2.0,
                1.0,
                0.0
        );
    }
}
