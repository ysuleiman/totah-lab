package totah.lab.athena.pocket.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.AlignmentInitialization;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PocketAssessmentRulesTest {

    private final PocketAssessmentRules rules =
            PocketAssessmentRules.defaults();

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

        assertEquals(
                PocketComparisonAssessment.INSUFFICIENT_EVIDENCE,
                rules.assess(evidence)
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

        assertEquals(
                PocketComparisonAssessment.REJECTED,
                rules.assess(evidence)
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

        assertEquals(
                PocketComparisonAssessment.CONFLICTING_EVIDENCE,
                rules.assess(evidence)
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

        assertEquals(
                PocketComparisonAssessment.CONFLICTING_EVIDENCE,
                rules.assess(evidence)
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

        assertEquals(
                PocketComparisonAssessment.STRONG_FUNCTIONAL_MATCH,
                rules.assess(evidence)
        );
    }

    @Test
    void strongRequiresContactConservationWhenLigandEvidenceExists() {
        LigandContactEvidence conserved = contacts(4, 4, 2, 1, 1, 0);
        LigandContactEvidence lost = contacts(4, 2, 2, 0, 0, 2);

        assertEquals(
                PocketComparisonAssessment.STRONG_FUNCTIONAL_MATCH,
                rules.assess(evidence(
                        0.50,
                        20,
                        0.80,
                        0.80,
                        1.0,
                        true,
                        conserved
                ))
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
                ))
        );

        // Chemistry high but contact conservation lost: probable.
        assertEquals(
                PocketComparisonAssessment.PROBABLE_FUNCTIONAL_MATCH,
                rules.assess(evidence(
                        0.50,
                        20,
                        0.80,
                        0.80,
                        1.0,
                        true,
                        lost
                ))
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

        assertEquals(
                PocketComparisonAssessment.PROBABLE_FUNCTIONAL_MATCH,
                rules.assess(evidence)
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

        assertEquals(
                PocketComparisonAssessment.PROBABLE_FUNCTIONAL_MATCH,
                rules.assess(evidence)
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

        assertEquals(
                PocketComparisonAssessment.GEOMETRIC_MATCH_ONLY,
                rules.assess(evidence)
        );
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

        return new PocketComparisonEvidence(
                retrieval(),
                alignment,
                residues,
                functional,
                PocketComparisonAssessment.INSUFFICIENT_EVIDENCE
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
