package totah.lab.hephaestus.receptor;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.hephaestus.client.HephaestusClients;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeReceptorRegressionTest {

    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest
    @ValueSource(strings = {"1CRN", "1UBQ"})
    void preparesRealReceptorAndPreservesOpenBabelHeavyAtomFrame(String id) throws Exception {
        Path input = resource("/pipeline/" + id + ".pdb");
        Path reference = resource("/pipeline/" + id + ".pdbqt");
        Path output = temporaryDirectory.resolve(id + "-prepared.pdbqt");

        var writeResult = HephaestusClients.createDefault()
                .prepareAndWriteReceptor(input, output, ReceptorPreparationOptions.defaults());

        assertTrue(Files.isRegularFile(writeResult.rigidOutput()));
        Map<AtomKey, Coordinates> expected = heavyAtoms(reference);
        Map<AtomKey, Coordinates> actual = heavyAtoms(output);
        assertFalse(expected.isEmpty());
        var missing = new java.util.LinkedHashSet<>(expected.keySet());
        missing.removeAll(actual.keySet());
        var unexpected = new java.util.LinkedHashSet<>(actual.keySet());
        unexpected.removeAll(expected.keySet());
        assertTrue(missing.isEmpty() && unexpected.isEmpty(),
                "heavy atom mismatch; missing=" + missing + ", unexpected=" + unexpected);
        expected.forEach((atom, coordinates) ->
                assertEquals(coordinates, actual.get(atom), "coordinate drift for " + atom));
    }

    private Path resource(String name) throws Exception {
        Path copy = temporaryDirectory.resolve(Path.of(name).getFileName().toString());
        try (var input = getClass().getResourceAsStream(name)) {
            if (input == null) {
                throw new AssertionError("missing test resource " + name);
            }
            Files.copy(input, copy, StandardCopyOption.REPLACE_EXISTING);
        }
        return copy;
    }

    private Map<AtomKey, Coordinates> heavyAtoms(Path path) throws Exception {
        Map<AtomKey, Coordinates> result = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path);
        for (String line : lines) {
            if (!line.startsWith("ATOM") && !line.startsWith("HETATM")) {
                continue;
            }
            String atomName = line.substring(12, 16).trim();
            String residueName = line.substring(17, 20).trim();
            if (atomName.startsWith("H") || "HOH".equals(residueName)
                    || "WAT".equals(residueName)) {
                continue;
            }
            AtomKey key = new AtomKey(
                    line.substring(21, 22).trim(),
                    Integer.parseInt(line.substring(22, 26).trim()),
                    line.substring(26, 27).trim(),
                    atomName);
            Coordinates coordinates = new Coordinates(
                    Double.parseDouble(line.substring(30, 38).trim()),
                    Double.parseDouble(line.substring(38, 46).trim()),
                    Double.parseDouble(line.substring(46, 54).trim()));
            if (result.putIfAbsent(key, coordinates) != null) {
                throw new AssertionError("duplicate heavy atom " + key + " in " + path);
            }
        }
        return result;
    }

    private record AtomKey(String chain, int residueNumber, String insertionCode, String atomName) {
    }

    private record Coordinates(double x, double y, double z) {
    }
}
