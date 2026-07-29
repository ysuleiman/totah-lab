package totah.lab.analysis.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.protein.analysis.ComplexAtom;
import totah.lab.protein.analysis.ComplexToken;
import totah.lab.protein.analysis.MolecularComplexPrediction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MolecularComplexPredictionArtifactWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesVersionedJsonAndCoordinateArtifact() throws Exception {
        MolecularComplexPrediction prediction =
                new MolecularComplexPrediction(
                        "BIOHUB_ESMFOLD2",
                        "model",
                        "SAH",
                        Instant.parse("2026-07-29T22:00:00Z"),
                        0.8,
                        0.7,
                        List.of(new ComplexToken(
                                0,
                                "L",
                                1,
                                "SAH",
                                0.9,
                                List.of(new ComplexAtom(
                                        "C1",
                                        "C",
                                        true,
                                        1.0,
                                        2.0,
                                        3.0
                                ))
                        ))
                );
        Path json = temporaryDirectory.resolve("prediction.json");
        Path pdb = temporaryDirectory.resolve("prediction.pdb");
        MolecularComplexPredictionArtifactWriter writer =
                new MolecularComplexPredictionArtifactWriter();

        writer.writeJson(json, prediction);
        writer.writePdb(pdb, prediction);

        JsonNode artifact = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .readTree(json.toFile());
        assertEquals("1.0", artifact.path("schemaVersion").asText());
        assertEquals(
                "LIGAND_CONDITIONED_COMPLEX_PREDICTION",
                artifact.path("analysisType").asText()
        );
        assertTrue(Files.readString(pdb).startsWith("HETATM"));
    }
}
