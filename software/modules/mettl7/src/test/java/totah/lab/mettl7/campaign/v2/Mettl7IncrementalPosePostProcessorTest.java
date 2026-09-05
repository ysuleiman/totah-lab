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
                Map.entry("seed", 1), Map.entry("receptorPath", receptor.toString()),
                Map.entry("receptorSha256", sha(receptor)), Map.entry("posesSha256", sha(poses))));
        Path ledger = temporary.resolve("ledger.csv");
        Files.writeString(ledger, "run_id,species_id,acceptor_atom\n"
                + "A0__TEST_S__s1,TEST_S,S\nB0__TEST_S__s1,TEST_S,S\n");
        Path csv = temporary.resolve("raw.csv"), summary = temporary.resolve("summary.json");
        var processor = new Mettl7IncrementalPosePostProcessor();

        var first = processor.process(ledger, runs, csv, summary);
        String firstBytes = Files.readString(csv);
        var second = processor.process(ledger, runs, csv, summary);

        assertEquals(first, second);
        assertEquals(firstBytes, Files.readString(csv));
        assertEquals(1, first.validRuns());
        assertEquals(1, first.rawPoseRows());
        assertEquals(1, first.remainingRuns());
        assertFalse(first.biologicalConclusionAuthorized());
        assertTrue(firstBytes.contains("GEOMETRY_PASS_CHEMISTRY_UNASSESSED"));
        assertTrue(firstBytes.contains("RAW_COMPUTATIONAL_EVIDENCE"));
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
