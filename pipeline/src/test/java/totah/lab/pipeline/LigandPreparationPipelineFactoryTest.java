package totah.lab.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.ligand.selection.LigandSelection;
import totah.lab.ligand.selection.SelectedLigandPreparation;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandPreparationPipelineFactoryTest {

    @TempDir
    Path tempPath;

    @Test
    void preparesExplicitResidueFromIndependentPdbInput() throws Exception {
        Path ligandInput = resourcePath(
                "/ligand/4E1J-glycerol-panel.pdb");
        Pipeline pipeline = new LigandPreparationPipelineFactory(tempPath)
                .create(
                        Map.of(
                                ContextKeys.LIGAND_SELECTION,
                                new LigandSelection("GOL", "A", 601, ' ')),
                        ligandInput);

        pipeline.run();

        SelectedLigandPreparation result = pipeline.getContext().require(
                ContextKeys.LIGAND_PREPARATION_RESULT);
        Path output = pipeline.getContext().require(
                ContextKeys.LIGAND_PDBQT_PATH);
        assertEquals("GOL", result.selectedLigand().residue().getName());
        assertEquals("A", result.selectedLigand().residue().getChain());
        assertEquals(601, result.selectedLigand().residue().getNumber());
        assertTrue(pipeline.getContext().containsKey(ContextKeys.SELECTED_LIGAND));
        assertTrue(pipeline.getContext().containsKey(ContextKeys.LIGAND_GRAPH_RESULT));
        assertTrue(pipeline.getContext().containsKey(
                ContextKeys.LIGAND_HYDROGENATION_RESULT));
        assertTrue(pipeline.getContext().containsKey(
                ContextKeys.LIGAND_CHARGE_ASSIGNMENT_RESULT));
        assertTrue(pipeline.getContext().containsKey(
                ContextKeys.LIGAND_AD4_TYPING_RESULT));
        assertTrue(pipeline.getContext().containsKey(
                ContextKeys.LIGAND_TORSION_TREE_RESULT));
        assertTrue(Files.isRegularFile(output));
        assertTrue(Files.readString(output).contains("TORSDOF"));
    }

    private Path resourcePath(String resourceName) throws Exception {
        URL resource = getClass().getResource(resourceName);
        if (resource == null) {
            throw new IllegalStateException(
                    "Missing test resource " + resourceName);
        }
        return Path.of(resource.toURI());
    }
}
