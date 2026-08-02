package totah.lab.pocket.visualization;

import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;

import java.util.Collection;
import java.util.Comparator;

final class PocketRanking {
    private PocketRanking() {
    }

    static Pocket preferredPocket(Collection<Pocket> pockets) {
        return pockets.stream()
                .filter(pocket -> pocket.alphaSphereSet()
                        .filter(set -> !set.spheres().isEmpty())
                        .isPresent())
                .max(Comparator.comparingDouble(PocketRanking::rankingScore))
                .orElseGet(() -> pockets.stream().findFirst().orElse(null));
    }

    static double rankingScore(Pocket pocket) {
        if (pocket.source() == PocketSource.FPOCKET) {
            return pocket.metric(PocketMetricType.FPOCKET_DRUGGABILITY)
                    .orElseGet(() -> pocket.metric(
                            PocketMetricType.FPOCKET_SCORE).orElse(0.0));
        }
        if (pocket.source() == PocketSource.P2RANK) {
            return pocket.metric(PocketMetricType.P2RANK_PROBABILITY)
                    .orElse(0.0);
        }
        return 0.0;
    }

    static Double druggabilityScore(Pocket pocket) {
        return pocket.metric(PocketMetricType.FPOCKET_DRUGGABILITY)
                .isPresent()
                ? pocket.metric(PocketMetricType.FPOCKET_DRUGGABILITY)
                        .getAsDouble()
                : null;
    }
}
