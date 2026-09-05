package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7MechanisticMatrixV2PanelTest {

    @Test
    void containsEveryRequiredReceptorExactlyOnce() {
        var receptors = Mettl7MechanisticMatrixV2Panel.receptors();
        assertThat(receptors).hasSize(16);
        assertThat(new HashSet<>(receptors.stream()
                .map(ReceptorBackground::id).toList())).hasSize(16);
        assertThat(receptors.stream().filter(r -> r.id().equals("A2"))
                .findFirst().orElseThrow().substitutions()).containsExactly("Y47S");
        assertThat(receptors.stream().filter(r -> r.id().equals("B2"))
                .findFirst().orElseThrow().substitutions()).containsExactly("S47Y");
    }

    @Test
    void containsEveryRequiredCompoundBranchExactlyOnce() {
        var compounds = Mettl7MechanisticMatrixV2Panel.compounds();
        assertThat(compounds).hasSize(22);
        assertThat(new HashSet<>(compounds.stream()
                .map(CompoundBranch::id).toList())).hasSize(22);
        assertThat(compounds.stream()
                .filter(CompoundBranch::productiveStateSearchRequired))
                .hasSize(16);
        assertThat(Mettl7MechanisticMatrixV2Panel.nominalDockingCellCount())
                .isEqualTo(352);
    }
}
