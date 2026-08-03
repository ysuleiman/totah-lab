package totah.lab.gaia.structure;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResidueTest {

    @Test
    void heavyAtomCountShouldMatchAtomIsHeavyAtom() {
        List<Atom> atoms = List.of(
                atom("CA", Element.C),
                atom("N", Element.N),
                atom("H", Element.H),
                atom("X", null));

        Residue residue = new Residue("GLY", 1, atoms);

        assertEquals(2, residue.getHeavyAtomCount());
        assertEquals(
                residue.getHeavyAtomCount(),
                atoms.stream()
                        .filter(Atom::isHeavyAtom)
                        .count());
    }

    @Test
    void heavyAtomCountShouldExcludeNullElementAtoms() {
        Residue residue = new Residue(
                "GLY",
                1,
                List.of(atom("X1", null), atom("X2", null)));

        assertEquals(0, residue.getHeavyAtomCount());
    }

    private static Atom atom(String name, Element element) {
        return Atom.builder()
                .pdbSerial(1)
                .name(name)
                .position(new Point3D(0.0, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(element)
                .build();
    }
}
