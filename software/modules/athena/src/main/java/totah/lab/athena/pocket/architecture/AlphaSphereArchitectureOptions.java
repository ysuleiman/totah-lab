package totah.lab.athena.pocket.architecture;

/**
 * Thresholds for {@link AlphaSphereArchitectureAnalyzer}. Both values
 * are calibration-pending geometric conventions, documented on the
 * result record.
 */
public record AlphaSphereArchitectureOptions(
        double componentGapAngstroms,
        double uniqueSphereDistanceAngstroms
) {

    public AlphaSphereArchitectureOptions {
        if (!Double.isFinite(componentGapAngstroms)
                || componentGapAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "componentGapAngstroms must be finite and "
                            + "non-negative"
            );
        }

        if (!Double.isFinite(uniqueSphereDistanceAngstroms)
                || uniqueSphereDistanceAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "uniqueSphereDistanceAngstroms must be finite and "
                            + "greater than zero"
            );
        }
    }

    public static AlphaSphereArchitectureOptions defaults() {
        return new AlphaSphereArchitectureOptions(
                1.0,
                3.0
        );
    }
}
