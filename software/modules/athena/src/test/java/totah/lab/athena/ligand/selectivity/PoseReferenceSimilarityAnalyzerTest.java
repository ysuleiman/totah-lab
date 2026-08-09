package totah.lab.athena.ligand.selectivity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.ligand.selectivity.DefaultMutationPoseComparatorTest.comparison;

class PoseReferenceSimilarityAnalyzerTest {

    private final PoseReferenceSimilarityAnalyzer analyzer =
            new PoseReferenceSimilarityAnalyzer();

    @Test
    void closerToAOnBothMetricsIsMoreALike() {
        PoseReferenceSimilarity similarity = analyzer.summarize(
                comparison(0.5, 0.4, 0.9),
                comparison(4.0, 3.5, 0.3)
        );

        assertThat(similarity.classification())
                .isEqualTo(PoseSimilarityClassification.MORE_A_LIKE);
        assertThat(similarity.centroidShiftToA()).isEqualTo(0.5);
        assertThat(similarity.centroidShiftToB()).isEqualTo(4.0);
        assertThat(similarity.rmsdToA()).isEqualTo(0.4);
        assertThat(similarity.rmsdToB()).isEqualTo(3.5);
        assertThat(similarity.contactSimilarityToA()).isEqualTo(0.9);
        assertThat(similarity.contactSimilarityToB()).isEqualTo(0.3);
        assertThat(similarity.reason()).isNotBlank();
    }

    @Test
    void closerToBOnBothMetricsIsMoreBLike() {
        PoseReferenceSimilarity similarity = analyzer.summarize(
                comparison(4.0, null, 0.2),
                comparison(0.8, null, 0.95)
        );

        assertThat(similarity.classification())
                .isEqualTo(PoseSimilarityClassification.MORE_B_LIKE);
        assertThat(similarity.rmsdToA()).isNull();
    }

    @Test
    void disagreementBetweenMetricsIsIntermediate() {
        // Shift favors A, contact similarity favors B.
        PoseReferenceSimilarity similarity = analyzer.summarize(
                comparison(0.5, null, 0.2),
                comparison(4.0, null, 0.9)
        );

        assertThat(similarity.classification())
                .isEqualTo(PoseSimilarityClassification.INTERMEDIATE);
    }

    @Test
    void tieOnlyOnOneMetricIsAlsoIntermediate() {
        // Shifts tie inside the band; contacts clearly favor A.
        PoseReferenceSimilarity similarity = analyzer.summarize(
                comparison(1.0, null, 0.9),
                comparison(1.2, null, 0.3)
        );

        assertThat(similarity.classification())
                .isEqualTo(PoseSimilarityClassification.INTERMEDIATE);
    }

    @Test
    void bothShiftsBeyondLargePoseChangeIsDifferentFromBoth() {
        PoseReferenceSimilarity similarity = analyzer.summarize(
                comparison(6.0, null, 0.1),
                comparison(7.0, null, 0.1)
        );

        assertThat(similarity.classification()).isEqualTo(
                PoseSimilarityClassification.DIFFERENT_FROM_BOTH);
        assertThat(similarity.reason())
                .contains("large-pose-change");
    }

    @Test
    void tiesOnBothMetricsAreAmbiguous() {
        PoseReferenceSimilarity similarity = analyzer.summarize(
                comparison(1.0, null, 0.80),
                comparison(1.2, null, 0.85)
        );

        assertThat(similarity.classification())
                .isEqualTo(PoseSimilarityClassification.AMBIGUOUS);
    }
}
