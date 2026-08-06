package totah.lab.athena.pocket.compare.residue;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResidueSubstitutionScorerTest {

    private final ResidueSubstitutionScorer scorer =
            new ResidueSubstitutionScorer();

    @Test
    void scoresKnownBlosum62Pairs() {
        // Normalized as (score - (-4)) / (11 - (-4)) = (score + 4) / 15.
        assertEquals(0.4, scorer.similarity("LEU", "ILE"), 1e-9);
        assertEquals(0.4, scorer.similarity("ASP", "GLU"), 1e-9);
        assertEquals(1.0, scorer.similarity("TRP", "TRP"), 1e-9);
        assertEquals(2.0 / 15.0, scorer.similarity("ALA", "ASP"), 1e-9);
    }

    @Test
    void isSymmetric() {
        assertEquals(
                scorer.similarity("LEU", "ILE"),
                scorer.similarity("ILE", "LEU"),
                1e-12
        );
        assertEquals(
                scorer.similarity("ASP", "TRP"),
                scorer.similarity("TRP", "ASP"),
                1e-12
        );
    }

    @Test
    void doesNotForceIdentityToOne() {
        // BLOSUM62 diagonal values differ by residue on purpose:
        // A-A is 4 while W-W is 11.
        assertEquals(8.0 / 15.0, scorer.similarity("ALA", "ALA"), 1e-9);
        assertEquals(1.0, scorer.similarity("TRP", "TRP"), 1e-9);
    }

    @Test
    void normalizesNamesCaseInsensitively() {
        assertEquals(
                scorer.similarity("LEU", "ILE"),
                scorer.similarity(" leu ", "ile"),
                1e-12
        );
    }

    @Test
    void returnsZeroForUnknownResidueNames() {
        assertEquals(0.0, scorer.similarity("MSE", "MET"));
        assertEquals(0.0, scorer.similarity("MET", "MSE"));
        assertEquals(0.0, scorer.similarity("X", "Y"));
    }

    @Test
    void rejectsNullResidueNames() {
        assertThrows(
                NullPointerException.class,
                () -> scorer.similarity(null, "ALA")
        );
        assertThrows(
                NullPointerException.class,
                () -> scorer.similarity("ALA", null)
        );
    }

    @Test
    void assessesAHandComputedCorrespondence() {
        List<ResidueMatch> matches = List.of(
                match("LEU", "ILE"),
                match("ASP", "GLU"),
                match("ALA", "ASP"),
                match("TRP", "TRP")
        );

        ResidueSubstitutionAssessment assessment = scorer.assess(
                correspondence(matches, 6, 5)
        );

        assertEquals(4, assessment.matchSimilarities().size());
        assertEquals(0.4, assessment.matchSimilarities().get(0), 1e-9);
        assertEquals(0.4, assessment.matchSimilarities().get(1), 1e-9);
        assertEquals(
                2.0 / 15.0,
                assessment.matchSimilarities().get(2),
                1e-9
        );
        assertEquals(1.0, assessment.matchSimilarities().get(3), 1e-9);

        double expectedMean =
                (0.4 + 0.4 + 2.0 / 15.0 + 1.0) / 4.0;
        assertEquals(
                expectedMean,
                assessment.meanSubstitutionSimilarity(),
                1e-9
        );
        assertEquals(0.25, assessment.identicalFraction(), 1e-9);
        assertEquals(4, assessment.matchedResidueCount());
        assertEquals(4.0 / 6.0, assessment.matchedFractionQuery(), 1e-9);
        assertEquals(4.0 / 5.0, assessment.matchedFractionCandidate(), 1e-9);
    }

    @Test
    void handlesEmptyCorrespondence() {
        ResidueSubstitutionAssessment assessment = scorer.assess(
                correspondence(List.of(), 3, 4)
        );

        assertEquals(0, assessment.matchedResidueCount());
        assertEquals(List.of(), assessment.matchSimilarities());
        assertEquals(0.0, assessment.meanSubstitutionSimilarity());
        assertEquals(0.0, assessment.identicalFraction());
        assertEquals(0.0, assessment.matchedFractionQuery());
        assertEquals(0.0, assessment.matchedFractionCandidate());
    }

    @Test
    void rejectsOutOfRangeAssessmentValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueSubstitutionAssessment(
                        List.of(1.1), 0.0, 0.0, 1, 0.0, 0.0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueSubstitutionAssessment(
                        List.of(0.5), Double.NaN, 0.0, 1, 0.0, 0.0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueSubstitutionAssessment(
                        List.of(0.5), 0.5, 0.0, -1, 0.0, 0.0
                )
        );
    }

    private static ResidueMatch match(
            String queryResidueName,
            String candidateResidueName
    ) {
        return new ResidueMatch(
                point("A", 1, queryResidueName),
                point("B", 1, candidateResidueName),
                1.0,
                queryResidueName.equals(candidateResidueName)
                        ? MatchType.IDENTICAL
                        : MatchType.DIFFERENT,
                queryResidueName.equals(candidateResidueName),
                false
        );
    }

    private static PocketResiduePoint point(
            String chainId,
            int residueNumber,
            String residueName
    ) {
        return new PocketResiduePoint(
                new ResidueReference(
                        chainId,
                        residueNumber,
                        ' ',
                        residueName
                ),
                new Point3D(residueNumber, 0.0, 0.0),
                ResidueChemistry.HYDROPHOBIC
        );
    }

    private static ResidueCorrespondence correspondence(
            List<ResidueMatch> matches,
            int queryResidueCount,
            int candidateResidueCount
    ) {
        List<PocketResiduePoint> unmatchedQuery = new ArrayList<>();
        for (int index = matches.size();
             index < queryResidueCount;
             index++) {
            unmatchedQuery.add(point("A", index + 1, "ALA"));
        }

        List<PocketResiduePoint> unmatchedCandidate = new ArrayList<>();
        for (int index = matches.size();
             index < candidateResidueCount;
             index++) {
            unmatchedCandidate.add(point("B", index + 1, "ALA"));
        }

        int matched = matches.size();

        return new ResidueCorrespondence(
                matches,
                unmatchedQuery,
                unmatchedCandidate,
                fraction(matched, queryResidueCount),
                fraction(matched, candidateResidueCount),
                0.0,
                0.0,
                0.0,
                0.0
        );
    }

    private static double fraction(int numerator, int denominator) {
        return denominator == 0
                ? 0.0
                : (double) numerator / (double) denominator;
    }
}
