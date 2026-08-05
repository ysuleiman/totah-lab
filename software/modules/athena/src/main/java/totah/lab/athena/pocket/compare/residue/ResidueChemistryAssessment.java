package totah.lab.athena.pocket.compare.residue;

/**
 * Chemistry assessment of a residue correspondence between two
 * pockets, computed by {@link ResidueChemistryScorer}.
 *
 * <p>Fractions and similarities are within {@code [0, 1]} and are
 * {@code 0.0} when their denominator is empty, except
 * {@code spatialReplacementFraction}, which is {@code 1.0} when there
 * are no matches (no matched support at all). Counts are
 * non-negative.</p>
 *
 * @param chemistrySimilarity weighted chemistry score over the matched
 *                            residue pairs (identical 1.00, conservative
 *                            0.70, chemistry-compatible 0.80)
 * @param chemistryCoverageAdjustedSimilarity {@code chemistrySimilarity}
 *                            scaled by the geometric mean of the query
 *                            and candidate residue coverage
 * @param compatibleMatchedFraction fraction of matched pairs that are
 *                            chemically acceptable (identical,
 *                            conservative, or chemistry-compatible)
 * @param spatialReplacementFraction fraction of matched pairs that are
 *                            spatial replacements ({@link MatchType#DIFFERENT})
 * @param identicalCount        matched pairs classified
 *                            {@link MatchType#IDENTICAL}
 * @param conservativeCount     matched pairs classified
 *                            {@link MatchType#CONSERVATIVE}
 * @param chemistryCompatibleCount matched pairs classified
 *                            {@link MatchType#CHEMISTRY_COMPATIBLE}
 * @param spatialReplacementCount matched pairs classified
 *                            {@link MatchType#DIFFERENT}
 * @param matchedResidueCount   total number of matched residue pairs
 * @param queryResidueCount     total number of query pocket residues
 * @param candidateResidueCount total number of candidate pocket
 *                            residues
 * @param keyResidueChemistrySimilarity the weighted chemistry score
 *                            restricted to matches whose query residue
 *                            is a configured key residue; {@code 0.0}
 *                            when no key residue matches
 * @param keyMatchedCount       number of matched pairs whose query
 *                            residue is a configured key residue
 */
public record ResidueChemistryAssessment(
        double chemistrySimilarity,
        double chemistryCoverageAdjustedSimilarity,
        double compatibleMatchedFraction,
        double spatialReplacementFraction,
        int identicalCount,
        int conservativeCount,
        int chemistryCompatibleCount,
        int spatialReplacementCount,
        int matchedResidueCount,
        int queryResidueCount,
        int candidateResidueCount,
        double keyResidueChemistrySimilarity,
        int keyMatchedCount
) {

    public ResidueChemistryAssessment {
        requireFraction(chemistrySimilarity, "chemistrySimilarity");
        requireFraction(
                chemistryCoverageAdjustedSimilarity,
                "chemistryCoverageAdjustedSimilarity"
        );
        requireFraction(
                compatibleMatchedFraction,
                "compatibleMatchedFraction"
        );
        requireFraction(
                spatialReplacementFraction,
                "spatialReplacementFraction"
        );
        requireFraction(
                keyResidueChemistrySimilarity,
                "keyResidueChemistrySimilarity"
        );
        requireCount(identicalCount, "identicalCount");
        requireCount(conservativeCount, "conservativeCount");
        requireCount(chemistryCompatibleCount, "chemistryCompatibleCount");
        requireCount(spatialReplacementCount, "spatialReplacementCount");
        requireCount(matchedResidueCount, "matchedResidueCount");
        requireCount(queryResidueCount, "queryResidueCount");
        requireCount(candidateResidueCount, "candidateResidueCount");
        requireCount(keyMatchedCount, "keyMatchedCount");
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be within [0, 1]"
            );
        }
    }

    private static void requireCount(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    name + " must be non-negative"
            );
        }
    }
}
