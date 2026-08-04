package totah.lab.athena.pocket.compare;

import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Objects;

/**
 * Compares pocket point clouds after rigid-body alignment.
 *
 * <p>The comparison uses bidirectional nearest-neighbor distances and
 * bidirectional point coverage. This avoids requiring equal point counts or
 * known point correspondence.</p>
 */
public final class PocketComparator {

    private final PocketAligner aligner;
    private final PocketComparisonOptions options;

    /**
     * Creates a comparator using centroid alignment and default comparison
     * options.
     *
     * <p>For rotation-independent comparison, supply a principal-axis,
     * ICP, or composite aligner explicitly.</p>
     */
    public PocketComparator() {
        this(
                new CompositePocketAligner(),
                PocketComparisonOptions.defaults()
        );
    }

    public PocketComparator(
            PocketAligner aligner,
            PocketComparisonOptions options
    ) {
        this.aligner = Objects.requireNonNull(
                aligner,
                "aligner"
        );

        this.options = Objects.requireNonNull(
                options,
                "options"
        );
    }

    /**
     * Aligns {@code candidate} onto {@code query}, then computes comparison
     * statistics from the aligned point clouds.
     */
    public PocketComparison compare(
            PocketPointCloud query,
            PocketPointCloud candidate
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidate, "candidate");

        requireSameBasis(query, candidate);

        PocketAlignment alignment =
                aligner.align(query, candidate);

        return compareAligned(alignment);
    }

    /**
     * Computes comparison statistics from a completed alignment.
     */
    public PocketComparison compareAligned(
            PocketAlignment alignment
    ) {
        Objects.requireNonNull(alignment, "alignment");

        PocketPointCloud query =
                alignment.query();

        PocketPointCloud alignedCandidate =
                alignment.alignedCandidate();

        requireSameBasis(
                query,
                alignedCandidate
        );

        DirectionalStatistics queryToCandidate =
                nearestNeighborStatistics(
                        query.points(),
                        alignedCandidate.points()
                );

        DirectionalStatistics candidateToQuery =
                nearestNeighborStatistics(
                        alignedCandidate.points(),
                        query.points()
                );

        double meanBidirectionalDistance =
                (
                        queryToCandidate.meanDistance()
                                + candidateToQuery.meanDistance()
                ) / 2.0;

        double maximumNearestNeighborDistance =
                Math.max(
                        queryToCandidate.maximumDistance(),
                        candidateToQuery.maximumDistance()
                );

        /*
         * Geometric mean penalizes asymmetric coverage. For example, high
         * query coverage cannot fully compensate for poor candidate coverage.
         */
        double symmetricCoverage = Math.sqrt(
                queryToCandidate.coverage()
                        * candidateToQuery.coverage()
        );

        /*
         * Converts an unbounded distance into the interval (0, 1].
         *
         * A distance of zero produces 1.0. Larger distances approach zero.
         */
        double distanceSimilarity =
                1.0 / (1.0 + meanBidirectionalDistance);

        double geometrySimilarity =
                clamp01(
                        distanceSimilarity
                                * symmetricCoverage
                );

        double sizeSimilarity =
                pointCountSimilarity(
                        query.size(),
                        alignedCandidate.size()
                );

        double overallSimilarity =
                clamp01(
                        options.geometryWeight()
                                * geometrySimilarity
                                + options.sizeWeight()
                                * sizeSimilarity
                );

        return new PocketComparison(
                overallSimilarity,
                geometrySimilarity,
                sizeSimilarity,

                queryToCandidate.coverage(),
                candidateToQuery.coverage(),

                queryToCandidate.meanDistance(),
                candidateToQuery.meanDistance(),

                meanBidirectionalDistance,
                maximumNearestNeighborDistance,

                query.size(),
                alignedCandidate.size(),

                query.basis()
        );
    }

    public PocketAlignment align(
            PocketPointCloud query,
            PocketPointCloud candidate
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidate, "candidate");

        requireSameBasis(query, candidate);

        return aligner.align(query, candidate);
    }

    private static void requireSameBasis(
            PocketPointCloud query,
            PocketPointCloud candidate
    ) {
        if (query.basis() != candidate.basis()) {
            throw new IllegalArgumentException(
                    "Cannot compare pockets represented by different bases: "
                            + query.basis()
                            + " vs "
                            + candidate.basis()
            );
        }
    }

    private DirectionalStatistics nearestNeighborStatistics(
            List<Point3D> source,
            List<Point3D> target
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");

        if (source.isEmpty() || target.isEmpty()) {
            throw new IllegalArgumentException(
                    "Point clouds must not be empty"
            );
        }

        double cutoff =
                options.matchCutoffAngstroms();

        double cutoffSquared =
                cutoff * cutoff;

        CompensatedSum totalDistance =
                new CompensatedSum();

        double maximumDistance = 0.0;
        int matched = 0;

        for (Point3D sourcePoint : source) {
            Objects.requireNonNull(
                    sourcePoint,
                    "source must not contain null points"
            );

            double minimumSquared =
                    nearestDistanceSquared(
                            sourcePoint,
                            target
                    );

            double minimumDistance =
                    Math.sqrt(minimumSquared);

            totalDistance.add(minimumDistance);

            maximumDistance = Math.max(
                    maximumDistance,
                    minimumDistance
            );

            if (minimumSquared <= cutoffSquared) {
                matched++;
            }
        }

        return new DirectionalStatistics(
                totalDistance.value() / source.size(),
                maximumDistance,
                matched / (double) source.size()
        );
    }

    private static double nearestDistanceSquared(
            Point3D source,
            List<Point3D> targets
    ) {
        double minimumSquared =
                Double.POSITIVE_INFINITY;

        for (Point3D target : targets) {
            Objects.requireNonNull(
                    target,
                    "target must not contain null points"
            );

            minimumSquared = Math.min(
                    minimumSquared,
                    source.distanceSquared(target)
            );
        }

        if (!Double.isFinite(minimumSquared)) {
            throw new IllegalStateException(
                    "Unable to calculate nearest-neighbor distance"
            );
        }

        return minimumSquared;
    }

    private static double pointCountSimilarity(
            int queryCount,
            int candidateCount
    ) {
        int maximum =
                Math.max(queryCount, candidateCount);

        if (maximum == 0) {
            return 0.0;
        }

        return Math.min(queryCount, candidateCount)
                / (double) maximum;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private record DirectionalStatistics(
            double meanDistance,
            double maximumDistance,
            double coverage
    ) {

        private DirectionalStatistics {
            requireNonNegativeFinite(
                    meanDistance,
                    "meanDistance"
            );

            requireNonNegativeFinite(
                    maximumDistance,
                    "maximumDistance"
            );

            if (!Double.isFinite(coverage)
                    || coverage < 0.0
                    || coverage > 1.0) {
                throw new IllegalArgumentException(
                        "coverage must be finite and between 0 and 1"
                );
            }
        }
    }

    private static void requireNonNegativeFinite(
            double value,
            String name
    ) {
        if (!Double.isFinite(value)
                || value < 0.0) {
            throw new IllegalArgumentException(
                    name
                            + " must be finite and non-negative"
            );
        }
    }

    private static final class CompensatedSum {

        private double sum;
        private double compensation;

        void add(double value) {
            double adjusted =
                    value - compensation;

            double next =
                    sum + adjusted;

            compensation =
                    (next - sum) - adjusted;

            sum = next;
        }

        double value() {
            return sum;
        }
    }
}