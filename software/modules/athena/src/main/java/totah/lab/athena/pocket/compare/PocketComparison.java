package totah.lab.athena.pocket.compare;


import totah.lab.athena.pocket.geometry.PocketGeometryBasis;

import totah.lab.athena.pocket.geometry.PocketGeometryBasis;

/**
 * Result of comparing two aligned pocket point clouds.
 */
public record PocketComparison(
        double overallSimilarity,
        double geometrySimilarity,
        double sizeSimilarity,
        double queryCoverage,
        double candidateCoverage,
        double queryToCandidateMeanDistance,
        double candidateToQueryMeanDistance,
        double meanBidirectionalDistance,
        double maximumNearestNeighborDistance,
        int queryPointCount,
        int candidatePointCount,
        PocketGeometryBasis basis
) {

    public PocketComparison {
        requireUnitInterval(
                overallSimilarity,
                "overallSimilarity"
        );
        requireUnitInterval(
                geometrySimilarity,
                "geometrySimilarity"
        );
        requireUnitInterval(
                sizeSimilarity,
                "sizeSimilarity"
        );
        requireUnitInterval(
                queryCoverage,
                "queryCoverage"
        );
        requireUnitInterval(
                candidateCoverage,
                "candidateCoverage"
        );

        requireNonNegative(
                queryToCandidateMeanDistance,
                "queryToCandidateMeanDistance"
        );
        requireNonNegative(
                candidateToQueryMeanDistance,
                "candidateToQueryMeanDistance"
        );
        requireNonNegative(
                meanBidirectionalDistance,
                "meanBidirectionalDistance"
        );
        requireNonNegative(
                maximumNearestNeighborDistance,
                "maximumNearestNeighborDistance"
        );

        if (queryPointCount <= 0 || candidatePointCount <= 0) {
            throw new IllegalArgumentException(
                    "Pocket point counts must be greater than zero"
            );
        }

        if (basis == null) {
            throw new NullPointerException("basis");
        }
    }

    private static void requireUnitInterval(
            double value,
            String name
    ) {
        if (!Double.isFinite(value)
                || value < 0.0
                || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0 and 1: " + value
            );
        }
    }

    private static void requireNonNegative(
            double value,
            String name
    ) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative: " + value
            );
        }
    }
}
