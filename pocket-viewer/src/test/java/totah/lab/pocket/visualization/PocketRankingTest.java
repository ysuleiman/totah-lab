package totah.lab.pocket.visualization;

import org.junit.jupiter.api.Test;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.PocketSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PocketRankingTest {
    @Test
    void fpocketUsesDruggabilityScoreWhenAvailable() {
        Pocket pocket = Pocket.builder()
                .source(PocketSource.FPOCKET)
                .score(0.003)
                .attributes(Map.of("druggability score", "0.832"))
                .build();

        assertThat(PocketRanking.rankingScore(pocket)).isEqualTo(0.832);
    }

    @Test
    void otherSourcesUsePrimaryScore() {
        Pocket pocket = Pocket.builder()
                .source(PocketSource.P2RANK)
                .score(0.71)
                .build();

        assertThat(PocketRanking.rankingScore(pocket)).isEqualTo(0.71);
    }
}
