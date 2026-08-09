package totah.lab.hermes.file.pdbqt.writer;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.pdbqt.*;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdbqtLigandWriterTest {
    @Test
    void writesDeterministicBalancedLigandTree() throws Exception {
        PdbqtLigand input = new PdbqtLigand(
                List.of(atom(0, "C1"), atom(1, "C2"), atom(2, "O1")),
                "root",
                List.of(
                        new PdbqtLigandFragment("root", List.of(0, 1), null, null, null),
                        new PdbqtLigandFragment("side", List.of(2), "root", 1, 2)));
        StringWriter output = new StringWriter();

        new PdbqtLigandWriter().write(input, output);

        String text = output.toString();
        assertTrue(text.startsWith("ROOT" + System.lineSeparator()));
        assertTrue(text.contains("BRANCH 2 3"));
        assertTrue(text.contains("ENDBRANCH 2 3"));
        assertTrue(text.endsWith("TORSDOF 1" + System.lineSeparator()));
        assertEquals(3, text.lines().filter(line -> line.startsWith("ATOM")).count());
    }

    private PdbqtAtomReference atom(int index, String name) {
        return new PdbqtAtomReference(index, index + 1, name, "LIG", "L", 1, null,
                new Point3D(index, 0, 0), 1, 0, 0, name.startsWith("O") ? "OA" : "C");
    }
}
