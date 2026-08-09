package totah.lab.athena.ligand.pose;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;
import static totah.lab.athena.ligand.pose.AlphaSphereMetricsTest.pocket;
import static totah.lab.athena.ligand.pose.AlphaSphereMetricsTest.sphere;

class DefaultPosePocketScorerTest {

    @Test
    void appliesDefaultWeights() {
        PosePocketMetrics metrics = metrics(1.0, 0.5, 0.2);

        double score = new DefaultPosePocketScorer().score(metrics);

        assertThat(score).isCloseTo(
                0.50 * 1.0 + 0.35 * 0.5 + 0.15 * 0.2,
                offset(1.0e-9));
    }

    @Test
    void zeroEvidenceScoresZero() {
        PosePocketMetrics metrics = metrics(0.0, 0.0, 0.0);

        assertThat(new DefaultPosePocketScorer().score(metrics))
                .isEqualTo(0.0);
    }

    @Test
    void customWeightsReplaceDefaults() {
        DefaultPosePocketScorer scorer = new DefaultPosePocketScorer(
                new PosePocketScoringWeights(0.0, 1.0, 0.0));

        assertThat(scorer.score(metrics(1.0, 0.5, 0.2)))
                .isCloseTo(0.5, offset(1.0e-9));
    }

    @Test
    void weightsMustSumToOne() {
        assertThatThrownBy(() ->
                new PosePocketScoringWeights(0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1.0");
    }

    @Test
    void weightsMustBeUnitInterval() {
        assertThatThrownBy(() ->
                new PosePocketScoringWeights(1.5, 0.0, -0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PosePocketMetrics metrics(
            double containment,
            double contactCoverage,
            double centroidProximity
    ) {
        Pocket pocket = pocket("A", 0, 0,
                List.of(new ResidueId("A", 10, null)),
                Optional.empty(),
                List.of(sphere(0, 0, 2.0)));

        return new PosePocketMetrics(
                pocket,
                null,
                5.0,
                containment,
                PosePocketMetrics.ContainmentBasis.ALPHA_SPHERES,
                contactCoverage,
                0.0,
                centroidProximity
        );
    }
}
