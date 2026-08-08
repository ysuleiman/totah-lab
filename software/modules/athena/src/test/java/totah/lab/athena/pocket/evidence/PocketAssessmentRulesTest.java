package totah.lab.athena.pocket.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.AlignmentInitialization;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketAssessmentRulesTest {

    private final PocketAssessmentRules rules =
            PocketAssessmentRules.defaults();

    @Test
    void versionedAssessmentPreservesExistingVerdictAndReason() {
        PocketComparisonEvidence evidence = evidence(
                0.10, 20, 0.20, 0.10, 0.0, false, null);

        PocketAssessment<PocketComparisonAssessment> assessment =
                rules.assessVersioned(evidence, "comparison-rules/1");

        assertEquals(PocketComparisonAssessment.REJECTED, assessment.verdict());
        assertEquals("comparison-rules/1", assessment.rulesetVersion());
        assertEquals("POCKET_COMPARISON",
                assessment.reasons().getFirst().dimension());
        assertTrue(assessment.reasons().getFirst().explanation()
                .contains("geometry"));
    }

    @Test
    void insufficientEvidenceWhenTooFewCorrespondences() {
        PocketComparisonEvidence evidence = evidence(
                0.50,
                2,
                0.90,
                0.90,
                1.0,
                true,
                null
        );

        PocketAssessmentVerdict verdict = rules.assess(evidence);

        assertEquals(
                PocketComparisonAssessment.INSUFFICIENT_EVIDENCE,
                verdict.verdict()
        );
        assertTrue(
                verdict.reason().contains("matched residue pairs"),
                "reason was: " + verdict.reason()
        );
    }

    @Test
    void rejectedWhenGeometryAndResidueEvidenceAreBothPoor() {
        PocketComparisonEvidence evidence = evidence(
                0.10,
                20,
                0.20,
                0.10,
                0.0,
                false,
                null
        );

        PocketAssessmentVerdict verdict = rules.assess(evidence);

        assertEquals(
                PocketComparisonAssessment.REJECTED,
                verdict.verdict()
        );
        assertTrue(
                verdict.reason().contains("geometry"),
                "reason was: " + verdict.reason()
        );
        assertTrue(
                verdict.reason().contains("chemistry"),
                "reason was: " + verdict.reason()
        );
    }

    @Test
    void conflictingWhenGeometryIsStrongButChemistryIsPoor() {
        PocketComparisonEvidence evidence = evidence(
                0.80,
                20,
                0.20,
                0.10,
                0.0,
                false,
                null
        );

        PocketAssessmentVerdict verdict = rules.assess(evidence);

        assertEquals(
                PocketComparisonAssessment.CONFLICTING_EVIDENCE,
                verdict.verdict()
        );
        assertTrue(
                verdict.reason().contains("strong geometry"),
                "reason was: " + verdict.reason()
        );
        assertTrue(
                verdict.reason().contains("poor residue chemistry"),
                "reason was: " + verdict.reason()
        );
    }

    @Test
    void conflictingWhenGeometryIsPoorButChemistryIsHigh() {
        PocketComparisonEvidence evidence = evidence(
                0.10,
                20,
                0.80,
                0.80,
                1.0,
                true,
                null
        );

        PocketAssessmentVerdict verdict = rules.assess(evidence);

        assertEquals(
                PocketComparisonAssessment.CONFLICTING_EVIDENCE,
                verdict.verdict()
        );
        assertTrue(
                verdict.reason().contains("geometry"),
                "reason was: " + verdict.reason()
        );
        assertTrue(
                verdict.reason().contains("high residue chemistry"),
                "reason was: " + verdict.reason()
        );
    }

    @Test
    void strongWhenEveryPresentDimensionIsHigh() {
        PocketComparisonEvidence evidence = evidence(
                0.50,
                20,
                0.80,
                0.80,
                1.0,
                true,
                null
        );

        PocketAssessmentVerdict verdict = rules.assess(evidence);

        assertEquals(
                PocketComparisonAssessment.STRONG_FUNCTIONAL_MATCH,
                verdict.verdict()
        );
        assertTrue(
                verdict.reason().contains("geometry 0.500"),
                "reason was: " + verdict.reason()
        );
        assertTrue(
                verdict.reason().contains("chemistry 0.800"),
                "reason was: " + verdict.reason()
        );
        assertTrue(
                verdict.reason().contains("substitution similarity 0.800"),
                "reason was: " + verdict.reason()
        );
        assertTrue(
                verdict.reason().contains("sequence consistency 1.000"),
                "reason was: " + verdict.reason()
        );
    }

    @Test
    void strongRequiresContactConservationWhenLigandEvidenceExists() {
        LigandContactEvidence conserved = contacts(4, 4, 2, 1, 1, 0);
        LigandContactEvidence lost = contacts(4, 2, 2, 0, 0, 2);

        PocketAssessmentVerdict strong = rules.assess(evidence(
                0.50,
                20,
                0.80,
                0.80,
                1.0,
                true,
                conserved
        ));
        assertEquals(
                PocketComparisonAssessment.STRONG_FUNCTIONAL_MATCH,
                strong.verdict()
        );
        assertTrue(
                strong.reason().contains("contact conservation 1.000"),
                "reason was: " + strong.reason()
        );

        assertEquals(
                PocketComparisonAssessment.GEOMETRIC_MATCH_ONLY,
                rules.assess(evidence(
                        0.50,
                        20,
                        0.20,
                        0.10,
                        0.0,
                        false,
                        lost
                )).verdict()
        );

        // Chemistry high but contact conservation lost: probable.
        PocketAssessmentVerdict probable = rules.assess(evidence(
                0.50,
                20,
                0.80,
                0.80,
                1.0,
                true,
                lost
        ));
        assertEquals(
                PocketComparisonAssessment.PROBABLE_FUNCTIONAL_MATCH,
                probable.verdict()
        );
        assertTrue(
                probable.reason().contains("contact conservation 0.500"),
                "reason was: " + probable.reason()
        );
    }

    @Test
    void probableWhenOneDimensionIsOnlyModerate() {
        // High chemistry but sequence consistency only moderate.
        PocketComparisonEvidence evidence = evidence(
                0.50,
                20,
                0.80,
                0.80,
                0.60,
                true,
                null
        );

        PocketAssessmentVerdict verdict = rules.assess(evidence);

        assertEquals(
                PocketComparisonAssessment.PROBABLE_FUNCTIONAL_MATCH,
                verdict.verdict()
        );
        assertTrue(
                verdict.reason().contains("sequence consistency 0.600"),
                "reason was: " + verdict.reason()
        );
    }

    @Test
    void probableWhenChemistryIsModerate() {
        PocketComparisonEvidence evidence = evidence(
                0.50,
                20,
                0.50,
                0.50,
                1.0,
                true,
                null
        );

        PocketAssessmentVerdict verdict = rules.assess(evidence);

        assertEquals(
                PocketComparisonAssessment.PROBABLE_FUNCTIONAL_MATCH,
                verdict.verdict()
        );
        assertTrue(
                verdict.reason().contains("chemistry 0.500"),
                "reason was: " + verdict.reason()
        );
    }

    @Test
    void geometricMatchOnlyWhenResidueAgreementIsPoor() {
        PocketComparisonEvidence evidence = evidence(
                0.50,
                20,
                0.20,
                0.10,
                0.0,
                false,
                null
        );

        PocketAssessmentVerdict verdict = rules.assess(evidence);

        assertEquals(
                PocketComparisonAssessment.GEOMETRIC_MATCH_ONLY,
                verdict.verdict()
        );
        assertTrue(
                verdict.reason().contains("geometry 0.500"),
                "reason was: " + verdict.reason()
        );
        assertTrue(
                verdict.reason().contains("chemistry 0.200"),
                "reason was: " + verdict.reason()
        );
    }

    @Test
    void everyVerdictCarriesANonBlankReason() {
        double[][] scenarios = {
                // geometry, chemistry, substitution
                {0.10, 0.20, 0.10},   // rejected
                {0.80, 0.20, 0.10},   // conflicting (strong geometry)
                {0.10, 0.80, 0.80},   // conflicting (high chemistry)
                {0.50, 0.80, 0.80},   // strong
                {0.50, 0.50, 0.50},   // probable
                {0.50, 0.20, 0.10}    // geometric only
        };

        for (double[] scenario : scenarios) {
            PocketAssessmentVerdict verdict = rules.assess(evidence(
                    scenario[0],
                    20,
                    scenario[1],
                    scenario[2],
                    1.0,
                    true,
                    null
            ));

            assertFalse(
                    verdict.reason().isBlank(),
                    "blank reason for " + verdict.verdict()
            );
            assertTrue(
                    verdict.reason().startsWith(
                            verdict.verdict().name() + ":"
                    ),
                    "reason does not name the verdict: "
                            + verdict.reason()
            );
        }
    }

    private static PocketComparisonEvidence evidence(
            double geometrySimilarity,
            int matchedResidueCount,
            double chemistrySimilarity,
            double substitutionSimilarity,
            double sequenceConsistentFraction,
            boolean sequenceSeededAvailable,
            LigandContactEvidence contacts
    ) {
        int sequenceConsistentCount = (int) Math.round(
                sequenceConsistentFraction * matchedResidueCount
        );

        AlignmentHypothesisEvidence pca = new AlignmentHypothesisEvidence(
                true,
                !sequenceSeededAvailable,
                geometrySimilarity,
                0.5,
                0.5,
                1.0,
                1.0,
                1.0,
                2.0,
                0,
                matchedResidueCount
        );
        AlignmentHypothesisEvidence seeded = sequenceSeededAvailable
                ? new AlignmentHypothesisEvidence(
                        true,
                        true,
                        geometrySimilarity,
                        0.5,
                        0.5,
                        1.0,
                        1.0,
                        1.0,
                        2.0,
                        sequenceConsistentCount,
                        matchedResidueCount
                )
                : AlignmentHypothesisEvidence.unavailable();

        PocketAlignmentEvidence alignment = new PocketAlignmentEvidence(
                pca,
                seeded,
                sequenceSeededAvailable
                        ? AlignmentInitialization.SEQUENCE_SEEDED_KABSCH
                        : AlignmentInitialization.PCA_ICP,
                "synthetic"
        );

        PocketResidueEvidence residues = new PocketResidueEvidence(
                matchedResidueCount,
                matchedResidueCount,
                matchedResidueCount,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0,
                substitutionSimilarity,
                chemistrySimilarity,
                0.0,
                0.0,
                1.0,
                1.0,
                sequenceSeededAvailable ? sequenceConsistentCount : 0,
                sequenceSeededAvailable
                        ? sequenceConsistentFraction
                        : 0.0,
                List.of()
        );

        PocketFunctionalEvidence functional =
                new PocketFunctionalEvidence(
                        Optional.ofNullable(contacts),
                        new KeyResidueEvidence(0, 0, 0, 0)
                );

        // The placeholder verdict is never read by the rules: they
        // assess the evidence dimensions only.
        return new PocketComparisonEvidence(
                retrieval(),
                alignment,
                residues,
                functional,
                new PocketAssessmentVerdict(
                        PocketComparisonAssessment.INSUFFICIENT_EVIDENCE,
                        "placeholder"
                )
        );
    }

    private static LigandContactEvidence contacts(
            int queryContactResidueCount,
            int matchedQueryContactResidueCount,
            int identicalContactCount,
            int conservativeContactCount,
            int chemistryCompatibleContactCount,
            int unmatchedContactCount
    ) {
        return new LigandContactEvidence(
                "SAM",
                queryContactResidueCount,
                matchedQueryContactResidueCount,
                identicalContactCount,
                conservativeContactCount,
                chemistryCompatibleContactCount,
                0,
                unmatchedContactCount,
                0,
                queryContactResidueCount == 0
                        ? 0.0
                        : (double) matchedQueryContactResidueCount
                                / queryContactResidueCount,
                0.0,
                0.0,
                0.0,
                List.of()
        );
    }

    private static PocketRetrievalEvidence retrieval() {
        return new PocketRetrievalEvidence(
                new GlobalShapeRetrievalEvidence(
                        true,
                        OptionalInt.of(6840),
                        OptionalDouble.of(0.31)
                ),
                PocketMatchRetrievalEvidence.notEvaluated(),
                false,
                Set.of(PocketCandidateSource.GLOBAL_SHAPE)
        );
    }
}
