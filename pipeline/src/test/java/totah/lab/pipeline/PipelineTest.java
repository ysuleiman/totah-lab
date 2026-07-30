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
        Path openBabelPdbqt = resourcePath("/Q6UX53/Q6UX53_TMT1B_HUMAN_3_clean.pdbqt");

        Map<String, Object> config = new HashMap<>();
        config.put(ContextKeys.PLDDT_CUTOFF, 50.0);
        Pipeline pipeline = new PipelineFactory(tempDir).createDockingPipeline(config, targetPath);
        pipeline.run();

        String generatedPdbqtPath = pipeline.getContext().require(ContextKeys.RECEPTOR_PDBQT);
        Path generatedPdbqt = Path.of(generatedPdbqtPath);
        assertTrue(Files.isRegularFile(openBabelPdbqt));
        assertTrue(Files.isRegularFile(generatedPdbqt));
        Path artifactPdbqt = Path.of("target", "test-artifacts", "pipeline", "prepared_receptor.pdbqt");
        Files.createDirectories(artifactPdbqt.getParent());
        Files.copy(generatedPdbqt, artifactPdbqt, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        List<PdbqtAtom> expectedHeavyAtoms = heavyAtoms(openBabelPdbqt);
        List<PdbqtAtom> generatedHeavyAtoms = heavyAtoms(generatedPdbqt);
        assertEquals(expectedHeavyAtoms.size(), generatedHeavyAtoms.size(),
                "Generated PDBQT copied to " + artifactPdbqt.toAbsolutePath());
        for (int i = 0; i < expectedHeavyAtoms.size(); i++) {
            PdbqtAtom expected = expectedHeavyAtoms.get(i);
            PdbqtAtom generated = generatedHeavyAtoms.get(i);
            assertEquals(expected.identity(), generated.identity(), "heavy atom identity at index " + i);
            assertEquals(expected.x(), generated.x(), 1e-3, expected.identity() + " x");
            assertEquals(expected.y(), generated.y(), 1e-3, expected.identity() + " y");
            assertEquals(expected.z(), generated.z(), 1e-3, expected.identity() + " z");
        }
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

    private List<PdbqtAtom> heavyAtoms(Path pdbqt) throws Exception {
        return Files.readAllLines(pdbqt).stream()
                .filter(line -> line.startsWith("ATOM") || line.startsWith("HETATM"))
                .map(this::parsePdbqtAtom)
                .filter(atom -> !atom.atomName().startsWith("H"))
                .toList();
    }

    private PdbqtAtom parsePdbqtAtom(String line) {
        String[] fields = line.trim().split("\\s+");
        return new PdbqtAtom(
                fields[2],
                fields[3],
                fields[4],
                Integer.parseInt(fields[5]),
                Double.parseDouble(fields[6]),
                Double.parseDouble(fields[7]),
                Double.parseDouble(fields[8]));
    }

    private record PdbqtAtom(String atomName, String residueName, String chain, int residueNumber,
                             double x, double y, double z) {
        String identity() {
            return atomName + " " + residueName + " " + chain + ":" + residueNumber;
        }
    }
}
