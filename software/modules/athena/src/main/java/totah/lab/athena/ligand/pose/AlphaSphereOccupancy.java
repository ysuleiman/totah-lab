package totah.lab.athena.ligand.pose;

/**
 * Raw alpha-sphere occupancy metrics of a predicted pose in one
 * candidate pocket. The nearest-sphere distance of a ligand heavy atom
 * is the signed surface distance
 * {@code max(0, d(atom, sphere.center) - sphere.radius)}, minimized over
 * all spheres of the pocket; an atom inside any sphere therefore has
 * distance {@code 0.0}.
 *
 * <p>These are raw geometric measurements only: no classifier or score
 * is hidden in them. {@code basisAvailable} is {@code false} when the
 * pocket carries no alpha spheres; all fractions and distances are then
 * {@code 0.0} and must not be read as evidence.
 */
public record AlphaSphereOccupancy(
        int sphereCount,
        boolean basisAvailable,
        double atomWithin2AOfSphereFraction,
        double atomWithin3AOfSphereFraction,
        double meanNearestSphereDistance,
        double maxNearestSphereDistance
) {
    public AlphaSphereOccupancy {
        if (sphereCount < 0) {
            throw new IllegalArgumentException(
                    "sphereCount must be non-negative"
            );
        }

        validateFraction(
                atomWithin2AOfSphereFraction,
                "atomWithin2AOfSphereFraction"
        );
        validateFraction(
                atomWithin3AOfSphereFraction,
                "atomWithin3AOfSphereFraction"
        );
        validateDistance(
                meanNearestSphereDistance,
                "meanNearestSphereDistance"
        );
        validateDistance(
                maxNearestSphereDistance,
                "maxNearestSphereDistance"
        );
    }

    private static void validateFraction(
            double value,
            String fieldName
    ) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0 and 1"
            );
        }
    }

    private static void validateDistance(
            double value,
            String fieldName
    ) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite and non-negative"
            );
        }
    }
}
