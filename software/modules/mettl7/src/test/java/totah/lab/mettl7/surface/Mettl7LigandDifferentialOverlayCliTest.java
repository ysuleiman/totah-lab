package totah.lab.mettl7.surface;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        Path overlay = output.resolve("LIGAND_INTERACTION_DIFFERENTIAL_OVERLAY.csv");
        String csv = Files.readString(overlay);
        assertThat(csv).contains("NETARSUDIL,B,B_FAMILY_5")
                .contains("NETARSUDIL,A,A_ADMISSIBLE_CONTROL")
                .contains("DCMB,A,A_R1").contains("DCMB,B,B_R1")
                .contains("protein_differential_evidence,ligand_interaction_evidence");
        assertThat(Mettl7Csv.read(overlay)).hasSize(303);
        Map<String, String> a = Mettl7Csv.read(overlay).stream()
                .filter(row -> row.get("anchor").equals("NETARSUDIL")
                        && row.get("paralog").equals("A"))
                .findFirst().orElseThrow();
        assertThat(a).containsEntry("original_docking_mode", "15")
                .containsEntry("pdbqt_model_number", "1")
                .containsEntry("pose_extracted", "true");
        Map<String, String> r206 = Mettl7Csv.read(overlay).stream()
                .filter(row -> row.get("anchor").equals("NETARSUDIL")
                        && row.get("paralog").equals("B")
                        && row.get("protein_residue").equals("206"))
                .findFirst().orElseThrow();
        assertThat(r206).containsEntry("residue_identity_status", "PRESERVED")
                .containsEntry("local_environment_status", "ALTERED");
    }

    @Test
    void rejectsTamperedSurfaceEvidenceAndInadmissibleFamily() throws Exception {
        Path output = Files.createTempDirectory("mettl7-overlay-tamper-");
        Path prepared = Path.of("../../..", "research/mettl7-netarsudil-sam-mechanism",
                "local-flexibility/prepared");
        Mettl7DifferentialSurfaceCli.run(prepared.resolve("METTL7A_protein_only.pdb"),
                prepared.resolve("METTL7B_protein_only.pdb"), output);
        Files.writeString(output.resolve("METTL7A_VS_METTL7B_residue_scores.csv"),
                "tampered", java.nio.file.StandardOpenOption.APPEND);
        assertThatThrownBy(() -> Mettl7LigandDifferentialOverlayCli.run(
                Path.of("../athena/src/test/resources/mettl7-v2-regression"), output))
                .hasMessageContaining("hash mismatch");
        assertThatThrownBy(() -> Mettl7LigandDifferentialOverlayCli
                .requireDcmbAdmissible(Map.of("system", "7A_WT", "enantiomer", "R",
                        "family", "1", "sam_compatibility", "incompatible")))
                .hasMessageContaining("inadmissible");
    }
}
