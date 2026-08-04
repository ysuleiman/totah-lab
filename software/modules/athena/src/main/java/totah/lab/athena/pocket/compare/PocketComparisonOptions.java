package totah.lab.athena.pocket.compare;

/**
 * Configuration for point-cloud pocket comparison.
 */
public record PocketComparisonOptions(
        double matchCutoffAngstroms,
        double geometryWeight,
        double sizeWeight
) {

    public PocketComparisonOptions {
        if (!Double.isFinite(matchCutoffAngstroms)
                || matchCutoffAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "Match cutoff must be finite and greater than zero"
            );
        }

        validateWeight(geometryWeight, "geometryWeight");
        validateWeight(sizeWeight, "sizeWeight");

        double sum = geometryWeight + sizeWeight;

        if (Math.abs(sum - 1.0) > 1.0e-9) {
            throw new IllegalArgumentException(
                    "Comparison weights must sum to 1.0: " + sum
            );
        }
    }

    public static PocketComparisonOptions defaults() {
        return new PocketComparisonOptions(
                2.0,
                0.85,
                0.15
        );
    }

    private static void validateWeight(
            double weight,
            String name
    ) {
        if (!Double.isFinite(weight)
                || weight < 0.0
                || weight > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0 and 1"
            );
        }
    }
}
