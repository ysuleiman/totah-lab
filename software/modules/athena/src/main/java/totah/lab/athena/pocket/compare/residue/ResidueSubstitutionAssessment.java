package totah.lab.athena.pocket.compare.residue;

import java.util.List;
import java.util.Objects;

/**
 * Graded substitution assessment of a residue correspondence between
 * two pockets, computed by {@link ResidueSubstitutionScorer} from
 * BLOSUM62. Distinct from the chemistry assessment: it is a
 * continuous, per-pair substitution similarity and never feeds the
 * chemistry gate or the final similarity blend.
 *
 * <p>Similarities and fractions are within {@code [0, 1]} and are
 * {@code 0.0} when their denominator is empty.</p>
 *
 * @param matchSimilarities normalized substitution similarity per
 *                          matched pair, in the order of the
 *                          correspondence's match list
 * @param meanSubstitutionSimilarity mean of {@code matchSimilarities};
 *                          {@code 0.0} when there are no matches
 * @param identicalFraction   fraction of matched pairs that are
 *                            identical; {@code 0.0} when there are no
 *                            matches
 * @param matchedResidueCount total number of matched residue pairs
 * @param matchedFractionQuery matched fraction of the query pocket
 *                            residues, passed through from the
 *                            correspondence
 * @param matchedFractionCandidate matched fraction of the candidate
 *                            pocket residues, passed through from the
 *                            correspondence
 */
public record ResidueSubstitutionAssessment(
        List<Double> matchSimilarities,
        double meanSubstitutionSimilarity,
        double identicalFraction,
        int matchedResidueCount,
        double matchedFractionQuery,
        double matchedFractionCandidate
) {

    public ResidueSubstitutionAssessment {
        Objects.requireNonNull(matchSimilarities, "matchSimilarities");

        matchSimilarities = List.copyOf(matchSimilarities);

        for (double similarity : matchSimilarities) {
            requireFraction(similarity, "matchSimilarities element");
        }
        requireFraction(
                meanSubstitutionSimilarity,
                "meanSubstitutionSimilarity"
        );
        requireFraction(identicalFraction, "identicalFraction");
        requireFraction(matchedFractionQuery, "matchedFractionQuery");
        requireFraction(
                matchedFractionCandidate,
                "matchedFractionCandidate"
        );

        if (matchedResidueCount < 0) {
            throw new IllegalArgumentException(
                    "matchedResidueCount must be non-negative"
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
