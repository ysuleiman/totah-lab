package totah.lab.athena.surface.differential;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SurfDiffSubjectAggregatorTest {
    private static final ResidueId ID = new ResidueId("A", 1, null);

    @Test
    void takesIndependentColumnWiseMinima() {
        DifferentialSurfaceMap first = map(score(0.7, 0.2));
        DifferentialSurfaceMap second = map(score(0.3, 0.8));
        AggregatedDifferentialResidueScore result =
                SurfDiffSubjectAggregator.minima(List.of(first, second)).getFirst();
        assertThat(result.minimumRup()).isEqualTo(0.3);
        assertThat(result.minimumRus()).isEqualTo(0.2);
        assertThat(result.minimumRss()).isCloseTo(0.2, within(1.0e-15));
    }

    @Test
    void computesRdsFromCohortMinima() {
        double result = SurfDiffSubjectAggregator.rds(
                List.of(score(0.1, 0.1), score(0.1, 0.2)),
                List.of(score(0.1, 0.8), score(0.1, 0.7)));
        assertThat(result).isCloseTo(0.5, within(1.0e-15));
    }

    private static DifferentialResidueScore score(double rup, double rus) {
        return new DifferentialResidueScore(
                ID, Optional.empty(), 0.5, 0.9, rup, rus, 1.0 - rus);
    }

    private static DifferentialSurfaceMap map(DifferentialResidueScore score) {
        return new DifferentialSurfaceMap(
                DifferentialSurfaceMode.SURFDIFF_COMPATIBLE,
                DifferentialSurfaceOptions.SURFDIFF_COMPATIBLE,
                List.of(score), Map.of());
    }
}
