package totah.lab.prometheus.variational.force;

import java.util.Objects;

/** Immutable raw, weighted streaming statistics for a three-dimensional force. */
public record AssarafCaffarelForceStatistics(
        Vector meanHartreePerBohr,
        Vector varianceHartree2PerBohr2,
        Vector standardErrorHartreePerBohr,
        double effectiveSampleSize,
        long acceptedSamples,
        long rejectedZeroAmplitudeSamples,
        long stateEvaluations,
        int peakBatchSize,
        String forceUnits) {
    public AssarafCaffarelForceStatistics {
        Objects.requireNonNull(meanHartreePerBohr, "meanHartreePerBohr");
        Objects.requireNonNull(varianceHartree2PerBohr2, "varianceHartree2PerBohr2");
        Objects.requireNonNull(standardErrorHartreePerBohr, "standardErrorHartreePerBohr");
        Objects.requireNonNull(forceUnits, "forceUnits");
    }

    /** Cartesian vector with components ordered x, y, z. */
    public record Vector(double x, double y, double z) {
        public Vector {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("vector components must be finite");
            }
        }
    }
}
