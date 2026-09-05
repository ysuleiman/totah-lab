package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7MechanisticMatrixV2ProtocolTest {

    @Test
    void freezesMatchedSamplingControls() {
        assertThat(Mettl7MechanisticMatrixV2Protocol.DOCKING_ENGINE)
                .isEqualTo("AutoDock Vina");
        assertThat(Mettl7MechanisticMatrixV2Protocol.EXHAUSTIVENESS)
                .isEqualTo(32);
        assertThat(Mettl7MechanisticMatrixV2Protocol.MAXIMUM_MODES)
                .isEqualTo(9);
        assertThat(Mettl7MechanisticMatrixV2Protocol.ENERGY_RANGE_KCAL_PER_MOL)
                .isEqualTo(3.0);
        assertThat(Mettl7MechanisticMatrixV2Protocol.SEEDS)
                .containsExactly(1, 7, 42);
        assertThat(Mettl7MechanisticMatrixV2Protocol.PRIMARY_COFACTOR_STATE)
                .isEqualTo("SAM");
        assertThat(Mettl7MechanisticMatrixV2Protocol.nominalSeededRunCount())
                .isEqualTo(1_056);
        assertThat(Mettl7MechanisticMatrixV2Protocol.poseOutputOptions()
                .maximumModes()).isEqualTo(9);
        assertThat(Mettl7MechanisticMatrixV2Protocol.poseOutputOptions()
                .energyRangeKcalPerMol()).isEqualTo(3.0);
    }
}
