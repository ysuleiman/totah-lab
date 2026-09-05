package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InteractionThresholdsTest {

    @Test
    void athenaDefaultsCarryLegacyHbAndPlipLiteratureValues() {
        InteractionThresholds thresholds = InteractionThresholds.athenaDefaults();

        // HB fields: legacy Athena convention.
        assertThat(thresholds.hydrogenAcceptorCutoff()).isEqualTo(2.5);
        assertThat(thresholds.donorAcceptorCutoff()).isEqualTo(3.5);
        assertThat(thresholds.minDonorAngleDegrees()).isEqualTo(120.0);
        assertThat(thresholds.donorBondCutoff()).isEqualTo(1.35);
        // Non-HB fields: PLIP 3.0.1 literature values.
        assertThat(thresholds.minDist()).isEqualTo(0.5);
        assertThat(thresholds.hydrophobicDistMax()).isEqualTo(4.0);
        assertThat(thresholds.saltBridgeDistMax()).isEqualTo(5.5);
        assertThat(thresholds.piStackDistMax()).isEqualTo(5.5);
        assertThat(thresholds.piStackParallelAngleDev()).isEqualTo(30.0);
        assertThat(thresholds.piStackTShapeAngleDev()).isEqualTo(30.0);
        assertThat(thresholds.piStackOffsetMax()).isEqualTo(2.0);
        assertThat(thresholds.piCationDistMax()).isEqualTo(6.0);
        assertThat(thresholds.piCationOffsetMax()).isEqualTo(2.0);
        assertThat(thresholds.piCationTertamineAngleMax()).isEqualTo(30.0);
        assertThat(thresholds.halogenDistMax()).isEqualTo(4.0);
        assertThat(thresholds.halogenAcceptorAngle()).isEqualTo(120.0);
        assertThat(thresholds.halogenDonorAngle()).isEqualTo(165.0);
        assertThat(thresholds.halogenAngleDev()).isEqualTo(30.0);
        assertThat(thresholds.provenance()).isEqualTo(
                InteractionThresholds.ATHENA_DEFAULTS_PROVENANCE);
    }

    @Test
    void plipReferenceCarriesPlipHbValuesWithUngatedAcceptorDistance() {
        InteractionThresholds thresholds = InteractionThresholds.plipReference();

        assertThat(thresholds.donorAcceptorCutoff()).isEqualTo(4.1);
        assertThat(thresholds.minDonorAngleDegrees()).isEqualTo(100.0);
        assertThat(thresholds.hydrogenAcceptorCutoff())
                .isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(thresholds.provenance())
                .isEqualTo(InteractionThresholds.PLIP_REFERENCE_PROVENANCE);
    }

    @Test
    void blankProvenanceRejected() {
        assertThatThrownBy(() -> new InteractionThresholds(
                0.5, 4.0, 5.5, 5.5, 30.0, 30.0, 2.0, 6.0, 2.0, 30.0,
                4.0, 120.0, 165.0, 30.0, 2.5, 3.5, 120.0, 1.35, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveCutoffRejected() {
        assertThatThrownBy(() -> new InteractionThresholds(
                0.5, -4.0, 5.5, 5.5, 30.0, 30.0, 2.0, 6.0, 2.0, 30.0,
                4.0, 120.0, 165.0, 30.0, 2.5, 3.5, 120.0, 1.35, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
