package totah.lab.athena.pocket.evidence;

import java.util.List;
import java.util.Objects;

/**
 * Conservation evidence for the residues annotated as contacting one
 * functional ligand, evaluated under the SELECTED alignment. Contact
 * coverage, identity, substitution similarity and chemistry
 * similarity are DISTINCT aggregates over the matched query contact
 * residues. Fractions are {@code 0.0} when their denominator is
 * empty.
 *
 * @param ligandName          name of the ligand under evaluation
 * @param queryContactResidueCount query pocket residues annotated as
 *                            contacting the ligand
 * @param matchedQueryContactResidueCount annotated query contacts
 *                            with a spatial correspondence
 * @param identicalContactCount matched contacts with identical
 *                            residue names
 * @param conservativeContactCount matched contacts classified
 *                            conservative
 * @param chemistryCompatibleContactCount matched contacts sharing
 *                            only the broad chemistry class
 * @param incompatibleContactCount matched contacts classified as
 *                            spatial replacements
 * @param unmatchedContactCount annotated query contacts without a
 *                            spatial correspondence
 * @param sharedContactAnnotationCount matched contacts whose
 *                            candidate partner is also annotated
 * @param contactCoverage     matched query contacts over query
 *                            contacts
 * @param contactIdentityFraction identical contacts over matched
 *                            query contacts
 * @param contactSubstitutionSimilarity mean normalized BLOSUM62
 *                            similarity over matched query contacts
 * @param contactChemistrySimilarity mean chemistry weight over
 *                            matched query contacts
 * @param correspondences     one entry per annotated query contact
 *                            (matched or not) and per matched pair
 *                            annotated on the candidate side only
 */
public record LigandContactEvidence(
        String ligandName,
        int queryContactResidueCount,
        int matchedQueryContactResidueCount,
        int identicalContactCount,
        int conservativeContactCount,
        int chemistryCompatibleContactCount,
        int incompatibleContactCount,
        int unmatchedContactCount,
        int sharedContactAnnotationCount,
        double contactCoverage,
        double contactIdentityFraction,
        double contactSubstitutionSimilarity,
        double contactChemistrySimilarity,
        List<FunctionalResidueCorrespondence> correspondences
) {

    public LigandContactEvidence {
        Objects.requireNonNull(ligandName, "ligandName");

        requireCount(queryContactResidueCount, "queryContactResidueCount");
        requireCount(
                matchedQueryContactResidueCount,
                "matchedQueryContactResidueCount"
        );
        requireCount(identicalContactCount, "identicalContactCount");
        requireCount(
                conservativeContactCount,
                "conservativeContactCount"
        );
        requireCount(
                chemistryCompatibleContactCount,
                "chemistryCompatibleContactCount"
        );
        requireCount(
                incompatibleContactCount,
                "incompatibleContactCount"
        );
        requireCount(unmatchedContactCount, "unmatchedContactCount");
        requireCount(
                sharedContactAnnotationCount,
                "sharedContactAnnotationCount"
        );

        requireFraction(contactCoverage, "contactCoverage");
        requireFraction(
                contactIdentityFraction,
                "contactIdentityFraction"
        );
        requireFraction(
                contactSubstitutionSimilarity,
                "contactSubstitutionSimilarity"
        );
        requireFraction(
                contactChemistrySimilarity,
                "contactChemistrySimilarity"
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
