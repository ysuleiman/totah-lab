package totah.lab.http.biohub.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.http.biohub.model.AtomComplex;
import totah.lab.http.biohub.model.ComplexToken;
import totah.lab.http.biohub.model.MolecularComplexPrediction;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;

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
        Atom atom = Atom.builder()
                .pdbSerial(1)
                .name("C1")
                .position(new Point3D(1.0, 2.0, 3.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.C)
                .build();
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
                                List.of(new AtomComplex(atom, true))
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
