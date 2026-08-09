package totah.lab.athena.ligand.pose;

/**
 * Configuration for pose-to-pocket assignment. The
 * {@code minimumAssignmentScore} floor and {@code ambiguityMargin} are
 * <b>calibration-pending</b> starting values; they exist so that weak
 * or tied evidence is reported honestly (NOT_ASSIGNED / AMBIGUOUS)
 * instead of forcing a match.
 */
public record PosePocketAssignmentOptions(
        double sphereToleranceAngstroms,
        double containmentRadius,
        double centroidReferenceDistance,
        double minimumAssignmentScore,
        double ambiguityMargin
) {

    public PosePocketAssignmentOptions {
        if (!Double.isFinite(sphereToleranceAngstroms)
                || sphereToleranceAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "sphereToleranceAngstroms must be finite and "
                            + "greater than zero"
            );
        }

        if (!Double.isFinite(containmentRadius)
                || containmentRadius <= 0.0) {
            throw new IllegalArgumentException(
                    "containmentRadius must be finite and greater "
                            + "than zero"
            );
        }

        if (!Double.isFinite(centroidReferenceDistance)
                || centroidReferenceDistance <= 0.0) {
            throw new IllegalArgumentException(
                    "centroidReferenceDistance must be finite and "
                            + "greater than zero"
            );
        }

        validateUnitInterval(
                minimumAssignmentScore,
                "minimumAssignmentScore"
        );
        validateUnitInterval(
                ambiguityMargin,
                "ambiguityMargin"
        );
    }

    public static PosePocketAssignmentOptions defaults() {
        return new PosePocketAssignmentOptions(
                2.0,
                3.0,
                25.0,
                0.10,
                0.05
        );
    }

    private static void validateUnitInterval(
            double value,
            String fieldName
    ) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0 and 1"
            );
        }
    }
}
