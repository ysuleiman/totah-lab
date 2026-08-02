package totah.lab.biohub.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BiohubSelectedLigandBatchTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsSelectedLigandsFromExistingManifest() throws Exception {
        Path manifest = temporaryDirectory.resolve("manifest.json");
        Files.writeString(manifest, """
                {"entries":[{"candidate":{"ligandId":"MCULE-1",
                "scorePrimary":-10.0,"scoreComparison":-7.0,"delta":3.0},
                "smiles":"CC(=O)O"}]}
                """);

        var result = new BiohubSelectedLigandBatch().readSourceLigands(manifest);

        assertEquals(List.of("MCULE-1"), result.keySet().stream().toList());
        assertEquals("CC(=O)O", result.get("MCULE-1").smiles());
        assertEquals(3.0, result.get("MCULE-1").selectivityDelta());
    }
}
