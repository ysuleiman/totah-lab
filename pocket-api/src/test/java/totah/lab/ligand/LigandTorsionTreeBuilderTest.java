package totah.lab.ligand;

import org.junit.jupiter.api.Test;
import totah.lab.chemistry.AtomChemicalProperties;
import totah.lab.chemistry.BondOrder;
import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;
import totah.lab.docking.torsion.TorsionBranch;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandTorsionTreeBuilderTest {

    private final LigandTorsionTreeBuilder builder = new LigandTorsionTreeBuilder();

    @Test
    void buildsStableNestedBranchesAndCoversEveryAtomOnce() {
        MolecularGraph chain = graph(
                List.of("C", "C", "C", "C", "C"),
                List.of(single(0, 1), single(1, 2), single(2, 3), single(3, 4)));

        LigandTorsionTreeResult result = builder.build(chain);

        assertEquals(List.of(0, 1), result.rootFragmentAtoms());
        assertEquals(List.of(0, 1), result.tree().getRootAtoms());
        assertEquals(2, result.torsionalDegreesOfFreedom());
        assertEquals(2, result.tree().flattenBranches().size());
        TorsionBranch first = result.tree().getRootBranches().getFirst();
        assertEquals(1, first.getParentIdx());
        assertEquals(2, first.getChildIdx());
        assertEquals(List.of(2), first.getMovingAtoms());
        TorsionBranch second = first.getChildren().getFirst();
        assertEquals(2, second.getParentIdx());
        assertEquals(3, second.getChildIdx());
        assertEquals(List.of(3, 4), second.getMovingAtoms());
        for (int index = 0; index < chain.atoms().size(); index++) {
            assertTrue(result.tree().containsAtom(index));
        }
    }

    @Test
    void keepsRingAsOneRigidRoot() {
        MolecularGraph ring = graph(
                List.of("C", "C", "C"),
                List.of(single(0, 1), single(1, 2), single(2, 0)));

        LigandTorsionTreeResult result = builder.build(ring);

        assertEquals(List.of(0, 1, 2), result.tree().getRootAtoms());
        assertTrue(result.tree().getRootBranches().isEmpty());
        assertEquals(0, result.torsionalDegreesOfFreedom());
    }

    @Test
    void rigidOverrideMergesFragmentsAndChangesTorsdof() {
        MolecularGraph chain = graph(
                List.of("C", "C", "C", "C", "C"),
                List.of(single(0, 1), single(1, 2), single(2, 3), single(3, 4)));

        LigandTorsionTreeResult result = builder.build(chain, Set.of(1));

        assertEquals(1, result.torsionalDegreesOfFreedom());
        assertEquals(1, result.tree().flattenBranches().size());
        assertEquals(List.of(0, 1, 2), result.rootFragmentAtoms());
    }

    @Test
    void rejectsDisconnectedLigandGraph() {
        MolecularGraph disconnected = graph(
                List.of("C", "C"),
                List.of());

        assertThrows(IllegalArgumentException.class,
                () -> builder.build(disconnected));
    }

    private MolecularGraph graph(List<String> elements, List<ChemicalBond> bonds) {
        List<Atom> atoms = java.util.stream.IntStream.range(0, elements.size())
                .mapToObj(index -> Atom.builder()
                        .name(elements.get(index) + index)
                        .element(Element.builder().symbol(elements.get(index)).build())
                        .position(new Point3D(index, 0.0, 0.0))
                        .charge(0.0)
                        .build())
                .toList();
        List<AtomChemicalProperties> properties =
                java.util.stream.IntStream.range(0, elements.size())
                        .mapToObj(index -> new AtomChemicalProperties(
                                atoms.get(index).getName(), 0, false, false, index))
                        .toList();
        return new MolecularGraph(atoms, bonds, properties);
    }

    private ChemicalBond single(int first, int second) {
        return new ChemicalBond(first, second, BondOrder.SINGLE, false);
    }
}
