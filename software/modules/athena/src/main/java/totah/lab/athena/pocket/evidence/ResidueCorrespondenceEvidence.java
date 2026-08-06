package totah.lab.athena.pocket.evidence;

import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.compare.residue.ResidueReference;

import java.util.Objects;

/**
 * Per-pair residue evidence of one spatial correspondence. Exact
 * identity, BLOSUM-based substitution similarity, chemistry-class
 * agreement, spatial distance and sequence consistency are kept as
 * DISTINCT fields and are never merged into a combined score.
 *
 * <p>No amino-acid enum exists in athena or gaia, so the amino acids
 * are carried as the residue-name strings of the
 * {@link ResidueReference}s (three-letter codes, for example
 * {@code "LEU"}).</p>
 *
 * @param queryResidue          the query pocket residue
 * @param candidateResidue      the candidate pocket residue
 * @param queryAminoAcid        residue name of the query residue
 * @param candidateAminoAcid    residue name of the candidate residue
 * @param distanceAngstroms     distance between the representative
 *                              positions in angstroms
 * @param sequenceAlignedPair   whether the residue numbers form an
 *                              aligned pair of the protein sequence
 *                              alignment
 * @param identical             whether both residue names are equal
 * @param conservativeSubstitution whether the pair is a conservative
 *                              substitution ({@link MatchType#CONSERVATIVE})
 * @param queryChemistry        chemistry class of the query residue
 * @param candidateChemistry    chemistry class of the candidate
 *                              residue
 * @param matchType             classification of the pair
 * @param chemistryScore        chemistry weight of the pair
 *                              (identical 1.00, conservative 0.70,
 *                              chemistry-compatible 0.80, different
 *                              0.00 — the weights of
 *                              {@code ResidueChemistryScorer})
 * @param substitutionScore     normalized BLOSUM62 substitution
 *                              similarity within {@code [0, 1]}
 * @param queryKeyResidue       whether the query residue is a
 *                              configured key residue
 * @param querySamContact       whether the query residue is annotated
 *                              as contacting the ligand under
 *                              evaluation
 * @param candidateSamContact   whether the candidate residue is
 *                              annotated as contacting the ligand
 *                              under evaluation
 */
public record ResidueCorrespondenceEvidence(
        ResidueReference queryResidue,
        ResidueReference candidateResidue,
        String queryAminoAcid,
        String candidateAminoAcid,
        double distanceAngstroms,
        boolean sequenceAlignedPair,
        boolean identical,
        boolean conservativeSubstitution,
        ResidueChemistry queryChemistry,
        ResidueChemistry candidateChemistry,
        MatchType matchType,
        double chemistryScore,
        double substitutionScore,
        boolean queryKeyResidue,
        boolean querySamContact,
        boolean candidateSamContact
) {

    public ResidueCorrespondenceEvidence {
        Objects.requireNonNull(queryResidue, "queryResidue");
        Objects.requireNonNull(candidateResidue, "candidateResidue");
        Objects.requireNonNull(queryAminoAcid, "queryAminoAcid");
        Objects.requireNonNull(candidateAminoAcid, "candidateAminoAcid");
        Objects.requireNonNull(queryChemistry, "queryChemistry");
        Objects.requireNonNull(candidateChemistry, "candidateChemistry");
        Objects.requireNonNull(matchType, "matchType");

        if (!Double.isFinite(distanceAngstroms)
                || distanceAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "distanceAngstroms must be finite and non-negative"
            );
        }

        requireFraction(chemistryScore, "chemistryScore");
        requireFraction(substitutionScore, "substitutionScore");
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be within [0, 1]"
            );
        }
    }
}
