package totah.lab.gaia.structure;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class StructureIdentityAndBondTest {

    @Test
    void resolvesResiduesByCanonicalIdentity() {
        Residue residue = residue(5, null, atom("N"), atom("CA"));
        Structure structure = new Structure(
                List.of(new Chain("A", List.of(residue))));

        ResidueId identity = new ResidueId("A", 5, ' ');

        assertSame(residue, structure.findResidue(identity).orElseThrow());
        assertSame(residue, structure.residue(identity));
        assertTrue(structure.contains(identity));
        assertFalse(structure.contains(new ResidueId("B", 5, null)));
        assertThrows(
                NoSuchElementException.class,
                () -> structure.residue(new ResidueId("B", 5, null)));
    }

    @Test
    void ownsValidatedImmutableConnectivity() {
        Residue residue = residue(1, null, atom("C1"), atom("O1"));
        Bond bond = new Bond(0, 1, BondOrder.DOUBLE, false);
        Structure structure = new Structure(
                List.of(new Chain("A", List.of(residue))),
                List.of(bond));

        assertEquals(List.of(bond), structure.getBonds());
        assertThrows(
                UnsupportedOperationException.class,
                () -> structure.getBonds().add(bond));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Structure(
                        structure.getChains(),
                        List.of(new Bond(0, 2, BondOrder.SINGLE, false))));
    }

    @Test
    void validatesIdentityValues() {
        assertEquals(
                new ResidueId("A", 1, null),
                new ResidueId(" A ", 1, ' '));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueId(" ", 1, null));
        assertEquals("model-1", new StructureId(" model-1 ").value());
    }

    private static Residue residue(
            int number,
            Character insertionCode,
            Atom... atoms) {

        return new Residue(
                "GLY",
                number,
                insertionCode,
                List.of(atoms));
    }

    private static Atom atom(String name) {
        return Atom.builder()
                .pdbSerial(1)
                .name(name)
                .position(new Point3D(0.0, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.C)
                .build();
    }
}
