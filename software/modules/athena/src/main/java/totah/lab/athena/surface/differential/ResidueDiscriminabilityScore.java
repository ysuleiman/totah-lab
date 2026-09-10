package totah.lab.athena.surface.differential;

/** SurfDiff RDS composition over already aggregated similar/different scores. */
public final class ResidueDiscriminabilityScore {
    private ResidueDiscriminabilityScore() {
    }

    public static double calculate(double minimumSimilarRss, double minimumDifferentRus) {
        if (!inUnitInterval(minimumSimilarRss) || !inUnitInterval(minimumDifferentRus)) {
            throw new IllegalArgumentException("RDS inputs must be within [0, 1]");
        }
        return SurfDiffWeights.clip(
                minimumSimilarRss - (1.0 - minimumDifferentRus));
    }

    private static boolean inUnitInterval(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}
