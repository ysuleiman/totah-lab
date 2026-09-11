package totah.lab.mettl7.recognition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.recognition.EvidenceQuality;
import totah.lab.mettl7.surface.Mettl7FrozenDifferentialSurfaceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GenericRecognitionLigandManifestAdapterTest {
    private static final Map<String, String> SDF_HASHES = Map.of(
            "ar_13503_deesterified", "ab6327dd3d4fc11b25107225d42de049ef38a80d90c6e9b4c455859c4cb52e38",
            "des_ortho_methyl", "d4b8d6183e31efab49a9d6f2ca996dbc446da825709987af53455fb8df54819c",
            "des_para_methyl", "ba7a05515a9c0bc2b61a9389e5cd8d9b99771066892045d6b607c19d6eba2576",
            "n_acetyl_amine", "df59888fab7a91cf4cf45d703c00309c61b23457359bcaffa12b8e0505a46ea8",
            "branch_point_enantiomer", "8ddf8a699c2a4bb06ebb2c0659457da1b96be42dea548bc87c6c4893459d776b",
            "quinoline_regioisomer", "cce7a0ae1d2a728d5460a4554adcf02284cbb88e3155d887c314d0ecc6b41df2");
    private static final Map<String, Long> EXPECTED_ARMS = Map.ofEntries(
            Map.entry("AR_13503_DEESTERIFIED_A", 60L), Map.entry("AR_13503_DEESTERIFIED_B", 59L),
            Map.entry("DES_ORTHO_METHYL_A", 59L), Map.entry("DES_ORTHO_METHYL_B", 60L),
            Map.entry("DES_PARA_METHYL_A", 59L), Map.entry("DES_PARA_METHYL_B", 60L),
            Map.entry("N_ACETYL_AMINE_A", 59L), Map.entry("N_ACETYL_AMINE_B", 60L),
            Map.entry("BRANCH_POINT_ENANTIOMER_A", 60L), Map.entry("BRANCH_POINT_ENANTIOMER_B", 60L),
            Map.entry("QUINOLINE_REGIOISOMER_A", 58L), Map.entry("QUINOLINE_REGIOISOMER_B", 60L));
    @TempDir Path temporary;

    @Test void accountsForEverySelectedExistingAnaloguePose() throws Exception {
        Path root = Path.of("../../..").toAbsolutePath().normalize();
        Path output = System.getProperty("mettl7.analogue.output") == null
                ? temporary : Path.of(System.getProperty("mettl7.analogue.output"));
        var result = GenericRecognitionLigandManifestAdapter.run(root,
                root.resolve("research/mettl7-netarsudil-sam-mechanism/phase2-matched-sar/run_manifest.json"),
                output, SDF_HASHES.keySet());
        assertThat(result.summary().outcomes()).hasSize(714).allSatisfy(outcome -> {
            assertThat(outcome.status()).isEqualTo("ADMITTED_ADEQUATE");
            assertThat(outcome.perceptionDegraded()).isFalse();
            assertThat(outcome.featureReceipt()).matches("[0-9a-f]{64}");
        });
        assertThat(result.summary().outcomes()).extracting(Mettl7RecognitionBatchMaterializer.Outcome::poseId)
                .doesNotHaveDuplicates();
        assertThat(result.summary().outcomes().stream().collect(Collectors.groupingBy(
                Mettl7RecognitionBatchMaterializer.Outcome::arm, Collectors.counting()))).isEqualTo(EXPECTED_ARMS);
        assertThat(result.evidence()).hasSize(714);
        assertThat(result.evidence().stream().collect(Collectors.groupingBy(
                Mettl7RecognitionBatchMaterializer.MaterializedEvidence::arm, Collectors.counting()))).isEqualTo(EXPECTED_ARMS);
        assertThat(result.evidence()).allSatisfy(evidence -> {
            String arm = evidence.arm(), paralog = arm.substring(arm.length() - 1);
            String ligand = arm.substring(0, arm.length() - 2).toLowerCase(Locale.ROOT);
            var observation = evidence.observation();
            assertThat(observation.quality()).isEqualTo(EvidenceQuality.ADEQUATE);
            assertThat(observation.graph().provenance().proteinId()).isEqualTo(paralog);
            assertThat(observation.graph().provenance().ligandId()).isEqualTo(ligand);
            assertThat(SDF_HASHES).containsKey(ligand);
            String surfaceHash = paralog.equals("A") ? Mettl7FrozenDifferentialSurfaceLoader.A_VS_B_SHA256
                    : Mettl7FrozenDifferentialSurfaceLoader.B_VS_A_SHA256;
            assertThat(evidence.surfDiffSha256()).isEqualTo(surfaceHash);
            assertThat(observation.graph().provenance().representativeProvenance())
                    .hasValueSatisfying(value -> assertThat(value).contains(surfaceHash));
        });
        List<String> manifest = Files.readAllLines(output.resolve("ANALOGUE_RECOGNITION_MANIFEST.csv"));
        assertThat(manifest).hasSize(37);
        SDF_HASHES.forEach((id, hash) -> {
            List<String> rows = manifest.stream().filter(line -> line.startsWith("\"" + id + "\",")).toList();
            assertThat(rows).hasSize(6).allSatisfy(row -> assertThat(row).contains("\"" + hash + "\""));
        });
        List<String> basins = Files.readAllLines(output.resolve("basins/BASINS.csv"));
        EXPECTED_ARMS.keySet().forEach(arm -> assertThat(basins)
                .anyMatch(line -> line.startsWith("\"" + arm + "\",\"2.0\",\"0.4\",")));
        for (Path path : List.of(result.summary().receipt(), output.resolve("materialization/DIAGNOSTIC_SUMMARY.txt"),
                output.resolve("basins/BASIN_EXECUTION_RECEIPT.txt"))) {
            assertThat(Files.readString(path)).doesNotContain("NETARSUDIL", "DCMB", "dcmb_pi_evidence");
        }
        assertThat(Files.readString(output.resolve("basins/BASIN_EXECUTION_RECEIPT.txt")))
                .contains("RECURRENT_BASIN_PRESENCE=", "PERSISTENT_WITHIN_BASIN=",
                        "N_ACETYL_AMINE_B={ADMITTED_ADEQUATE=60}");
    }
}
