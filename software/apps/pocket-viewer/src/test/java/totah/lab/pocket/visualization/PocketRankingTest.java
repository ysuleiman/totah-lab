package totah.lab.pocket.visualization;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketMetric;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PocketRankingTest {
    @Test
    void fpocketUsesDruggabilityScoreWhenAvailable() {
        Pocket pocket = pocket(
                PocketSource.FPOCKET,
                new PocketMetric(PocketMetricType.FPOCKET_SCORE, 0.003),
                new PocketMetric(
                        PocketMetricType.FPOCKET_DRUGGABILITY, 0.832));

        assertThat(PocketRanking.rankingScore(pocket)).isEqualTo(0.832);
    }

    @Test
    void otherSourcesUsePrimaryScore() {
        Pocket pocket = pocket(
                PocketSource.P2RANK,
                new PocketMetric(
                        PocketMetricType.P2RANK_PROBABILITY, 0.71));

        assertThat(PocketRanking.rankingScore(pocket)).isEqualTo(0.71);
    }

    private static Pocket pocket(
            PocketSource source,
            PocketMetric... metrics) {
        return new Pocket(
                new PocketId("1"),
                "Pocket 1",
                source,
                new Point3D(0, 0, 0),
                List.of(),
                List.of(metrics),
                Optional.empty(),
                Optional.empty(),
                Map.of());
    }
}
