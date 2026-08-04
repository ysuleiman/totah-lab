package totah.lab.athena.pocket.compare.residue;

import java.util.Objects;

/**
 * A single matched pair of pocket residue points together with the
 * distance between their representative positions and the resulting
 * classification.
 *
 * @param query                the residue point on the query pocket
 * @param candidate            the residue point on the (aligned)
 *                             candidate pocket
 * @param distanceAngstroms    distance between the two representative
 *                             positions in angstroms
 * @param matchType            classification of the pair
 * @param identicalResidue     whether both residues have the same name
 * @param chemistryCompatible  whether the two residues are considered
 *                             chemically compatible
 */
public record ResidueMatch(
        PocketResiduePoint query,
        PocketResiduePoint candidate,
        double distanceAngstroms,
        MatchType matchType,
        boolean identicalResidue,
        boolean chemistryCompatible
) {

    public ResidueMatch {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(matchType, "matchType");

        if (!Double.isFinite(distanceAngstroms)
                || distanceAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "distanceAngstroms must be finite and non-negative"
            );
        }
    }
}
