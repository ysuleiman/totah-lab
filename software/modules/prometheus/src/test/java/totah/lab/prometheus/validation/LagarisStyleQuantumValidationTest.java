package totah.lab.prometheus.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LagarisStyleQuantumValidationTest {
    @Test
    void solvesInfiniteSquareWellEndToEnd() {
        var result=LagarisStyleQuantumValidation.run();
        assertThat(result.passed()).isTrue();
        assertThat(result.optimizedEnergyHartree()).isLessThan(result.initialEnergyHartree());
        assertThat(result.absoluteEnergyErrorHartree()).isLessThanOrEqualTo(0.02);
        assertThat(result.normalizedWavefunctionRmse()).isLessThanOrEqualTo(0.03);
        assertThat(result.normalizedWavefunctionOverlap()).isGreaterThan(0.999);
        assertThat(result.leftBoundaryValue()).isZero();
        assertThat(result.rightBoundaryValue()).isZero();
    }
}
