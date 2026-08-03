package totah.lab.gaia.structure;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;

import static org.junit.jupiter.api.Assertions.*;

class AtomTest {

    @Test
    void carbonAtomShouldBeHeavy() {
        assertTrue(atom(Element.C).isHeavyAtom());
    }

    @Test
    void hydrogenAtomShouldNotBeHeavy() {
        assertFalse(atom(Element.H).isHeavyAtom());
    }

    @Test
    void nullElementAtomShouldNotBeHeavy() {
        assertFalse(atom(null).isHeavyAtom());
    }

    @Test
    void nullElementAtomShouldNotBeHydrogen() {
        assertFalse(atom(null).isHydrogen());
    }

    private static Atom atom(Element element) {
        return Atom.builder()
                .pdbSerial(1)
                .name("X1")
                .position(new Point3D(0.0, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(element)
                .build();
    }
}
