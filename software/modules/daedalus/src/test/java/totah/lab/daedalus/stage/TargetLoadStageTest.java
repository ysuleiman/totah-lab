package totah.lab.daedalus.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.factory.ProteinFactory;
import totah.lab.hermes.file.reader.StructureReader;
import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.PipelineContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TargetLoadStageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsGaiaProteinThroughHermesReader() throws Exception {
        Path input = Files.createFile(temporaryDirectory.resolve("target.pdb"));
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(new Residue("ALA", 1, List.of())))));
        StructureReader reader = new FixedReader(structure);
        PipelineContext context = new PipelineContext(
                temporaryDirectory, temporaryDirectory)
                .with(ContextKeys.TARGET_PDB_PATH, input);

        new TargetLoadStage(reader, new ProteinFactory()).run(context);

        var protein = context.require(ContextKeys.TARGET_PROTEIN);
        assertEquals("target.pdb",
                ((totah.lab.gaia.molecule.Protein) protein).id());
        assertEquals(structure,
                ((totah.lab.gaia.molecule.Protein) protein).structure());
    }

    @Test
    void rejectsMissingTargetBeforeCallingReader() {
        PipelineContext context = new PipelineContext(
                temporaryDirectory, temporaryDirectory)
                .with(ContextKeys.TARGET_PDB_PATH,
                        temporaryDirectory.resolve("missing.pdb"));

        assertThrows(IOException.class,
                () -> new TargetLoadStage(
                        new FixedReader(new Structure(List.of())),
                        new ProteinFactory()).run(context));
    }

    private record FixedReader(Structure structure)
            implements StructureReader {
        @Override
        public Structure read(Path path) {
            return structure;
        }

        @Override
        public boolean supports(Path path) {
            return true;
        }
    }
}
