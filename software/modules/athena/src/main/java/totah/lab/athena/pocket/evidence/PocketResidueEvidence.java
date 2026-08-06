package totah.lab.athena.pocket.evidence;

import java.util.List;
import java.util.Objects;

/**
 * Residue-level evidence of a pocket comparison under the SELECTED
 * alignment. Exact identity, substitution similarity, chemistry-class
 * similarity, spatial coverage and sequence consistency are reported
 * as DISTINCT aggregates over the matched pairs; no combined score is
 * computed. Fractions are {@code 0.0} when their denominator is
 * empty.
 *
 * @param queryResidueCount       total query pocket residues
 * @param candidateResidueCount   total candidate pocket residues
 * @param matchedResidueCount     spatially matched residue pairs
 * @param unmatchedQueryResidueCount query residues without a match
 * @param unmatchedCandidateResidueCount candidate residues without a
 *                              match
 * @param identicalCount          matched pairs with identical residue
 *                                names
 * @param conservativeSubstitutionCount matched pairs classified
 *                                conservative
 * @param chemistryCompatibleCount matched pairs only sharing the
 *                                broad chemistry class
 * @param incompatibleReplacementCount matched pairs classified as
 *                                spatial replacements
 *                                ({@code MatchType.DIFFERENT})
 * @param identityFraction        identical pairs over matched pairs
 * @param substitutionSimilarity  mean normalized BLOSUM62 similarity
 *                                over matched pairs
 * @param chemistrySimilarity     mean chemistry weight over matched
 *                                pairs (identical 1.00, conservative
 *                                0.70, chemistry-compatible 0.80,
 *                                different 0.00)
 * @param compatibleMatchedFraction chemically acceptable pairs
 *                                (identical, conservative or
 *                                chemistry-compatible) over matched
 *                                pairs
 * @param replacementFraction     spatial replacements over matched
 *                                pairs
 * @param queryResidueCoverage    matched pairs over query residues
 * @param candidateResidueCoverage matched pairs over candidate
 *                                residues
 * @param sequenceConsistentPairCount matched pairs agreeing with the
 *                                protein sequence alignment
 * @param sequenceConsistentFraction the same count over matched
 *                                pairs
 * @param correspondences         per-pair evidence, one entry per
 *                                matched pair
 */
public record PocketResidueEvidence(
        int queryResidueCount,
        int candidateResidueCount,
        int matchedResidueCount,
        int unmatchedQueryResidueCount,
        int unmatchedCandidateResidueCount,
        int identicalCount,
        int conservativeSubstitutionCount,
        int chemistryCompatibleCount,
        int incompatibleReplacementCount,
        double identityFraction,
        double substitutionSimilarity,
        double chemistrySimilarity,
        double compatibleMatchedFraction,
        double replacementFraction,
        double queryResidueCoverage,
        double candidateResidueCoverage,
        int sequenceConsistentPairCount,
        double sequenceConsistentFraction,
        List<ResidueCorrespondenceEvidence> correspondences
) {

    public PocketResidueEvidence {
        requireCount(queryResidueCount, "queryResidueCount");
        requireCount(candidateResidueCount, "candidateResidueCount");
        requireCount(matchedResidueCount, "matchedResidueCount");
        requireCount(
                unmatchedQueryResidueCount,
                "unmatchedQueryResidueCount"
        );
        requireCount(
                unmatchedCandidateResidueCount,
                "unmatchedCandidateResidueCount"
        );
        requireCount(identicalCount, "identicalCount");
        requireCount(
                conservativeSubstitutionCount,
                "conservativeSubstitutionCount"
        );
        requireCount(
                chemistryCompatibleCount,
                "chemistryCompatibleCount"
        );
        requireCount(
                incompatibleReplacementCount,
                "incompatibleReplacementCount"
        );
        requireCount(
                sequenceConsistentPairCount,
                "sequenceConsistentPairCount"
        );

        requireFraction(identityFraction, "identityFraction");
        requireFraction(substitutionSimilarity, "substitutionSimilarity");
        requireFraction(chemistrySimilarity, "chemistrySimilarity");
        requireFraction(
                compatibleMatchedFraction,
                "compatibleMatchedFraction"
        );
        requireFraction(replacementFraction, "replacementFraction");
        requireFraction(queryResidueCoverage, "queryResidueCoverage");
        requireFraction(
                candidateResidueCoverage,
                "candidateResidueCoverage"
        );
        requireFraction(
                sequenceConsistentFraction,
                "sequenceConsistentFraction"
        );

        correspondences = List.copyOf(
                Objects.requireNonNull(correspondences, "correspondences")
        );
    }

    private static void requireCount(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    name + " must be non-negative"
            );
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be within [0, 1]"
            );
        }
    }
}
