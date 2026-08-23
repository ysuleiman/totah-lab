package totah.lab.prometheus.ingest.authoritative;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AmberPrmtopReaderTest {
    @TempDir Path temp;

    @Test
    void appliesAmberChargeScaleAndPreservesAtomOrder() throws Exception {
        Path topology = temp.resolve("test.prmtop");
        Files.writeString(topology, """
                %VERSION VERSION_STAMP = V0001.000
                %FLAG POINTERS
                %FORMAT(10I8)
                       2
                %FLAG ATOM_NAME
                %FORMAT(20a4)
                S1  H56 
                %FLAG CHARGE
                %FORMAT(5E16.8)
                 -0.18222300E+02  0.18222300E+02
                %FLAG AMBER_ATOM_TYPE
                %FORMAT(20a4)
                sh  hs  
                """);

        AmberTopologyResult result = new AmberPrmtopReader().read(topology);
        assertThat(result.atomCount().value()).contains(2);
        assertThat(result.atomNames().value().orElseThrow()).containsExactly("S1", "H56");
        assertThat(result.atomTypes().value().orElseThrow()).containsExactly("sh", "hs");
        assertThat(result.charges().value().orElseThrow()).containsExactly(-1.0, 1.0);
        assertThat(result.totalCharge().value().orElseThrow()).isZero();
        assertThat(result.charges().provenance().getFirst().sha256()).hasSize(64);
    }

    @Test
    void parsesFortranDExponentWithoutTransformingFixedWidthIdentifiers() throws Exception {
        Path topology = Path.of("src/test/resources/amber/identifier-and-d-exponent.prmtop");

        AmberTopologyResult result = new AmberPrmtopReader().read(topology);

        assertThat(result.atomNames().value().orElseThrow())
                .containsExactly("CD1", "HD11", "SD");
        assertThat(result.atomTypes().value().orElseThrow())
                .containsExactly("CD1", "HD11", "SD");
        assertThat(result.charges().value().orElseThrow())
                .containsExactly(1.0, -1.0, 0.5);
    }
}
