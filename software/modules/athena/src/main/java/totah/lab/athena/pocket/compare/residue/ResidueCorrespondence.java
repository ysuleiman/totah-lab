package totah.lab.athena.pocket.compare.residue;

import java.util.List;
import java.util.Objects;

/**
 * Result of establishing a one-to-one residue correspondence between
 * two pocket residue point sets.
 *
 * <p>Fractions are relative to the size of the respective point set
 * (or to the number of matches for {@code identicalFraction} and
 * {@code chemistryCompatibleFraction}) and are {@code 0.0} when the
 * denominator is empty. Distances are {@code 0.0} when there are no
 * matches.</p>
 */
public record ResidueCorrespondence(
        List<ResidueMatch> matches,
        List<PocketResiduePoint> unmatchedQuery,
        List<PocketResiduePoint> unmatchedCandidate,
        double matchedFractionQuery,
        double matchedFractionCandidate,
        double identicalFraction,
        double chemistryCompatibleFraction,
        double meanMatchedDistance,
        double maximumMatchedDistance
) {

    public ResidueCorrespondence {
        Objects.requireNonNull(matches, "matches");
        Objects.requireNonNull(unmatchedQuery, "unmatchedQuery");
        Objects.requireNonNull(unmatchedCandidate, "unmatchedCandidate");

        matches = List.copyOf(matches);
        unmatchedQuery = List.copyOf(unmatchedQuery);
        unmatchedCandidate = List.copyOf(unmatchedCandidate);

        requireFraction(matchedFractionQuery, "matchedFractionQuery");
        requireFraction(
                matchedFractionCandidate,
                "matchedFractionCandidate"
        );
        requireFraction(identicalFraction, "identicalFraction");
        requireFraction(
                chemistryCompatibleFraction,
                "chemistryCompatibleFraction"
        );
        requireDistance(meanMatchedDistance, "meanMatchedDistance");
        requireDistance(maximumMatchedDistance, "maximumMatchedDistance");
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
