package totah.lab.athena.pocket.architecture;

/**
 * Region definition and probe thresholds for
 * {@link InterPoseRegionAnalyzer}. A receptor residue belongs to the
 * inter-pose region when any of its heavy atoms lies within
 * {@code regionRadiusAngstroms} of any heavy atom of either pose, or
 * within {@code corridorRadiusAngstroms} of the segment connecting
 * the two pose centroids. Calibration-pending conventions.
 */
public record InterPoseRegionOptions(
        double regionRadiusAngstroms,
        double corridorRadiusAngstroms,
        double probeRadiusAngstroms
) {

    public InterPoseRegionOptions {
        if (!Double.isFinite(regionRadiusAngstroms)
                || regionRadiusAngstroms <= 0.0
                || !Double.isFinite(corridorRadiusAngstroms)
                || corridorRadiusAngstroms <= 0.0
                || !Double.isFinite(probeRadiusAngstroms)
                || probeRadiusAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "Region thresholds must be finite and greater "
                            + "than zero"
            );
        }
    }

    public static InterPoseRegionOptions defaults() {
        return new InterPoseRegionOptions(
                4.0,
                4.0,
                1.4
        );
    }
}
