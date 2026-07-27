package totah.lab.ligand;

import org.junit.jupiter.api.Test;
import totah.lab.chemistry.AtomChemicalProperties;
import totah.lab.chemistry.BondOrder;
import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RotatableBondClassifierTest {

    private final RotatableBondClassifier classifier = new RotatableBondClassifier();

    @Test
    void findsOnlyTheCentralBondOfAHeavyAtomChain() {
        MolecularGraph graph = graph(
                List.of("C", "C", "C", "C"),
                List.of(single(0, 1), single(1, 2), single(2, 3)));

        LigandRotatableBondReport report = classifier.classify(graph);

        assertEquals(List.of(
                        RotatableBondClassification.TERMINAL,
                        RotatableBondClassification.ROTATABLE,
                        RotatableBondClassification.TERMINAL),
                report.bondClassifications());
        assertEquals(List.of(1), report.rotatableBondIndices());
    }

    @Test
    void marksEveryEdgeOfACycleAsRing() {
        MolecularGraph graph = graph(
                List.of("C", "C", "C"),
                List.of(single(0, 1), single(1, 2), single(2, 0)));

        assertEquals(List.of(
                        RotatableBondClassification.RING,
                        RotatableBondClassification.RING,
                        RotatableBondClassification.RING),
                classifier.classify(graph).bondClassifications());
    }

    @Test
    void excludesAmideHydrogenDoubleMetalAndExplicitlyRigidBonds() {
        MolecularGraph amide = graph(
                List.of("N", "C", "O", "C", "H", "Zn"),
                List.of(
                        single(0, 1),
                        new ChemicalBond(1, 2, BondOrder.DOUBLE, false),
                        single(1, 3),
                        single(0, 4),
                        single(3, 5)));

        assertEquals(List.of(
                        RotatableBondClassification.RESONANCE_RESTRICTED,
                        RotatableBondClassification.NON_SINGLE,
                        RotatableBondClassification.EXPLICITLY_RIGID,
                        RotatableBondClassification.HYDROGEN,
                        RotatableBondClassification.METAL_COORDINATION),
                classifier.classify(amide, Set.of(2)).bondClassifications());
    }

    @Test
    void rejectsOutOfRangeRigidOverride() {
        MolecularGraph graph = graph(List.of("C", "C"), List.of(single(0, 1)));

        assertThrows(IllegalArgumentException.class,
                () -> classifier.classify(graph, Set.of(1)));
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
