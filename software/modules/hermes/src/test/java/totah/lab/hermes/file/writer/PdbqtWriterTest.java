package totah.lab.hermes.file.writer;

import totah.lab.hermes.file.pdbqt.PdbqtWriteOptions;
import totah.lab.hermes.file.pdbqt.PdbqtWriteResult;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.writer.PdbqtWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdbqtWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void canonicalModelPathMatchesRigidStructureWriter() throws Exception {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(new Residue(
                        "ALA", 1, List.of(atom("CA", Element.C, "C", -0.1))))),
                new Chain("B", List.of(new Residue(
                        "GLY", 2, List.of(atom("H", Element.H, "HD", 0.1)))))));
        PdbqtWriteOptions options = new PdbqtWriteOptions(true, true);
        Path direct = temporaryDirectory.resolve("direct.pdbqt");
        Path canonical = temporaryDirectory.resolve("canonical.pdbqt");

        PdbqtWriter writer = new PdbqtWriter();
        writer.write(structure, direct, options);
        writer.write(PdbqtGaiaMapper.fromStructure(structure), canonical, options);

        assertEquals(Files.readString(direct), Files.readString(canonical));
    }

    @Test
    void writesMultipleChainsInOriginalOrderWithExplicitIdentity()
            throws Exception {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(new Residue(
                        "ALA", 1, List.of(atom("CA", Element.C, "C", -0.1))))),
                new Chain("B", List.of(new Residue(
                        "GLY", 2, List.of(atom("H", Element.H, "HD", 0.1)))))));
        Path output = temporaryDirectory.resolve("receptor.pdbqt");

        PdbqtWriteResult result = new PdbqtWriter().write(
                structure, output, new PdbqtWriteOptions(true, true));

        List<String> lines = Files.readAllLines(output);
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).startsWith("ATOM      1  CA  ALA A   1"));
        assertEquals("TER", lines.get(1));
        assertTrue(lines.get(2).startsWith("ATOM      2  H   GLY B   2"));
        assertEquals("END", lines.get(3));
        assertTrue(lines.get(0).endsWith("-0.1000  C"));
        assertTrue(lines.get(2).endsWith("+0.1000 HD"));
        assertEquals(output.toAbsolutePath().normalize(), result.rigidOutput());
        assertEquals(2, result.rigidAtomCount());
        assertEquals(0, result.flexibleAtomCount());
    }

    @Test
    void rejectsChargeThatOverflowsItsField() {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(new Residue(
                        "ALA", 1, List.of(atom("CA", Element.C, "C", 12.5)))))));

        assertThrows(IllegalArgumentException.class,
                () -> new PdbqtWriter().write(structure,
                        temporaryDirectory.resolve("charge-overflow.pdbqt"),
                        PdbqtWriteOptions.defaults()));
    }

    @Test
    void rejectsCoordinateThatOverflowsItsField() {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(new Residue(
                        "ALA", 1, List.of(atom("CA", Element.C, "C", 0.1,
                        new Point3D(-1500.0, 0.0, 0.0))))))));

        assertThrows(IllegalArgumentException.class,
                () -> new PdbqtWriter().write(structure,
                        temporaryDirectory.resolve("coordinate-overflow.pdbqt"),
                        PdbqtWriteOptions.defaults()));
    }

    private Atom atom(
            String name, Element element, String type, double charge) {
        return atom(name, element, type, charge, new Point3D(1.0, 2.0, 3.0));
    }

    private Atom atom(
            String name, Element element, String type, double charge,
            Point3D position) {
        return Atom.builder()
                .name(name)
                .element(element)
                .autoDockType(type)
                .amberType(name)
                .charge(charge)
                .occupancy(1.0)
                .bFactor(0.0)
                .position(position)
                .build();
    }
}
