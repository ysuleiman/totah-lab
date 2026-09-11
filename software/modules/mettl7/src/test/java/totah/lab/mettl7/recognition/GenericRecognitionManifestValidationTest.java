package totah.lab.mettl7.recognition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericRecognitionManifestValidationTest {
    private static final Path FIXTURES = Path.of("src/test/resources/recognition/manifest-validation");
    private static final Set<String> SELECTED = Set.of("parent_netarsudil", "branch_point_enantiomer");
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;

    @Test void rejectsNonexistentRunLigand() throws Exception {
        ObjectNode manifest = manifest();
        firstRun(manifest).put("ligand", "does-not-exist.pdbqt");
        rejects(manifest, "missing ");
    }

    @Test void rejectsIncorrectRunLigandHash() throws Exception {
        ObjectNode manifest = manifest();
        firstRun(manifest).put("ligand_sha256", "0".repeat(64));
        rejects(manifest, "hash mismatch");
    }

    @Test void rejectsAnotherAnalogueDespiteValidActualHash() throws Exception {
        ObjectNode manifest = manifest();
        useLigand(manifest, "quinoline_regioisomer");
        rejects(manifest, "run ligand identity mismatch");
    }

    @Test void rejectsSameAtomCountWithDifferentMolecularIdentity() throws Exception {
        ObjectNode manifest = manifest();
        JsonNode parent = ligand(manifest, "parent_netarsudil"), other = ligand(manifest, "quinoline_regioisomer");
        assertThat(atomCount(parent)).isEqualTo(atomCount(other));
        assertThat(parent.path("canonical_isomeric_smiles").asText()).isNotEqualTo(other.path("canonical_isomeric_smiles").asText());
        useLigand(manifest, "quinoline_regioisomer");
        rejects(manifest, "run ligand identity mismatch");
    }

    @Test void rejectsEnantiomerPreparedProvenanceMismatch() throws Exception {
        ObjectNode manifest = manifest();
        JsonNode parent = ligand(manifest, "parent_netarsudil"), other = ligand(manifest, "branch_point_enantiomer");
        assertThat(atomCount(parent)).isEqualTo(atomCount(other));
        assertThat(parent.path("canonical_isomeric_smiles").asText()).isNotEqualTo(other.path("canonical_isomeric_smiles").asText());
        useLigand(manifest, "branch_point_enantiomer");
        rejects(manifest, "run ligand identity mismatch");
    }

    @Test void rejectsDifferentPathEvenWithIdenticalBytes() throws Exception {
        ObjectNode manifest = manifest();
        Path copy = temporary.resolve("copied.pdbqt");
        Files.copy(FIXTURES.resolve(firstRun(manifest).path("ligand").asText()), copy);
        firstRun(manifest).put("ligand", copy.toAbsolutePath().toString());
        rejects(manifest, "run ligand identity mismatch");
    }

    @Test void rejectsSelectedCompoundWithoutRuns() throws Exception {
        ObjectNode manifest = manifest();
        removeRuns(manifest, r -> r.path("compound").asText().equals("branch_point_enantiomer"));
        rejects(manifest, "missing selected run");
    }

    @Test void rejectsMissingBArm() throws Exception {
        ObjectNode manifest = manifest();
        removeRuns(manifest, r -> r.path("enzyme").asText().equals("7B"));
        rejects(manifest, "missing selected run");
    }

    @Test void rejectsMissingAArm() throws Exception {
        ObjectNode manifest = manifest();
        removeRuns(manifest, r -> r.path("enzyme").asText().equals("7A"));
        rejects(manifest, "missing selected run");
    }

    @Test void rejectsMissingSeed() throws Exception {
        ObjectNode manifest = manifest();
        ((ArrayNode) manifest.path("runs")).remove(0);
        rejects(manifest, "missing selected run");
    }

    @Test void rejectsDuplicateRun() throws Exception {
        ObjectNode manifest = manifest();
        ((ArrayNode) manifest.path("runs")).add(firstRun(manifest).deepCopy());
        rejects(manifest, "duplicate run ID");
    }

    @Test void rejectsDuplicateRunIdAcrossDifferentArms() throws Exception {
        ObjectNode manifest = manifest();
        ((ObjectNode) manifest.path("runs").get(1)).put("key", firstRun(manifest).path("key").asText());
        rejects(manifest, "duplicate run ID");
    }

    @Test void rejectsUndeclaredSeedRun() throws Exception {
        ObjectNode manifest = manifest();
        ObjectNode extra = firstRun(manifest).deepCopy().put("seed", 999).put("key", "7A_parent_netarsudil_seed999");
        ((ArrayNode) manifest.path("runs")).add(extra);
        rejects(manifest, "undeclared seed");
    }

    @Test void rejectsUndeclaredRunId() throws Exception {
        ObjectNode manifest = manifest();
        firstRun(manifest).put("key", "arbitrary-extra-run");
        rejects(manifest, "undeclared run ID");
    }

    @Test void rejectsUnknownEnzymeInsteadOfDefaultingToB() throws Exception {
        ObjectNode manifest = manifest();
        firstRun(manifest).put("enzyme", "7C");
        rejects(manifest, "unknown enzyme");
    }

    @Test void rejectsDuplicateDeclaredSeed() throws Exception {
        ObjectNode manifest = manifest();
        ((ArrayNode) manifest.path("seeds")).add(172904);
        rejects(manifest, "duplicate declared seed");
    }

    @Test void rejectsDuplicateSelectedLigandMetadata() throws Exception {
        ObjectNode manifest = manifest();
        ((ArrayNode) manifest.path("ligands")).add(ligand(manifest, "parent_netarsudil").deepCopy());
        rejects(manifest, "duplicate selected ligand");
    }

    private ObjectNode manifest() throws IOException {
        try (var reader = Files.newBufferedReader(FIXTURES.resolve("manifest.json"))) {
            return (ObjectNode) JSON.readTree(reader);
        }
    }

    private void rejects(ObjectNode manifest, String message) throws IOException {
        Path input = temporary.resolve("manifest.json"), output = temporary.resolve("output");
        try (var writer = Files.newBufferedWriter(input)) { JSON.writeValue(writer, manifest); }
        // Validation must also preserve a previous result if called with its output directory.
        Files.createDirectories(output);
        Path existing = output.resolve("ANALOGUE_RECOGNITION_MANIFEST.csv");
        Files.writeString(existing, "previous frozen manifest\n");
        assertThatThrownBy(() -> GenericRecognitionLigandManifestAdapter.run(FIXTURES, input, output, SELECTED))
                .isInstanceOf(IOException.class).hasMessageContaining(message);
        assertThat(Files.readString(existing)).isEqualTo("previous frozen manifest\n");
        assertThat(output.resolve("materialization")).doesNotExist();
        assertThat(output.resolve("basins")).doesNotExist();
    }

    private static ObjectNode firstRun(ObjectNode manifest) { return (ObjectNode) manifest.path("runs").get(0); }

    private static JsonNode ligand(ObjectNode manifest, String id) {
        for (JsonNode ligand : manifest.path("ligands")) if (ligand.path("compound").asText().equals(id)) return ligand;
        throw new IllegalArgumentException(id);
    }

    private static void useLigand(ObjectNode manifest, String id) {
        JsonNode ligand = ligand(manifest, id);
        firstRun(manifest).put("ligand", ligand.path("pdbqt").asText())
                .put("ligand_sha256", ligand.path("pdbqt_sha256").asText());
    }

    private static long atomCount(JsonNode ligand) throws IOException {
        try (var lines = Files.lines(FIXTURES.resolve(ligand.path("pdbqt").asText()))) {
            return lines.filter(line -> line.startsWith("ATOM  ") || line.startsWith("HETATM")).count();
        }
    }

    private static void removeRuns(ObjectNode manifest, Predicate<JsonNode> predicate) {
        ArrayNode runs = (ArrayNode) manifest.path("runs");
        for (int i = runs.size() - 1; i >= 0; i--) if (predicate.test(runs.get(i))) runs.remove(i);
    }
}
