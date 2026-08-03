package totah.lab.hephaestus.mutation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityMetadata;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationOperationTest {

    @Test
    void rebuiltStructureKeepsTemplateBondOrders() {
        MutationOperationResult result = new MutationOperation().apply(
                structure(),
                new ResidueMutation(new ResidueId("A", 1, null), "ALA", "PHE"),
                MutationContext.defaults());

        assertTrue(result.appliedMutation().isPresent());

        List<Bond> bonds = result.structure().bonds();
        assertEquals(BondOrder.AROMATIC, orderOf(bonds, "CG", "CD1"));
        assertEquals(BondOrder.AROMATIC, orderOf(bonds, "CE1", "CZ"));
        assertEquals(BondOrder.SINGLE, orderOf(bonds, "CA", "CB"));
        assertEquals(BondOrder.SINGLE, orderOf(bonds, "CB", "CG"));
    }

    @Test
    void asparagineCarbonylKeepsDoubleBondOrder() {
        MutationOperationResult result = new MutationOperation().apply(
                structure(),
                new ResidueMutation(new ResidueId("A", 1, null), "ALA", "ASN"),
                MutationContext.defaults());

        assertTrue(result.appliedMutation().isPresent());
        assertEquals(BondOrder.DOUBLE,
                orderOf(result.structure().bonds(), "CG", "OD1"));
    }

    @Test
    void newSideChainSerialsDoNotCollideWithOtherResidues() {
        MutationOperationResult result = new MutationOperation().apply(
                structure(),
                new ResidueMutation(new ResidueId("A", 1, null), "ALA", "PHE"),
                MutationContext.defaults());

        assertTrue(result.appliedMutation().isPresent());

        Set<Integer> serials = new HashSet<>();
        int maxOriginalSerial = 9;
        int newAtoms = 0;
        for (Chain chain : result.structure().getChains()) {
            for (Residue residue : chain.residues()) {
                for (Atom atom : residue.getAtoms()) {
                    assertTrue(serials.add(atom.getPdbSerial()),
                            "duplicate pdb serial " + atom.getPdbSerial());
                    if (residue.getNumber() == 1
                            && atom.getPdbSerial() > maxOriginalSerial) {
                        newAtoms++;
                    }
                }
            }
        }
        // PHE contributes seven new side-chain atoms, all numbered above
        // the structure-wide maximum serial of the input structure.
        assertEquals(7, newAtoms);
    }

    private BondOrder orderOf(List<Bond> bonds, String first, String second) {
        return bonds.stream()
                .filter(bond -> matches(bond, first, second))
                .map(Bond::order)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no bond " + first + "-" + second));
    }

    private boolean matches(Bond bond, String first, String second) {
        return (bond.atom1().atomName().equals(first)
                && bond.atom2().atomName().equals(second))
                || (bond.atom1().atomName().equals(second)
                && bond.atom2().atomName().equals(first));
    }

    private Structure structure() {
        Residue alanine = new Residue("ALA", 1, List.of(
                atom("N", Element.N, 1, 0.0, 0.0, 0.0),
                atom("CA", Element.C, 2, 1.45, 0.0, 0.0),
                atom("C", Element.C, 3, 2.05, 1.35, 0.0),
                atom("O", Element.O, 4, 1.45, 2.40, 0.0),
                atom("CB", Element.C, 5, 2.10, -1.20, 0.80)));
        Residue glycine = new Residue("GLY", 2, List.of(
                atom("N", Element.N, 6, 3.50, 1.80, 0.0),
                atom("CA", Element.C, 7, 4.95, 1.80, 0.0),
                atom("C", Element.C, 8, 5.55, 3.15, 0.0),
                atom("O", Element.O, 9, 4.95, 4.20, 0.0)));
        return new Structure(
                List.of(new Chain("A", List.of(alanine, glycine))),
                List.of(),
                new ConnectivityMetadata(
                        ConnectivityProvenance.EXPLICIT, List.of()));
    }

    private Atom atom(
            String name,
            Element element,
            int serial,
            double x,
            double y,
            double z) {

        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .element(element)
                .position(new Point3D(x, y, z))
                .occupancy(1.0)
                .build();
    }
}
