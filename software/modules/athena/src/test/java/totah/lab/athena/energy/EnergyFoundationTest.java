package totah.lab.athena.energy;

import org.junit.jupiter.api.Test;
import totah.lab.athena.landscape.DeterministicPerturbationGrid;
import totah.lab.athena.thermo.EnergyUnit;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnergyFoundationTest {
    @Test
    void energyRequiresTotalAndCannotMasqueradeAsFreeEnergy() {
        assertThatThrownBy(() -> new EnergyEvaluation("q0", Map.of(),
                EnergyUnit.KILOCALORIES_PER_MOLE, "engine", "ff", "parameters",
                ScientificMethod.STATE_ENERGY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnergyEvaluation("q0",
                Map.of(EnergyComponent.TOTAL_POTENTIAL, 1.0),
                EnergyUnit.KILOCALORIES_PER_MOLE, "engine", "ff", "parameters",
                ScientificMethod.FEP_ESTIMATE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deterministicGridIsStableAndContainsTheUnperturbedState() {
        var first = DeterministicPerturbationGrid.rigidBody(1.0, 1.0, 0.0, 1.0);
        var second = DeterministicPerturbationGrid.rigidBody(1.0, 1.0, 0.0, 1.0);
        assertThat(first).isEqualTo(second).hasSize(27);
        assertThat(first).anySatisfy(q -> {
            assertThat(q.translationAngstroms().x()).isZero();
            assertThat(q.translationAngstroms().y()).isZero();
            assertThat(q.translationAngstroms().z()).isZero();
        });
    }
}
