package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.protein.Atom;
import totah.lab.protein.Residue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetLoadStageTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsPdbAndPublishesResiduesInFileOrder() throws Exception {
        PipelineContext context = contextWithTarget(resourcePath("/hetatm_fragment.pdb"));

        new TargetLoadStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(3, residues.size());

        Residue first = residues.getFirst();
        assertEquals("CYS", first.getName());
        assertEquals("A", first.getChain());
        assertEquals(32, first.getNumber());
        assertEquals(List.of("N", "CA", "C", "CB", "O", "SG"), atomNames(first));

        Residue mse = residues.get(2);
        assertEquals("MSE", mse.getName());
        assertEquals(40, mse.getNumber());
        assertEquals("Se", mse.getAtom("SE").getElement().getSymbol());
    }

    @Test
    void loadsCifInput() throws Exception {
        PipelineContext context = contextWithTarget(resourcePath("/Q6UX53/Q6UX53_TMT1B_HUMAN.cif"));

        new TargetLoadStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertFalse(residues.isEmpty());
        assertEquals("A", residues.getFirst().getChain());
        assertEquals(1, residues.getFirst().getNumber());
    }

    @Test
    void requiresTargetPathInContext() {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new TargetLoadStage().run(context));

        assertTrue(error.getMessage().contains(ContextKeys.TARGET_PDB_PATH));
    }

    @Test
    void rejectsMissingFileBeforeParsing() {
        Path missing = tempDir.resolve("missing.pdb");
        PipelineContext context = contextWithTarget(missing);

        IOException error = assertThrows(
                IOException.class,
                () -> new TargetLoadStage().run(context));

        assertTrue(error.getMessage().contains("does not exist"));
    }

    @Test
    void rejectsDirectoryBeforeParsing() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("structure.pdb"));
        PipelineContext context = contextWithTarget(directory);

        IOException error = assertThrows(
                IOException.class,
                () -> new TargetLoadStage().run(context));

        assertTrue(error.getMessage().contains("not a regular file"));
    }

    @Test
    void rejectsUnsupportedStructureFormat() throws IOException {
        Path textFile = tempDir.resolve("target.txt");
        Files.writeString(textFile, "not a structure");
        PipelineContext context = contextWithTarget(textFile);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new TargetLoadStage().run(context));

        assertTrue(error.getMessage().contains("Unsupported structure format"));
    }

    @Test
    void rejectsParseableFileWithNoResidues() throws IOException {
        Path emptyPdb = tempDir.resolve("empty.pdb");
        Files.writeString(emptyPdb, """
                HEADER    EMPTY STRUCTURE
                END
                """);
        PipelineContext context = contextWithTarget(emptyPdb);

        Exception error = assertThrows(
                Exception.class,
                () -> new TargetLoadStage().run(context));

        assertInstanceOf(IllegalStateException.class, error);
        assertTrue(error.getMessage().contains("No residues loaded"));
    }

    private PipelineContext contextWithTarget(Path targetPath) {
        return new PipelineContext(tempDir, tempDir.resolve("run"))
                .with(ContextKeys.TARGET_PDB_PATH, targetPath);
    }

    private Path resourcePath(String resourceName) throws URISyntaxException {
        return Path.of(getClass().getResource(resourceName).toURI());
    }

    private List<String> atomNames(Residue residue) {
        return residue.getAtoms().stream()
                .map(Atom::getName)
                .toList();
    }
}
