package totah.lab.athena.surface.differential;

/** Exact scalar weighting functions used by SurfDiff 1.0.0. */
public final class SurfDiffWeights {
    private SurfDiffWeights() {
    }

    public static double distance(double value, double maximum) {
        if (!Double.isFinite(value) || !Double.isFinite(maximum)
                || maximum <= 1.0) {
            throw new IllegalArgumentException("invalid distance weighting input");
        }
        return clip(1.0 - (value - 1.0) / (maximum - 1.0));
    }

    public static double exposure(double relativeSasa) {
        if (!Double.isFinite(relativeSasa) || relativeSasa < 0.0) {
            throw new IllegalArgumentException("relativeSasa must be non-negative");
        }
        if (relativeSasa < 0.05) {
            return 0.0;
        }
        return 1.0 / (1.0
                + Math.pow(0.5 / (relativeSasa + 0.5), 5.0));
    }

    public static double sasaDifference(double difference) {
        if (!Double.isFinite(difference)) {
            throw new IllegalArgumentException("difference must be finite");
        }
        double sigmoid = 1.0 / (1.0 + Math.exp(0.03 * (difference - 50.0)));
        return clip(1.0 - (1.0 - sigmoid) * 0.5);
    }

    static double clip(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
