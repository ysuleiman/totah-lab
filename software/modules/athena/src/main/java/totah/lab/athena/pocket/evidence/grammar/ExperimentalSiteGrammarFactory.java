package totah.lab.athena.pocket.evidence.grammar;

import totah.lab.athena.pocket.compare.residue.ResidueChemistryClassifier;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondenceCalculator;
import totah.lab.athena.pocket.compare.residue.ResidueSubstitutionScorer;

import java.util.Objects;

/** Derives independent site-grammar dimensions without an aggregate score. */
public final class ExperimentalSiteGrammarFactory {
    private final ResidueSubstitutionScorer substitutionScorer =
            new ResidueSubstitutionScorer();
    private final ResidueChemistryClassifier chemistryClassifier =
            new ResidueChemistryClassifier();

    public ExperimentalSiteGrammarResidue derive(int queryPosition,
            int candidatePosition, String queryResidue,
            String candidateResidue, ExperimentalContactRole queryRole,
            ExperimentalContactRole candidateRole, int queryDirectCount,
            int queryShellCount, int candidateDirectCount,
            int candidateShellCount,
            StructuralVariabilityEvidence queryVariability,
            StructuralVariabilityEvidence candidateVariability) {
        Objects.requireNonNull(queryResidue);
        Objects.requireNonNull(candidateResidue);
        String queryThree = threeLetter(queryResidue);
        String candidateThree = threeLetter(candidateResidue);
        return new ExperimentalSiteGrammarResidue(queryPosition,
                candidatePosition, queryResidue, candidateResidue,
                queryResidue.equals(candidateResidue),
                substitutionScorer.similarity(queryThree, candidateThree),
                chemistryClassifier.classifyName(queryThree),
                chemistryClassifier.classifyName(candidateThree),
                ResidueCorrespondenceCalculator.matchTypeOf(queryThree,
                        candidateThree), queryRole, candidateRole,
                queryDirectCount, queryShellCount, candidateDirectCount,
                candidateShellCount, queryVariability,
                candidateVariability);
    }

    static String threeLetter(String residue) {
        return switch (residue.trim().toUpperCase()) {
            case "A" -> "ALA"; case "R" -> "ARG"; case "N" -> "ASN";
            case "D" -> "ASP"; case "C" -> "CYS"; case "Q" -> "GLN";
            case "E" -> "GLU"; case "G" -> "GLY"; case "H" -> "HIS";
            case "I" -> "ILE"; case "L" -> "LEU"; case "K" -> "LYS";
            case "M" -> "MET"; case "F" -> "PHE"; case "P" -> "PRO";
            case "S" -> "SER"; case "T" -> "THR"; case "W" -> "TRP";
            case "Y" -> "TYR"; case "V" -> "VAL";
            default -> "UNK";
        };
    }
}
