package totah.lab.structure.io.pdbqt;

import org.junit.jupiter.api.Test;
import totah.lab.docking.torsion.TorsionBranch;
import totah.lab.docking.torsion.TorsionTree;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandPDBQTWriterTest {

    @Test
    void writesLigandTreeWithStableSerialsAndTorsdof() {
        Residue ligand = residue();
        TorsionTree tree = new TorsionTree();
        tree.addRootAtom(0);
        tree.addRootAtom(1);
        tree.addRootBranch(new TorsionBranch(1, 2, List.of(2, 3)));
        StringWriter output = new StringWriter();

        new LigandPDBQTWriter(output).write(ligand, tree, 1);

        List<String> lines = output.toString().lines().toList();
        assertEquals("ROOT", lines.getFirst());
        assertEquals("ENDROOT", lines.get(3));
        assertEquals("BRANCH 2 3", lines.get(4));
        assertEquals("ENDBRANCH 2 3", lines.get(7));
        assertEquals("TORSDOF 1", lines.getLast());
        assertFalse(output.toString().contains("BEGIN_RES"));
        assertEquals(4, lines.stream().filter(line -> line.startsWith("ATOM")).count());
    }

    @Test
    void rejectsIncompleteTreeAndTorsdofMismatch() {
        Residue ligand = residue();
        TorsionTree incomplete = new TorsionTree();
        incomplete.addRootAtom(0);

        assertThrows(IllegalArgumentException.class,
                () -> new LigandPDBQTWriter(new StringWriter())
                        .write(ligand, incomplete, 0));

        TorsionTree complete = new TorsionTree();
        ligand.getAtoms().forEach(atom ->
                complete.addRootAtom(ligand.getAtoms().indexOf(atom)));
        assertThrows(IllegalArgumentException.class,
                () -> new LigandPDBQTWriter(new StringWriter())
                        .write(ligand, complete, 1));
    }

    @Test
    void rejectsIllegalTypeAndNonFiniteCoordinates() {
        Residue illegal = residue().toBuilder().atoms(List.of(
                atom("X", "C", "XX", 0.0),
                atom("C2", "C", "C", 1.0),
                atom("C3", "C", "C", 2.0),
                atom("C4", "C", "C", 3.0))).build();
        TorsionTree tree = new TorsionTree();
        for (int index = 0; index < 4; index++) tree.addRootAtom(index);

        assertThrows(IllegalArgumentException.class,
                () -> new LigandPDBQTWriter(new StringWriter()).write(illegal, tree, 0));
        assertTrue(tree.containsAtom(3));
    }

    private Residue residue() {
        return Residue.builder()
                .name("LIG").chain("A").number(1).insertionCode(' ')
                .atoms(List.of(
                        atom("C1", "C", "C", 0.0),
                        atom("C2", "C", "C", 1.0),
                        atom("N1", "N", "NA", 2.0),
                        atom("H1", "H", "HD", 3.0)))
                .build();
    }

    private Atom atom(String name, String element, String type, double x) {
        return Atom.builder()
                .name(name)
                .element(Element.builder().symbol(element).build())
                .position(new Point3D(x, 0.0, 0.0))
                .charge(0.0).occupancy(1.0).bFactor(0.0)
                .autoDockType(type)
                .build();
    }
}
