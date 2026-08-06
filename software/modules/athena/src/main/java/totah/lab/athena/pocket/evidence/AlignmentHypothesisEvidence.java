package totah.lab.athena.pocket.evidence;

/**
 * Preserved metrics of one alignment hypothesis evaluated by the
 * multi-hypothesis aligner. Both the selected and the losing
 * hypothesis are retained; a hypothesis that was never computed is
 * represented by {@link #unavailable()} with {@code available =
 * false} and zeroed metrics (which must not be read as measured
 * values).
 *
 * @param available          whether the hypothesis was computed
 * @param accepted           whether this hypothesis was selected
 * @param geometrySimilarity geometric similarity of the aligned
 *                           clouds within {@code [0, 1]}
 * @param forwardCoverage    fraction of query points with a candidate
 *                           neighbour within the coverage radius
 * @param reverseCoverage    fraction of candidate points with a query
 *                           neighbour within the coverage radius
 * @param forwardMeanDistance mean query-to-candidate nearest-
 *                           neighbour distance in angstroms
 * @param reverseMeanDistance mean candidate-to-query nearest-
 *                           neighbour distance in angstroms
 * @param bidirectionalDistance mean of the two directional mean
 *                           distances in angstroms
 * @param maximumNearestNeighborDistance worst nearest-neighbour
 *                           distance in angstroms
 * @param sequenceConsistentPairCount matched residue pairs of this
 *                           hypothesis that agree with the protein
 *                           sequence alignment
 * @param residueCorrespondenceCount total matched residue pairs of
 *                           this hypothesis
 */
public record AlignmentHypothesisEvidence(
        boolean available,
        boolean accepted,
        double geometrySimilarity,
        double forwardCoverage,
        double reverseCoverage,
        double forwardMeanDistance,
        double reverseMeanDistance,
        double bidirectionalDistance,
        double maximumNearestNeighborDistance,
        int sequenceConsistentPairCount,
        int residueCorrespondenceCount
) {

    public AlignmentHypothesisEvidence {
        if (!available && accepted) {
            throw new IllegalArgumentException(
                    "An unavailable hypothesis cannot be accepted"
            );
        }

        requireFraction(geometrySimilarity, "geometrySimilarity");
        requireFraction(forwardCoverage, "forwardCoverage");
        requireFraction(reverseCoverage, "reverseCoverage");
        requireDistance(forwardMeanDistance, "forwardMeanDistance");
        requireDistance(reverseMeanDistance, "reverseMeanDistance");
        requireDistance(bidirectionalDistance, "bidirectionalDistance");
        requireDistance(
                maximumNearestNeighborDistance,
                "maximumNearestNeighborDistance"
        );

        if (sequenceConsistentPairCount < 0) {
            throw new IllegalArgumentException(
                    "sequenceConsistentPairCount must be non-negative"
            );
        }

        if (residueCorrespondenceCount < 0) {
            throw new IllegalArgumentException(
                    "residueCorrespondenceCount must be non-negative"
            );
        }

        if (sequenceConsistentPairCount > residueCorrespondenceCount) {
            throw new IllegalArgumentException(
                    "sequenceConsistentPairCount cannot exceed"
                            + " residueCorrespondenceCount"
            );
        }
    }

    /**
     * The placeholder for a hypothesis that was never computed (for
     * example the sequence-seeded hypothesis when no usable sequence
     * seed existed). All metrics are {@code 0.0} and carry no
     * meaning.
     */
    public static AlignmentHypothesisEvidence unavailable() {
        return new AlignmentHypothesisEvidence(
                false,
                false,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0,
                0
        );
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be within [0, 1]"
            );
        }
    }

    private static void requireDistance(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative"
            );
        }
    }
}
