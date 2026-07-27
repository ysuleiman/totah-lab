package totah.lab.pipeline;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Special residues are allowed through cleanup only when configured, but they
 * still need an Amber-compatible residue state before docking preparation can
 * assign hydrogens, topology, charges, and AD4 types.
 */
public class HetatmPipelineTest {

    @Test
    public void terminalSpecialResidueFailsBeforeDockingExport() throws Exception {
        URL resource = getClass().getResource("/hetatm_fragment.pdb");
        assertNotNull(resource, "hetatm_fragment.pdb test resource missing");
        Path targetPath = Path.of(resource.toURI());

        Map<String, Object> config = new HashMap<>();
        config.put(ContextKeys.PLDDT_CUTOFF, 50.0);
        Pipeline pipeline = new PipelineFactory().createDockingPipeline(config, targetPath);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                pipeline::run);

        assertTrue(error.getMessage().contains("MSE A:40"));
        assertTrue(error.getMessage().contains("combined terminal templates are not supported"));
    }
}
