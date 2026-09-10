package totah.lab.mettl7.surface;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7LigandDifferentialOverlayCliTest {
    @Test
    void overlaysAllFourRequestedAnchorParalogPairs() throws Exception {
        Path output = Files.createTempDirectory("mettl7-overlay-");
        Path prepared = Path.of("../../..", "research/mettl7-netarsudil-sam-mechanism",
                "local-flexibility/prepared");
        Mettl7DifferentialSurfaceCli.run(prepared.resolve("METTL7A_protein_only.pdb"),
                prepared.resolve("METTL7B_protein_only.pdb"), output);
        Mettl7LigandDifferentialOverlayCli.run(Path.of("../athena/src/test/resources",
                "mettl7-v2-regression"), output);
        String csv = Files.readString(output.resolve(
                "LIGAND_INTERACTION_DIFFERENTIAL_OVERLAY.csv"));
        assertThat(csv).contains("NETARSUDIL,B,B_FAMILY_5")
                .contains("NETARSUDIL,A,A_ADMISSIBLE_CONTROL")
                .contains("DCMB,A,A_R1").contains("DCMB,B,B_R1")
                .contains("PROTEIN_DIFFERENTIAL_EVIDENCE|LIGAND_INTERACTION_EVIDENCE");
    }
}
