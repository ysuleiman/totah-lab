package totah.lab.athena.surface.differential;

import totah.lab.gaia.structure.ResidueId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact column-wise minimum aggregation used by SurfDiff result tables. */
public final class SurfDiffSubjectAggregator {
    private SurfDiffSubjectAggregator() {
    }

    public static List<AggregatedDifferentialResidueScore> minima(
            List<DifferentialSurfaceMap> maps) {
        Objects.requireNonNull(maps, "maps");
        if (maps.isEmpty()) {
            throw new IllegalArgumentException("at least one map is required");
        }
        LinkedHashMap<ResidueId, MutableMinima> minima = new LinkedHashMap<>();
        for (DifferentialSurfaceMap map : maps) {
            if (map.mode() != DifferentialSurfaceMode.SURFDIFF_COMPATIBLE) {
                throw new IllegalArgumentException("cannot mix scientific definitions");
            }
            for (DifferentialResidueScore score : map.residues()) {
                minima.computeIfAbsent(score.queryResidue(), ignored -> new MutableMinima())
                        .accept(score);
            }
        }
        int expectedCount = maps.size();
        List<AggregatedDifferentialResidueScore> result = new ArrayList<>();
        for (Map.Entry<ResidueId, MutableMinima> entry : minima.entrySet()) {
            if (entry.getValue().count != expectedCount) {
                throw new IllegalArgumentException(
                        "all maps must contain the same query residues");
            }
            MutableMinima row = entry.getValue();
            result.add(new AggregatedDifferentialResidueScore(
                    entry.getKey(), row.rup, row.rus, row.rss));
        }
        return List.copyOf(result);
    }

    public static double rds(
            List<DifferentialResidueScore> similar,
            List<DifferentialResidueScore> different) {
        if (similar.isEmpty() || different.isEmpty()) {
            throw new IllegalArgumentException("both cohorts require at least one score");
        }
        double minimumRss = similar.stream()
                .mapToDouble(DifferentialResidueScore::rss).min().orElseThrow();
        double minimumRus = different.stream()
                .mapToDouble(DifferentialResidueScore::rus).min().orElseThrow();
        return ResidueDiscriminabilityScore.calculate(minimumRss, minimumRus);
    }

    private static final class MutableMinima {
        private double rup = 1.0;
        private double rus = 1.0;
        private double rss = 1.0;
        private int count;

        void accept(DifferentialResidueScore score) {
            rup = Math.min(rup, score.rup());
            rus = Math.min(rus, score.rus());
            rss = Math.min(rss, score.rss());
            count++;
        }
    }
}
