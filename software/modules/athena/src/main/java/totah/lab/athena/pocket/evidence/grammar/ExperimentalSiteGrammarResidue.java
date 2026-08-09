package totah.lab.athena.pocket.evidence.grammar;

import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;

import java.util.Objects;

/** Orthogonal evidence dimensions for one accepted aligned residue pair. */
public record ExperimentalSiteGrammarResidue(
        int queryPosition,
        int candidatePosition,
        String queryResidue,
        String candidateResidue,
        boolean identical,
        double substitutionSimilarity,
        ResidueChemistry queryChemistry,
        ResidueChemistry candidateChemistry,
        MatchType chemistryRelationship,
        ExperimentalContactRole queryContactRole,
        ExperimentalContactRole candidateContactRole,
        int queryDirectObservationCount,
        int queryShellObservationCount,
        int candidateDirectObservationCount,
        int candidateShellObservationCount,
        StructuralVariabilityEvidence queryStructuralVariability,
        StructuralVariabilityEvidence candidateStructuralVariability) {

    public ExperimentalSiteGrammarResidue {
        if (queryPosition < 1 || candidatePosition < 1) {
            throw new IllegalArgumentException("UniProt positions must be positive");
        }
        Objects.requireNonNull(queryResidue);
        Objects.requireNonNull(candidateResidue);
        Objects.requireNonNull(queryChemistry);
        Objects.requireNonNull(candidateChemistry);
        Objects.requireNonNull(chemistryRelationship);
        Objects.requireNonNull(queryContactRole);
        Objects.requireNonNull(candidateContactRole);
        Objects.requireNonNull(queryStructuralVariability);
        Objects.requireNonNull(candidateStructuralVariability);
        if (!Double.isFinite(substitutionSimilarity)
                || substitutionSimilarity < 0 || substitutionSimilarity > 1) {
            throw new IllegalArgumentException(
                    "substitutionSimilarity must be within [0,1]");
        }
    }

    public boolean hasExperimentalSiteEvidence() {
        return queryContactRole != ExperimentalContactRole.NONE
                || candidateContactRole != ExperimentalContactRole.NONE;
    }
}
