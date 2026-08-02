package totah.lab.daedalus.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.daedalus.docking.DockingInput;
import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.PipelineContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockingInputAssemblyStageTest {

    @TempDir
    Path tempPath;

    @Test
    void assemblesPreparedReceptorLigandAndOptionalFlexPaths()
            throws Exception {
        Path receptor = Files.writeString(
                tempPath.resolve("receptor.pdbqt"), "ATOM\n");
        Path ligand = Files.writeString(
                tempPath.resolve("ligand.pdbqt"), "ROOT\n");
        Path flex = Files.writeString(
                tempPath.resolve("flex.pdbqt"), "BEGIN_RES\n");
        PipelineContext context = new PipelineContext(tempPath, tempPath)
                .with(ContextKeys.RECEPTOR_PDBQT, receptor.toString())
                .with(ContextKeys.LIGAND_PDBQT_PATH, ligand)
                .with(ContextKeys.FLEX_PDBQT_PATH, flex.toString());

        new DockingInputAssemblyStage().run(context);

        DockingInput input = context.require(ContextKeys.DOCKING_INPUT);
        assertEquals(receptor, input.receptorPdbqt());
        assertEquals(ligand, input.ligandPdbqt());
        assertEquals(flex, input.flexPdbqt().orElseThrow());
    }

    @Test
    void rejectsMissingLigandArtifact() throws Exception {
        Path receptor = Files.writeString(
                tempPath.resolve("receptor.pdbqt"), "ATOM\n");
        PipelineContext context = new PipelineContext(tempPath, tempPath)
                .with(ContextKeys.RECEPTOR_PDBQT, receptor)
                .with(
                        ContextKeys.LIGAND_PDBQT_PATH,
                        tempPath.resolve("missing-ligand.pdbqt"));

        assertThrows(
                java.io.IOException.class,
                () -> new DockingInputAssemblyStage().run(context));
        assertTrue(!context.containsKey(ContextKeys.DOCKING_INPUT));
    }
}
