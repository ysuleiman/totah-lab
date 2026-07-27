package totah.lab.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineOpenBabelVerifierTest {

    private static final List<String> OPEN_BABEL_VERIFIERS = List.of(
            "1CRN",
            "1HVR",
            "1UBQ",
            "4HVP",
            "Q6UX53_TMT1B_HUMAN");

    private static final List<String> PIPELINE_SUPPORTED_VERIFIERS = List.of(
            "1CRN",
            "1UBQ",
            "Q6UX53_TMT1B_HUMAN");

    @TempDir
    Path tempDir;

    @Test
    void openBabelVerifiersPreserveSourcePdbHeavyAtoms() throws Exception {
        for (String receptor : OPEN_BABEL_VERIFIERS) {
            Path pdb = resourcePath("/pipeline/" + receptor + ".pdb");
            Path pdbqt = resourcePath("/pipeline/" + receptor + ".pdbqt");

            List<HeavyAtom> source = pdbHeavyAtoms(pdb);
            List<HeavyAtom> verifier = pdbqtHeavyAtoms(pdbqt);

            assertEquals(source.size(), verifier.size(), receptor + " heavy atom count");
            for (int i = 0; i < source.size(); i++) {
                HeavyAtom expected = source.get(i);
                HeavyAtom actual = verifier.get(i);
                assertEquals(expected.identity(), actual.identity(), receptor + " heavy atom identity at index " + i);
                assertEquals(expected.x(), actual.x(), 1e-3, receptor + " " + expected.identity() + " x");
                assertEquals(expected.y(), actual.y(), 1e-3, receptor + " " + expected.identity() + " y");
                assertEquals(expected.z(), actual.z(), 1e-3, receptor + " " + expected.identity() + " z");
            }
        }
    }

    @Test
    void supportedPipelineOutputsPreserveOpenBabelHeavyAtoms() throws Exception {
        for (String receptor : PIPELINE_SUPPORTED_VERIFIERS) {
            Path pdb = resourcePath("/pipeline/" + receptor + ".pdb");
            Path openBabelPdbqt = resourcePath("/pipeline/" + receptor + ".pdbqt");

            Map<String, Object> config = new HashMap<>();
            config.put(ContextKeys.PLDDT_CUTOFF, 0.0);
            Pipeline pipeline = new PipelineFactory(tempDir.resolve(receptor)).createDockingPipeline(config, pdb);
            pipeline.run();

            String generatedPdbqtPath = pipeline.getContext().require(ContextKeys.RECEPTOR_PDBQT);
            Path generatedPdbqt = Path.of(generatedPdbqtPath);
            Path artifactPdbqt = Path.of("target", "test-artifacts", "pipeline", receptor + "-prepared_receptor.pdbqt");
            Files.createDirectories(artifactPdbqt.getParent());
            Files.copy(generatedPdbqt, artifactPdbqt, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            List<HeavyAtom> expected = pdbqtProteinHeavyAtoms(openBabelPdbqt);
            List<HeavyAtom> actual = pdbqtProteinHeavyAtoms(generatedPdbqt);
            assertEquals(expected.size(), actual.size(),
                    receptor + " generated PDBQT copied to " + artifactPdbqt.toAbsolutePath());
            for (int i = 0; i < expected.size(); i++) {
                HeavyAtom expectedAtom = expected.get(i);
                HeavyAtom actualAtom = actual.get(i);
                assertEquals(expectedAtom.identity(), actualAtom.identity(),
                        receptor + " heavy atom identity at index " + i);
                assertEquals(expectedAtom.x(), actualAtom.x(), 1e-3, receptor + " " + expectedAtom.identity() + " x");
                assertEquals(expectedAtom.y(), actualAtom.y(), 1e-3, receptor + " " + expectedAtom.identity() + " y");
                assertEquals(expectedAtom.z(), actualAtom.z(), 1e-3, receptor + " " + expectedAtom.identity() + " z");
            }
        }
    }

    private Path resourcePath(String resourceName) throws Exception {
        URL resource = getClass().getResource(resourceName);
        assertNotNull(resource, resourceName + " resource missing");
        return Path.of(resource.toURI());
    }

    private List<HeavyAtom> pdbHeavyAtoms(Path pdb) throws Exception {
        return Files.readAllLines(pdb).stream()
                .filter(line -> line.startsWith("ATOM") || line.startsWith("HETATM"))
                .map(this::parsePdbAtom)
                .filter(atom -> !atom.element().equals("H"))
                .toList();
    }

    private List<HeavyAtom> pdbqtHeavyAtoms(Path pdbqt) throws Exception {
        return Files.readAllLines(pdbqt).stream()
                .filter(line -> line.startsWith("ATOM") || line.startsWith("HETATM"))
                .map(this::parsePdbqtAtom)
                .filter(atom -> !atom.element().equals("H"))
                .toList();
    }

    private List<HeavyAtom> pdbqtProteinHeavyAtoms(Path pdbqt) throws Exception {
        return Files.readAllLines(pdbqt).stream()
                .filter(line -> line.startsWith("ATOM"))
                .map(this::parsePdbqtAtom)
                .filter(atom -> !isSolvent(atom.residueName()))
                .filter(atom -> !atom.element().equals("H"))
                .toList();
    }

    private boolean isSolvent(String residueName) {
        return residueName.equals("HOH") || residueName.equals("WAT") || residueName.equals("SOL");
    }

    private HeavyAtom parsePdbAtom(String line) {
        return new HeavyAtom(
                line.substring(12, 16).trim(),
                line.substring(17, 20).trim(),
                line.substring(21, 22).trim(),
                Integer.parseInt(line.substring(22, 26).trim()),
                Double.parseDouble(line.substring(30, 38).trim()),
                Double.parseDouble(line.substring(38, 46).trim()),
                Double.parseDouble(line.substring(46, 54).trim()),
                line.length() >= 78 ? line.substring(76, 78).trim() : "");
    }

    private HeavyAtom parsePdbqtAtom(String line) {
        String[] fields = line.trim().split("\\s+");
        String atomType = fields[fields.length - 1];
        return new HeavyAtom(
                fields[2],
                fields[3],
                fields[4],
                Integer.parseInt(fields[5]),
                Double.parseDouble(fields[6]),
                Double.parseDouble(fields[7]),
                Double.parseDouble(fields[8]),
                atomType.substring(0, 1));
    }

    private record HeavyAtom(String atomName, String residueName, String chain, int residueNumber,
                             double x, double y, double z, String element) {
        String identity() {
            return atomName + " " + residueName + " " + chain + ":" + residueNumber;
        }
    }
}
