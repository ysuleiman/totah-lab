package totah.lab.prometheus.ingest.authoritative;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class AmberSanderOutputReaderTest {
    private static final Path REAL = Path.of("..", "..", "..", "analysis", "mettl7-phase2",
            "execution-unit-05L", "v2-fixed", "phi060_psi060_A_m10.out");

    @Test
    void parsesLastNativeEnergyBlockAndRunControls() throws Exception {
        AmberSanderResult result = new AmberSanderOutputReader().read(REAL);
        AmberEnergyComponents energy = result.components().value().orElseThrow();

        assertThat(result.software().value()).contains("Amber 26 SANDER");
        assertThat(result.fileAssignments().value().orElseThrow()).containsEntry("MDIN", "energy.in");
        assertThat(result.controls().value().orElseThrow()).containsEntry("imin", "1").containsEntry("cut", "999.00000");
        assertThat(energy.bond()).isEqualTo(5.8203);
        assertThat(energy.angle()).isEqualTo(63.0581);
        assertThat(energy.properTorsion()).isEqualTo(82.0587);
        assertThat(energy.ordinaryLennardJones()).isEqualTo(32.0322);
        assertThat(energy.oneFourLennardJones()).isEqualTo(31.1334);
        assertThat(energy.oneFourElectrostatics()).isEqualTo(-26.7472);
        assertThat(result.components().provenance().getFirst().locator()).contains("last-energy-block");
    }
}
