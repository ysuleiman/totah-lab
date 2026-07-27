package totah.lab.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineResourcePanelTest {

    private static final List<String> SUPPORTED_RECEPTORS = List.of(
            "1CRN.pdb",
            "1A8O.pdb",
            "1UBQ.pdb",
            "Q6UX53_TMT1B_HUMAN.pdb");

    private static final List<String> UNSUPPORTED_COMPLEXES = List.of(
            "1A4W.pdb",
            "1G9V.pdb",
            "1GZX.pdb",
            "1HVR.pdb",
            "2POR.pdb",
            "4HVP.pdb");

    @TempDir
    Path tempDir;

    @Test
    void preparesSupportedReceptorPanel() throws Exception {
        for (String pdbName : SUPPORTED_RECEPTORS) {
            Pipeline pipeline = pipelineFor(pdbName);

            pipeline.run();

            PipelineContext context = pipeline.getContext();
            Path pdbqt = Path.of((String) context.require(ContextKeys.RECEPTOR_PDBQT));
            assertTrue(Files.isRegularFile(pdbqt), pdbName);
            assertNotNull(context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT), pdbName);
            assertNotNull(context.require(ContextKeys.RESIDUE_STATE_REPORT), pdbName);
            assertNotNull(context.require(ContextKeys.HYDROGENATION_REPORT), pdbName);
            assertNotNull(context.require(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT), pdbName);
            assertNotNull(context.require(ContextKeys.TOPOLOGY_BUILD_REPORT), pdbName);
            assertNotNull(context.require(ContextKeys.CHARGE_ASSIGNMENT_REPORT), pdbName);
            assertNotNull(context.require(ContextKeys.AD4_ATOM_TYPING_REPORT), pdbName);
            assertNotNull(context.require(ContextKeys.PDBQT_EXPORT_REPORT), pdbName);
        }
    }

    @Test
    void rejectsComplexesWithoutExplicitSpecialResiduePolicy() throws Exception {
        for (String pdbName : UNSUPPORTED_COMPLEXES) {
            Pipeline pipeline = pipelineFor(pdbName);

            RuntimeException error = assertThrows(RuntimeException.class, pipeline::run, pdbName);

            assertTrue(error.getMessage().contains("Unsupported residue")
                            || error.getMessage().contains("not supported")
                            || error.getMessage().contains("No residue template")
                            || error.getMessage().contains("combined terminal templates"),
                    pdbName + ": " + error.getMessage());
        }
    }

    @Test
    void tysSupportAllowsOneA4wToAdvanceToNextUnsupportedResidue() throws Exception {
        Pipeline pipeline = pipelineFor("1A4W.pdb");

        RuntimeException error = assertThrows(RuntimeException.class, pipeline::run);

        assertTrue(error.getMessage().contains("Unsupported residue QWE H:373"), error.getMessage());
        assertTrue(!error.getMessage().contains("TYS I:363"), error.getMessage());
    }

    private Pipeline pipelineFor(String pdbName) throws Exception {
        Path pdb = resourcePath("/pipeline/" + pdbName);
        Map<String, Object> config = Map.of(ContextKeys.PLDDT_CUTOFF, 0.0);
        return new PipelineFactory(tempDir.resolve(pdbName)).createDockingPipeline(config, pdb);
    }

    private Path resourcePath(String resourceName) throws Exception {
        URL resource = getClass().getResource(resourceName);
        assertNotNull(resource, resourceName + " resource missing");
        return Path.of(resource.toURI());
    }
}
