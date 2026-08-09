package totah.lab.hermes.file.sdf.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.structure.Atom;
import totah.lab.hermes.file.sdf.SdfLigand;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SdfLigandWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsV2000ExampleWithoutChangingMolecularData() throws Exception {
        Path example = Path.of(getClass().getResource("/sdf/example.sdf").toURI());
        SdfLigandReader reader = new SdfLigandReader();
        SdfLigand expected = reader.readModel(example);
        Path output = temporaryDirectory.resolve("round-trip.sdf");

        new SdfLigandWriter().write(output, expected);
        SdfLigand actual = reader.readModel(output);

        assertEquals(expected.title(), actual.title());
        assertEquals(expected.bonds(), actual.bonds());
        assertEquals(expected.formalCharges(), actual.formalCharges());
        assertAtomsEqual(atoms(expected), atoms(actual));
    }

    private void assertAtomsEqual(List<Atom> expected, List<Atom> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index).getElement(), actual.get(index).getElement());
            assertEquals(expected.get(index).getPosition(), actual.get(index).getPosition());
        }
    }

    private List<Atom> atoms(SdfLigand model) {
        return model.ligand().structure().getChains().getFirst()
                .residues().getFirst().getAtoms();
    }
}
