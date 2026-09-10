package totah.lab.athena.surface.differential;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurfDiffPhysicochemicalDifferenceTest {
    @Test
    void reproducesFrozenMatrixCasesAndMissingResidue() {
        assertThat(SurfDiffPhysicochemicalDifference.betweenThreeLetter("ALA", "ALA"))
                .isZero();
        assertThat(SurfDiffPhysicochemicalDifference.betweenThreeLetter("LYS", "GLU"))
                .isEqualTo(8.0 / 18.0);
        assertThat(SurfDiffPhysicochemicalDifference.betweenThreeLetter("PHE", "TYR"))
                .isEqualTo(5.0 / 18.0);
        assertThat(SurfDiffPhysicochemicalDifference.betweenThreeLetter("ALA", "XXX"))
                .isEqualTo(1.0);
    }
}
