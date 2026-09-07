package totah.lab.mettl7.campaign.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mettl7IncrementalPosePostProcessorTest {
    @TempDir Path temporary;

    @Test
    void rebuildIsIdempotentAndNeverAuthorizesPartialConclusions() throws Exception {
        Path runs = temporary.resolve("runs");
        Path run = runs.resolve("A0__TEST_S__s1");
        Files.createDirectories(run);
        Path receptor = copy("receptor.pdbqt", temporary.resolve("receptor.pdbqt"));
        Path poses = copy("poses.pdbqt", run.resolve("poses.pdbqt"));
        new ObjectMapper().writeValue(run.resolve("receipt.json").toFile(), Map.ofEntries(
                Map.entry("runId", "A0__TEST_S__s1"), Map.entry("status", "COMPLETED_VALID"),
                Map.entry("parsedPoseCount", 1),
                Map.entry("seed", 1), Map.entry("receptorPath", receptor.toString()),
                Map.entry("receptorSha256", sha(receptor)), Map.entry("posesSha256", sha(poses))));
        Path ledger = temporary.resolve("ledger.csv");
        Files.writeString(ledger, "run_id,receptor_id,paralog,receptor_mutations,window_id,"
                + "compound_branch,species_id,stereoisomer,protonation_or_speciation,tautomer,"
                + "acceptor_atom,cofactor_state,seed,technical_status\n"
                + "A0__TEST_S__s1,A0,METTL7A,,A_WINDOW,TEST,TEST_S,NA,neutral,NA,S,SAM,1,PENDING\n"
                + "B0__TEST_S__s1,B0,METTL7B,,B_WINDOW,TEST,TEST_S,NA,neutral,NA,S,SAM,1,TECHNICAL_FAILURE\n");
        Path sdf = copy("ligand.sdf", temporary.resolve("ligand.sdf"));
        Path manifest = temporary.resolve("ligands.json");
        new ObjectMapper().writeValue(manifest.toFile(), Map.of("species", java.util.List.of(Map.ofEntries(
                Map.entry("species_id", "TEST_S"), Map.entry("prepared_sdf_path", sdf.toString()),
                Map.entry("prepared_sdf_sha256", sha(sdf)), Map.entry("formal_charge", 0),
                Map.entry("heavy_atom_count", 1)))));
        Path csv = temporary.resolve("raw.csv"), summary = temporary.resolve("summary.json");
        var processor = new Mettl7IncrementalPosePostProcessor();

        var first = processor.process(ledger, runs, manifest, csv, summary);
        String firstBytes = Files.readString(csv);
        var second = processor.process(ledger, runs, manifest, csv, summary);

        assertEquals(first, second);
        assertEquals(firstBytes, Files.readString(csv));
        assertEquals(1, first.validRuns());
        assertEquals(1, first.rawPoseRows());
        assertEquals(0, first.remainingRuns());
        assertEquals(1, first.predeclaredTechnicalFailures());
        assertFalse(first.biologicalConclusionAuthorized());
        assertTrue(firstBytes.contains("GEOMETRY_PASS_CHEMISTRY_UNASSESSED"));
        assertTrue(firstBytes.contains("RAW_COMPUTATIONAL_EVIDENCE"));
        assertTrue(firstBytes.contains("athena_hydrophobic_raw_count"));
        assertTrue(firstBytes.contains("athena_hydrophobic_refined_count"));
        assertTrue(firstBytes.contains("athena_refined_interaction_details"));
    }

    @Test
    void failsClosedWhenReceiptCountDiffersFromEmittedModels() throws Exception {
        Path runs = temporary.resolve("mismatch-runs");
        Path run = runs.resolve("A0__TEST_S__s1");
        Files.createDirectories(run);
        Path receptor = copy("receptor.pdbqt", temporary.resolve("mismatch-receptor.pdbqt"));
        Path poses = copy("poses.pdbqt", run.resolve("poses.pdbqt"));
        new ObjectMapper().writeValue(run.resolve("receipt.json").toFile(), Map.ofEntries(
                Map.entry("runId", "A0__TEST_S__s1"), Map.entry("status", "COMPLETED_VALID"),
                Map.entry("parsedPoseCount", 2), Map.entry("seed", 1),
                Map.entry("receptorPath", receptor.toString()),
                Map.entry("receptorSha256", sha(receptor)), Map.entry("posesSha256", sha(poses))));
        Path ledger = temporary.resolve("mismatch-ledger.csv");
        Files.writeString(ledger, "run_id,receptor_id,paralog,receptor_mutations,window_id,"
                + "compound_branch,species_id,stereoisomer,protonation_or_speciation,tautomer,"
                + "acceptor_atom,cofactor_state,seed,technical_status\n"
                + "A0__TEST_S__s1,A0,METTL7A,,A_WINDOW,TEST,TEST_S,NA,neutral,NA,S,SAM,1,PENDING\n");
        Path sdf = copy("ligand.sdf", temporary.resolve("mismatch-ligand.sdf"));
        Path manifest = temporary.resolve("mismatch-ligands.json");
        new ObjectMapper().writeValue(manifest.toFile(), Map.of("species", java.util.List.of(Map.ofEntries(
                Map.entry("species_id", "TEST_S"), Map.entry("prepared_sdf_path", sdf.toString()),
                Map.entry("prepared_sdf_sha256", sha(sdf)), Map.entry("formal_charge", 0),
                Map.entry("heavy_atom_count", 1)))));
        Path csv = temporary.resolve("mismatch.csv"), summary = temporary.resolve("mismatch.json");

        var result = new Mettl7IncrementalPosePostProcessor().process(
                ledger, runs, manifest, csv, summary);

        assertEquals(0, result.validRuns());
        assertEquals(1, result.invalidRuns());
        assertEquals(0, result.rawPoseRows());
        assertEquals(1, Files.readAllLines(csv).size(), "header only; no partial run rows");
    }

    @Test
    void emitsEveryModelIncludingFinalZeroInteractionPoseAndDuplicateFingerprints() throws Exception {
        Path runs = temporary.resolve("nine-runs");
        Path run = runs.resolve("A0__TEST_S__s1");
        Files.createDirectories(run);
        Path receptor = copy("receptor.pdbqt", temporary.resolve("nine-receptor.pdbqt"));
        String model = Files.readString(Path.of(getClass().getResource(
                "/mettl7/v2/incremental/poses.pdbqt").toURI()));
        StringBuilder all = new StringBuilder();
        for (int index = 1; index <= 9; index++) {
            String current = model.replace("MODEL 1", "MODEL " + index);
            if (index == 9) {
                current = current.replace("       4.800   0.000   0.000",
                        "     100.000 100.000 100.000");
            }
            all.append(current);
        }
        Path poses = run.resolve("poses.pdbqt");
        Files.writeString(poses, all.toString());
        new ObjectMapper().writeValue(run.resolve("receipt.json").toFile(), Map.ofEntries(
                Map.entry("runId", "A0__TEST_S__s1"), Map.entry("status", "COMPLETED_VALID"),
                Map.entry("parsedPoseCount", 9), Map.entry("seed", 1),
                Map.entry("receptorPath", receptor.toString()),
                Map.entry("receptorSha256", sha(receptor)), Map.entry("posesSha256", sha(poses))));
        Path ledger = temporary.resolve("nine-ledger.csv");
        Files.writeString(ledger, "run_id,receptor_id,paralog,receptor_mutations,window_id,"
                + "compound_branch,species_id,stereoisomer,protonation_or_speciation,tautomer,"
                + "acceptor_atom,cofactor_state,seed,technical_status\n"
                + "A0__TEST_S__s1,A0,METTL7A,,A_WINDOW,TEST,TEST_S,NA,neutral,NA,S,SAM,1,PENDING\n");
        Path sdf = copy("ligand.sdf", temporary.resolve("nine-ligand.sdf"));
        Path manifest = temporary.resolve("nine-ligands.json");
        new ObjectMapper().writeValue(manifest.toFile(), Map.of("species", java.util.List.of(Map.ofEntries(
                Map.entry("species_id", "TEST_S"), Map.entry("prepared_sdf_path", sdf.toString()),
                Map.entry("prepared_sdf_sha256", sha(sdf)), Map.entry("formal_charge", 0),
                Map.entry("heavy_atom_count", 1)))));
        Path csv = temporary.resolve("nine.csv"), summary = temporary.resolve("nine.json");

        var result = new Mettl7IncrementalPosePostProcessor().process(
                ledger, runs, manifest, csv, summary);

        assertEquals(1, result.validRuns());
        assertEquals(9, result.rawPoseRows());
        assertEquals(10, Files.readAllLines(csv).size());
    }

    private Path copy(String name, Path target) throws IOException {
        try (var input = getClass().getResourceAsStream("/mettl7/v2/incremental/" + name)) {
            Files.copy(input, target);
        }
        return target;
    }
    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
