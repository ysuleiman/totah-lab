package totah.lab.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.protein.Residue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PipelineTest {

    @Test
    public void testPipeline() throws Exception {

        Path targetPath = resourcePath("/Q6UX53/Q6UX53_TMT1B_HUMAN.pdb");
        Path expectedPdbqt = resourcePath("/Q6UX53/Q6UX53_TMT1B_HUMAN_2.pdbqt");

        Map<String, Object> config = new HashMap<>();
        config.put(ContextKeys.PLDDT_CUTOFF, 50.0);
        Pipeline pipeline = new PipelineFactory(tempDir).createDockingPipeline(config, targetPath);
        pipeline.run();

        String generatedPdbqtPath = pipeline.getContext().require(ContextKeys.RECEPTOR_PDBQT);
        Path generatedPdbqt = Path.of(generatedPdbqtPath);
        assertTrue(Files.isRegularFile(expectedPdbqt));
        assertTrue(Files.isRegularFile(generatedPdbqt));
        assertEquals(Files.readAllLines(expectedPdbqt), Files.readAllLines(generatedPdbqt));
    }

    @TempDir
    Path tempDir;

    @Test
    public void testPipeline2() throws Exception {
        Path path = resourcePath("/Q6UX53/Q6UX53_TMT1B_HUMAN.pdb");

        Pipeline pipeline = new PipelineFactory(tempDir).createDockingPipeline(
                Map.of(ContextKeys.PLDDT_CUTOFF, 1.0),
                path);
        pipeline.run();

        PipelineContext context = pipeline.getContext();
        context.put(ContextKeys.PLDDT_CUTOFF, 1.0);
        List<Residue> residueList = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertNotNull(residueList);
        assertEquals(244, residueList.size());
        String pathToFile = context.require(ContextKeys.RECEPTOR_PDBQT);
        Path receptorPdbqtPath = Path.of(pathToFile);
        assertTrue(Files.isRegularFile(receptorPdbqtPath));
        assertNotNull(context.require(ContextKeys.RESIDUE_STATE_REPORT));
        assertNotNull(context.require(ContextKeys.HYDROGENATION_REPORT));
        assertNotNull(context.require(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT));
        assertNotNull(context.require(ContextKeys.TOPOLOGY_BUILD_REPORT));
        assertNotNull(context.require(ContextKeys.CHARGE_ASSIGNMENT_REPORT));
        assertNotNull(context.require(ContextKeys.AD4_ATOM_TYPING_REPORT));
        assertNotNull(context.require(ContextKeys.PDBQT_EXPORT_REPORT));
    }

    private Path resourcePath(String resourceName) throws Exception {
        return Path.of(getClass().getResource(resourceName).toURI());
    }
}
