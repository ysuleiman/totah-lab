package totah.lab.hermes.file.pocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.hermes.file.pocket.reader.AutoDetectingPocketReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class P2RankAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void supportsDirectoryWithBarePredictionJson() throws Exception {
        Files.writeString(tempDir.resolve("prediction.json"),
                predictionJson());

        assertTrue(new P2RankAdapter().supports(tempDir));
    }

    @Test
    void supportsDirectoryWithPredictionJsonUnderPrank() throws Exception {
        Path prank = Files.createDirectory(tempDir.resolve("prank"));
        Files.writeString(prank.resolve("prediction.json"),
                predictionJson());

        assertTrue(new P2RankAdapter().supports(tempDir));
    }

    @Test
    void supportsPredictionJsonFileDirectly() throws Exception {
        Path prediction = Files.writeString(
                tempDir.resolve("prediction.json"), predictionJson());

        assertTrue(new P2RankAdapter().supports(prediction));
    }

    @Test
    void rejectsDirectoryWithoutPredictionJson() {
        assertFalse(new P2RankAdapter().supports(tempDir));
    }

    @Test
    void autoDetectingReaderReadsBarePredictionJsonLayout()
            throws Exception {

        Files.writeString(tempDir.resolve("prediction.json"),
                predictionJson());

        List<Pocket> pockets =
                new AutoDetectingPocketReader().read(tempDir);

        assertEquals(1, pockets.size());
        Pocket pocket = pockets.getFirst();
        assertEquals(PocketSource.P2RANK, pocket.source());
        assertEquals("pocket1", pocket.name());
        assertEquals(2, pocket.residues().size());
        assertEquals("A", pocket.residues().getFirst().chainId());
        assertEquals(10, pocket.residues().getFirst().residueNumber());
    }

    private String predictionJson() {
        return """
                {
                  "pockets": [
                    {
                      "name": "pocket1",
                      "rank": "1",
                      "score": "27.20",
                      "center": ["1.0", "2.0", "3.0"],
                      "residues": ["A_10", "B_20"]
                    }
                  ]
                }
                """;
    }
}
