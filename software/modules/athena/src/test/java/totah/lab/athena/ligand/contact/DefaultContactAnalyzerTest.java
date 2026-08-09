package totah.lab.athena.ligand.contact;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultContactAnalyzerTest {

    private final DefaultContactAnalyzer analyzer =
            new DefaultContactAnalyzer();

    @Test
    void classifiesDirectAndShellContactsByDistance() {
        Structure receptor = receptor(
                residue("ALA", 10, atom(1, "CA", 0, 0, 0)),
                residue("PHE", 20, atom(2, "CA", 6, 0, 0)),
                residue("GLY", 30, atom(3, "CA", 50, 0, 0))
        );
        Ligand ligand = ligand(atom(100, "C1", 0, 0, 3));

        List<LigandContact> contacts =
                analyzer.analyze(receptor, ligand);

        assertEquals(2, contacts.size());
        assertEquals(10, contacts.get(0).residue().residueNumber());
        assertEquals(ContactType.DIRECT, contacts.get(0).type());
        assertEquals(3.0, contacts.get(0).distance(), 1.0e-9);
        assertEquals(20, contacts.get(1).residue().residueNumber());
        assertEquals(ContactType.SHELL, contacts.get(1).type());
    }

    @Test
    void skipsHydrogensOnBothSides() {
        Structure receptor = receptor(
                residue("ALA", 10, atom(1, "H", 0, 0, 0, Element.H))
        );
        Ligand ligand = ligand(atom(100, "H1", 0, 0, 1, Element.H));

        assertEquals(0, analyzer.analyze(receptor, ligand).size());
    }

    @Test
    void rejectsInvalidCutoffs() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultContactAnalyzer(0.0, 8.0));
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultContactAnalyzer(9.0, 8.0));
    }

    private static Structure receptor(Residue... residues) {
        return new Structure(List.of(new Chain("A", List.of(residues))));
    }

    private static Residue residue(String name, int number, Atom... atoms) {
        return new Residue(name, number, List.of(atoms));
    }

    private static Ligand ligand(Atom... atoms) {
        Residue residue = new Residue("LIG", 1, List.of(atoms));
        Structure structure = new Structure(
                List.of(new Chain("L", List.of(residue))));
        return new Ligand("L", "L", null, null, null, null, structure);
    }

    private static Atom atom(int serial, String name,
            double x, double y, double z) {
        return atom(serial, name, x, y, z, Element.C);
    }

    private static Atom atom(int serial, String name,
            double x, double y, double z, Element element) {
        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(element)
                .build();
    }
}
