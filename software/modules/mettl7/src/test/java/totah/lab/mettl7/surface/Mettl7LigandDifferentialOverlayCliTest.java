package totah.lab.mettl7.surface;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mettl7LigandDifferentialOverlayCliTest {
    @Test void commandLineLegacyOverlayIsDisabledUnlessExplicitlyEnabled() {
        assertThatThrownBy(() -> Mettl7LigandDifferentialOverlayCli.main(new String[]{"fixtures","output"}))
                .hasMessageContaining("NONCANONICAL_LEGACY_OVERLAY_DISABLED")
                .hasMessageContaining("Mettl7RecognitionBatchMaterializer");
    }
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
        List<Map<String, String>> rows = Mettl7Csv.read(overlay);
        assertThat(rows).hasSize(170);
        assertThat(rows.stream()
                .filter(row -> row.get("anchor").equals("DCMB")))
                .allSatisfy(row -> assertThat(row.get("perception_provenance"))
                        .contains("PerceptionSummary[side=ligand, "
                                + "hydrophobicProvenance=BOND_GRAPH, "
                                + "hydrophobicAtomCount=5, ringCount=1, "
                                + "degradedRingCount=0"));
        Map<String, String> a = rows.stream()
                .filter(row -> row.get("anchor").equals("NETARSUDIL")
                        && row.get("paralog").equals("A"))
                .findFirst().orElseThrow();
        assertThat(a).containsEntry("original_docking_mode", "15")
                .containsEntry("pdbqt_model_number", "1")
                .containsEntry("pose_extracted", "true");
        Map<String, String> r206 = rows.stream()
                .filter(row -> row.get("anchor").equals("NETARSUDIL")
                        && row.get("paralog").equals("B")
                        && row.get("protein_residue").equals("206"))
                .findFirst().orElseThrow();
        assertThat(r206).containsEntry("residue_identity_status", "PRESERVED")
                .containsEntry("local_environment_status", "ALTERED");
        assertThat(Files.readString(output.resolve("overlay_receipt.txt")))
                .contains("canonical_status=NONCANONICAL_LEGACY_OVERLAY")
                .contains("Mettl7RecognitionBatchMaterializer");
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
