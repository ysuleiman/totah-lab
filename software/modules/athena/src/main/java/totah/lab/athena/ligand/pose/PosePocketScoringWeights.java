package totah.lab.athena.ligand.pose;

/**
 * Weights of the {@link DefaultPosePocketScorer}: occupancy (alpha-sphere
 * or fallback containment) is the dominant signal, contact-residue
 * coverage second, and centroid proximity the weakest — it can never
 * win an assignment on its own.
 *
 * <p>The {@link #defaults()} values are the spec's starting point and
 * are <b>calibration-pending</b>: they have not been tuned against any
 * benchmark result and must not be adjusted toward an expected outcome
 * without explicit instruction.
 */
public record PosePocketScoringWeights(
        double occupancyWeight,
        double contactCoverageWeight,
        double centroidProximityWeight
) {

    public PosePocketScoringWeights {
        validateWeight(occupancyWeight, "occupancyWeight");
        validateWeight(contactCoverageWeight, "contactCoverageWeight");
        validateWeight(centroidProximityWeight, "centroidProximityWeight");

        double sum = occupancyWeight
                + contactCoverageWeight
                + centroidProximityWeight;

        if (Math.abs(sum - 1.0) > 1.0e-9) {
            throw new IllegalArgumentException(
                    "Scoring weights must sum to 1.0: " + sum
            );
        }
    }

    /**
     * Uncalibrated starting weights: 0.50 occupancy, 0.35 contact
     * coverage, 0.15 centroid proximity.
     */
    public static PosePocketScoringWeights defaults() {
        return new PosePocketScoringWeights(
                0.50,
                0.35,
                0.15
        );
    }

    private static void validateWeight(
            double weight,
            String fieldName
    ) {
        if (!Double.isFinite(weight) || weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0 and 1"
            );
        }
    }
}
