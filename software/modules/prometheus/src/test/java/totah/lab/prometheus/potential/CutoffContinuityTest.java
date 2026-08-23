package totah.lab.prometheus.potential;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.potential.delta.basis.SmoothCutoff;

class CutoffContinuityTest {
 @Test void lockedSwitchHasContinuousValueAndDerivative(){assertThat(SmoothCutoff.value(4.5)).isZero();assertThat(SmoothCutoff.derivative(4.5)).isZero();assertThat(Math.abs(SmoothCutoff.value(4.5-1e-6))).isLessThan(1e-10);assertThat(Math.abs(SmoothCutoff.derivative(4.5-1e-6))).isLessThan(1e-8);assertThat(SmoothCutoff.value(4.0)).isEqualTo(1);assertThat(SmoothCutoff.derivative(4.0)).isZero();}
}
