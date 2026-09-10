package totah.lab.athena.surface.differential;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SurfDiffWeightsTest {
    @Test
    void reproducesDistanceBoundariesAndClipping() {
        assertThat(SurfDiffWeights.distance(0.0, 5.0)).isEqualTo(1.0);
        assertThat(SurfDiffWeights.distance(1.0, 5.0)).isEqualTo(1.0);
        assertThat(SurfDiffWeights.distance(3.0, 5.0)).isEqualTo(0.5);
        assertThat(SurfDiffWeights.distance(5.0, 5.0)).isEqualTo(0.0);
        assertThat(SurfDiffWeights.distance(6.0, 5.0)).isEqualTo(0.0);
    }

    @Test
    void reproducesExposureCutoffAndHillFunction() {
        assertThat(SurfDiffWeights.exposure(0.049999)).isZero();
        assertThat(SurfDiffWeights.exposure(0.05))
                .isCloseTo(1.0 / (1.0 + Math.pow(0.5 / 0.55, 5.0)), within(1.0e-15));
        assertThat(SurfDiffWeights.exposure(0.5))
                .isCloseTo(1.0 / 1.03125, within(1.0e-15));
    }

    @Test
    void reproducesRdsClipping() {
        assertThat(ResidueDiscriminabilityScore.calculate(0.9, 0.8))
                .isCloseTo(0.7, within(1.0e-15));
        assertThat(ResidueDiscriminabilityScore.calculate(0.1, 0.2)).isZero();
    }
}
