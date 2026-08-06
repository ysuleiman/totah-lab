package totah.lab.athena.pocket.evidence;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Retrieval evidence from the pocket-match method. When the method
 * did not evaluate the candidate, {@code evaluated} is
 * {@code false} and every rank and score is empty: they are never
 * invented.
 *
 * @param evaluated          whether the pocket-match method evaluated
 *                           the candidate
 * @param symmetricRank      rank by the symmetric pocket-match score
 * @param queryCoverageRank  rank by query coverage
 * @param symmetricScore     symmetric pocket-match score
 * @param queryCoverage      fraction of the query pocket matched
 * @param candidateCoverage  fraction of the candidate pocket matched
 * @param toleranceAngstroms matching tolerance the pocket-match
 *                           method used; {@code 0.0} when the method
 *                           did not evaluate the candidate
 */
public record PocketMatchRetrievalEvidence(
        boolean evaluated,
        OptionalInt symmetricRank,
        OptionalInt queryCoverageRank,
        OptionalDouble symmetricScore,
        OptionalDouble queryCoverage,
        OptionalDouble candidateCoverage,
        double toleranceAngstroms
) {

    public PocketMatchRetrievalEvidence {
        Objects.requireNonNull(symmetricRank, "symmetricRank");
        Objects.requireNonNull(queryCoverageRank, "queryCoverageRank");
        Objects.requireNonNull(symmetricScore, "symmetricScore");
        Objects.requireNonNull(queryCoverage, "queryCoverage");
        Objects.requireNonNull(candidateCoverage, "candidateCoverage");

        if (!evaluated
                && (symmetricRank.isPresent()
                        || queryCoverageRank.isPresent()
                        || symmetricScore.isPresent()
                        || queryCoverage.isPresent()
                        || candidateCoverage.isPresent())) {
            throw new IllegalArgumentException(
                    "A candidate that was not evaluated must not carry"
                            + " ranks or scores"
            );
        }

        if (!Double.isFinite(toleranceAngstroms)
                || toleranceAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "toleranceAngstroms must be finite and non-negative"
            );
        }
    }

    /**
     * The evidence of a candidate the pocket-match method never
     * evaluated.
     */
    public static PocketMatchRetrievalEvidence notEvaluated() {
        return new PocketMatchRetrievalEvidence(
                false,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                0.0
        );
    }
}
