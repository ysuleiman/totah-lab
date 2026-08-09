package totah.lab.athena.pocket.compare.residue;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidueChemistryScorerTest {

    private final ResidueChemistryScorer scorer =
            new ResidueChemistryScorer();

    @Test
    void scoresTheRegressionFixtureExactly() {
        // matched=25 (1 identical, 2 conservative, 4 compatible,
        // 18 replacements), queryResidues=38, candidateResidues=40.
        List<ResidueMatch> matches = new ArrayList<>();
        addMatches(matches, MatchType.IDENTICAL, 1);
        addMatches(matches, MatchType.CONSERVATIVE, 2);
        addMatches(matches, MatchType.CHEMISTRY_COMPATIBLE, 4);
        addMatches(matches, MatchType.DIFFERENT, 18);

        ResidueChemistryAssessment assessment = scorer.assess(
                correspondence(matches, 38, 40),
                Set.of()
        );

        assertEquals(1, assessment.identicalCount());
        assertEquals(2, assessment.conservativeCount());
        assertEquals(4, assessment.chemistryCompatibleCount());
        assertEquals(18, assessment.spatialReplacementCount());
        assertEquals(25, assessment.matchedResidueCount());
        assertEquals(38, assessment.queryResidueCount());
        assertEquals(40, assessment.candidateResidueCount());
        assertEquals(0.224, assessment.chemistrySimilarity(), 1e-9);
        assertEquals(
                0.1436,
                assessment.chemistryCoverageAdjustedSimilarity(),
                1e-4
        );
        assertEquals(
                0.28,
                assessment.compatibleMatchedFraction(),
                1e-9
        );
        assertEquals(
                0.72,
                assessment.spatialReplacementFraction(),
                1e-9
        );
        assertEquals(0.0, assessment.keyResidueChemistrySimilarity());
        assertEquals(0, assessment.keyMatchedCount());

        assertFalse(scorer.passesChemistry(assessment));
        assertEquals(
                PocketSimilarityClassification.SHAPE_ONLY_NEIGHBOR,
                scorer.classify(assessment, 0.99)
        );
    }

    @Test
    void handlesEmptyMatches() {
        ResidueChemistryAssessment assessment = scorer.assess(
                correspondence(List.of(), 38, 40),
                Set.of("CYS202")
        );

        assertEquals(0, assessment.matchedResidueCount());
        assertEquals(0.0, assessment.chemistrySimilarity());
        assertEquals(
                0.0,
                assessment.chemistryCoverageAdjustedSimilarity()
        );
        assertEquals(0.0, assessment.compatibleMatchedFraction());
        assertEquals(1.0, assessment.spatialReplacementFraction());
        assertEquals(0.0, assessment.keyResidueChemistrySimilarity());
        assertFalse(scorer.passesChemistry(assessment));
    }

    @Test
    void handlesEmptyCorrespondenceEntirely() {
        ResidueChemistryAssessment assessment = scorer.assess(
                correspondence(List.of(), 0, 0),
                Set.of()
        );

        assertEquals(0, assessment.queryResidueCount());
        assertEquals(0, assessment.candidateResidueCount());
        assertEquals(
                0.0,
                assessment.chemistryCoverageAdjustedSimilarity()
        );
    }

    @Test
    void appliesGateThresholdsInclusively() {
        assertTrue(scorer.passesChemistry(assessment(0.40, 0.40, 0.50)));
        assertFalse(scorer.passesChemistry(assessment(0.39, 0.40, 0.50)));
        assertFalse(scorer.passesChemistry(assessment(0.40, 0.39, 0.50)));
        assertFalse(scorer.passesChemistry(assessment(0.40, 0.40, 0.51)));
    }

    @Test
    void classifiesOnFinalSimilarityThresholds() {
        ResidueChemistryAssessment passing = assessment(0.80, 0.80, 0.10);

        assertEquals(
                PocketSimilarityClassification.STRONG_SIMILARITY,
                scorer.classify(passing, 0.60)
        );
        assertEquals(
                PocketSimilarityClassification.MODERATE_SIMILARITY,
                scorer.classify(passing, 0.5999)
        );
        assertEquals(
                PocketSimilarityClassification.MODERATE_SIMILARITY,
                scorer.classify(passing, 0.40)
        );
        assertEquals(
                PocketSimilarityClassification.REJECTED,
                scorer.classify(passing, 0.3999)
        );
        assertEquals(
                PocketSimilarityClassification.SHAPE_ONLY_NEIGHBOR,
                scorer.classify(assessment(0.10, 0.10, 0.90), 0.95)
        );
    }

    @Test
    void scoresKeyResiduesOnTheQuerySubsetOnly() {
        List<ResidueMatch> matches = List.of(
                match("cys", 202, MatchType.IDENTICAL),
                match("LEU", 10, MatchType.CONSERVATIVE),
                match("ASP", 20, MatchType.CHEMISTRY_COMPATIBLE),
                match("GLY", 30, MatchType.DIFFERENT)
        );

        ResidueChemistryAssessment assessment = scorer.assess(
                correspondence(matches, 4, 4),
                Set.of("CYS202", "LEU10")
        );

        // Key similarity is the weighted score over the key subset
        // only: (1.00 + 0.70) / 2.
        assertEquals(2, assessment.keyMatchedCount());
        assertEquals(
                0.85,
                assessment.keyResidueChemistrySimilarity(),
                1e-9
        );
    }

    @Test
    void keySimilarityIsZeroWithoutMatchingKeyResidues() {
        List<ResidueMatch> matches = List.of(
                match("CYS", 202, MatchType.IDENTICAL)
        );

        ResidueChemistryAssessment unconfigured = scorer.assess(
                correspondence(matches, 1, 1),
                Set.of()
        );
        ResidueChemistryAssessment absent = scorer.assess(
                correspondence(matches, 1, 1),
                Set.of("CYS148")
        );

        assertEquals(0.0, unconfigured.keyResidueChemistrySimilarity());
        assertEquals(0, unconfigured.keyMatchedCount());
        assertEquals(0.0, absent.keyResidueChemistrySimilarity());
        assertEquals(0, absent.keyMatchedCount());
    }

    @Test
    void blendsFinalSimilarityFromItsComponents() {
        ResidueChemistryAssessment assessment =
                new ResidueChemistryAssessment(
                        0.9,
                        0.5,
                        0.9,
                        0.1,
                        8,
                        1,
                        0,
                        1,
                        10,
                        12,
                        12,
                        1.0,
                        2
                );

        // 0.45 * 0.8 + 0.40 * 0.5 + 0.15 * 1.0
        assertEquals(
                0.71,
                ResidueChemistryScorer.finalSimilarity(0.8, assessment),
                1e-9
        );
    }

    @Test
    void exposesCanonicalPerPairChemistryWeights() {
        assertEquals(1.0, ResidueChemistryScorer.chemistryWeight(
                MatchType.IDENTICAL));
        assertEquals(0.7, ResidueChemistryScorer.chemistryWeight(
                MatchType.CONSERVATIVE));
        assertEquals(0.8, ResidueChemistryScorer.chemistryWeight(
                MatchType.CHEMISTRY_COMPATIBLE));
        assertEquals(0.0, ResidueChemistryScorer.chemistryWeight(
                MatchType.DIFFERENT));
        assertEquals(0.0, ResidueChemistryScorer.chemistryWeight(
                MatchType.UNMATCHED));
    }

    @Test
    void rejectsOutOfRangeAssessmentValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueChemistryAssessment(
                        1.1, 0.0, 0.0, 0.0,
                        0, 0, 0, 0, 0, 0, 0, 0.0, 0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueChemistryAssessment(
                        Double.NaN, 0.0, 0.0, 0.0,
                        0, 0, 0, 0, 0, 0, 0, 0.0, 0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueChemistryAssessment(
                        0.0, 0.0, 0.0, 0.0,
                        -1, 0, 0, 0, 0, 0, 0, 0.0, 0
                )
        );
    }

    /**
     * Builds an assessment with the given gate inputs; all other
     * fields are filled with consistent placeholder values.
     */
    private static ResidueChemistryAssessment assessment(
            double chemistrySimilarity,
            double compatibleMatchedFraction,
            double spatialReplacementFraction
    ) {
        return new ResidueChemistryAssessment(
                chemistrySimilarity,
                chemistrySimilarity,
                compatibleMatchedFraction,
                spatialReplacementFraction,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0,
                0
        );
    }

    private static void addMatches(
            List<ResidueMatch> matches,
            MatchType matchType,
            int count
    ) {
        for (int index = 0; index < count; index++) {
            matches.add(match(
                    "ALA",
                    matches.size() + 1,
                    matchType
            ));
        }
    }

    private static ResidueMatch match(
            String queryResidueName,
            int queryResidueNumber,
            MatchType matchType
    ) {
        boolean identical = matchType == MatchType.IDENTICAL;
        boolean compatible = matchType != MatchType.DIFFERENT
                && matchType != MatchType.UNMATCHED;

        return new ResidueMatch(
                point("A", queryResidueNumber, queryResidueName),
                point("B", queryResidueNumber, "ALA"),
                1.0,
                matchType,
                identical,
                compatible
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
