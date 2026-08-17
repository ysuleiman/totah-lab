package totah.lab.gaia.graph;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.classification.ResidueCategory;
import totah.lab.gaia.geometry.AtomSelection;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidueGraphTest {

    @Test
    void includesEveryResidueInStructureOrder() {
        Residue a10 = residue("ALA", 10, null, atom("CA", 0.0));
        Residue a10a = residue("GLY", 10, 'A', atom("CA", 3.0));
        Residue b5 = residue("SER", 5, null, atom("CA", 9.0));
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(a10, a10a)),
                new Chain("B", List.of(b5))));

        ResidueGraph graph = ResidueGraph.from(structure);

        assertEquals(
                List.of(id("A", 10, null), id("A", 10, 'A'),
                        id("B", 5, null)),
                graph.residueIds());
        assertEquals(List.of(a10, a10a, b5), graph.residues());
        assertEquals(structure, graph.structure());
        assertSame(structure, graph.toStructure());
    }

    @Test
    void nodesExposeIntrinsicChemistryWithoutCopyingResidues() {
        Residue leucine = residue(
                "LEU", 1, null, atom("CA", 0.0));
        Residue aspartate = residue(
                "ASP", 2, null, atom("CA", 2.0));
        Residue modified = residue(
                "MSE", 3, null, atom("CA", 4.0));
        ResidueGraph graph = ResidueGraph.from(new Structure(List.of(
                new Chain("A", List.of(leucine, aspartate, modified)))));

        ResidueNode leucineNode = graph.node(id("A", 1, null));
        ResidueNode modifiedNode = graph.node(id("A", 3, null));

        assertSame(leucine, leucineNode.residue());
        assertTrue(leucineNode.chemistry().isAvailable());
        assertTrue(leucineNode.chemistry().contains(
                ResidueCategory.HYDROPHOBIC));
        assertEquals(
                ResidueChemistryStatus.NOT_AVAILABLE,
                modifiedNode.chemistry().status());
        assertTrue(modifiedNode.chemistry().categories().isEmpty());
        assertEquals(
                List.of(id("A", 1, null)),
                graph.viewByCategory(ResidueCategory.HYDROPHOBIC)
                        .residueIds());
        assertEquals(
                List.of(id("A", 2, null)),
                graph.viewByCategory(
                                ResidueCategory.NEGATIVELY_CHARGED)
                        .residueIds());
        assertTrue(graph.findNode(id("A", 99, null)).isEmpty());
    }

    @Test
    void usesRecognizedExplicitPolymerBondsOnly() {
        Residue first = residue(
                "ALA", 10, null,
                atom("C", 0.0), atom("SG", 1.0));
        Residue second = residue(
                "CYS", 50, null,
                atom("N", 1.3), atom("SG", 2.0));
        Residue third = residue(
                "CYS", 70, null,
                atom("SG", 2.2));
        Structure structure = new Structure(
                List.of(new Chain("A", List.of(first, second, third))),
                List.of(
                        bond("A", 10, null, "C", 50, null, "N"),
                        bond("A", 50, null, "SG", 70, null, "SG")));

        List<SequenceEdge> edges = ResidueGraph.from(structure)
                .sequenceEdges();

        assertEquals(1, edges.size());
        assertEquals(id("A", 10, null), edges.getFirst().first());
        assertEquals(id("A", 50, null), edges.getFirst().second());
        assertEquals(
                SequenceEdgeProvenance.EXPLICIT_BOND,
                edges.getFirst().provenance());
    }

    @Test
    void chainOrderInferenceHandlesGapsInsertionsAndMultipleChains() {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(
                        residue("ALA", 10, null, atom("CA", 0.0)),
                        residue("GLY", 10, 'A', atom("CA", 2.0)),
                        residue("SER", 40, null, atom("CA", 4.0)))),
                new Chain("B", List.of(
                        residue("ALA", 1, null, atom("CA", 6.0)),
                        residue("GLY", 2, null, atom("CA", 8.0))))));

        List<SequenceEdge> edges = ResidueGraph.builder(structure)
                .sequencePolicy(SequencePolicy.EXPLICIT_OR_CHAIN_ORDER)
                .build()
                .sequenceEdges();

        assertEquals(3, edges.size());
        assertTrue(edges.stream().allMatch(edge ->
                edge.provenance()
                        == SequenceEdgeProvenance.CHAIN_ORDER_INFERRED));
        assertFalse(edges.stream().anyMatch(edge ->
                !edge.first().chainId().equals(edge.second().chainId())));
        assertTrue(edges.contains(new SequenceEdge(
                id("A", 10, 'A'),
                id("A", 40, null),
                SequenceEdgeProvenance.CHAIN_ORDER_INFERRED)));
    }

    @Test
    void distanceQueriesRetainNeutralMeasurementsAndMissingCa() {
        Residue first = residue(
                "ALA", 1, null,
                atom("CA", Element.C, 0.0),
                atom("H", Element.H, 2.9));
        Residue second = residue(
                "GLY", 2, null,
                atom("CB", Element.C, 3.0));
        Residue far = residue(
                "SER", 3, null,
                atom("CA", Element.C, 20.0));
        ResidueGraph graph = ResidueGraph.from(new Structure(List.of(
                new Chain("A", List.of(first, second, far)))));

        ResidueDistance heavy = graph.withinDistance(
                4.0, AtomSelection.HEAVY).getFirst();
        ResidueDistance all = graph.withinDistance(
                4.0, AtomSelection.ALL).getFirst();

        assertEquals(3.0, heavy.minimumDistance());
        assertEquals(0.1, all.minimumDistance(), 1.0e-12);
        assertTrue(heavy.alphaCarbonDistance().isEmpty());
        assertEquals(3.0, heavy.centroidDistance().orElseThrow());
        assertEquals(1, graph.withinDistance(
                4.0, AtomSelection.HEAVY).size());
    }

    @Test
    void proximitiesPreserveEveryEstablishingAtomPair() {
        Residue first = residue(
                "ALA", 1, null,
                atom("CA", 0.0), atom("CB", 0.5));
        Residue second = residue(
                "GLY", 2, null,
                atom("CA", 2.0), atom("N", 2.5));
        ResidueGraph graph = ResidueGraph.from(new Structure(List.of(
                new Chain("A", List.of(first, second)))));

        ResidueAtomProximity proximity = graph.atomProximities(
                AtomDistanceCriterion.heavyAtomsWithin(3.0)).getFirst();

        assertEquals(id("A", 1, null), proximity.first());
        assertEquals(id("A", 2, null), proximity.second());
        assertEquals(4, proximity.atomPairs().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> proximity.atomPairs().clear());
    }

    @Test
    void samePairCanHaveSequenceDistanceAndContactRelationships() {
        Residue first = residue("ALA", 1, null, atom("C", 0.0));
        Residue second = residue("GLY", 2, null, atom("N", 1.3));
        Structure structure = new Structure(
                List.of(new Chain("A", List.of(first, second))),
                List.of(bond("A", 1, null, "C", 2, null, "N")));
        ResidueGraph graph = ResidueGraph.from(structure);

        assertEquals(1, graph.sequenceEdges().size());
        assertEquals(1, graph.withinDistance(
                2.0, AtomSelection.HEAVY).size());
        assertEquals(1, graph.atomProximities(
                AtomDistanceCriterion.heavyAtomsWithin(2.0)).size());
    }

    @Test
    void createsImmutableInducedViewsInParentOrder() {
        Residue first = residue("ALA", 1, null, atom("C", 0.0));
        Residue second = residue("GLY", 2, null, atom("N", 1.3));
        Residue third = residue("SER", 3, null, atom("CA", 2.5));
        Structure structure = new Structure(
                List.of(new Chain("A", List.of(first, second, third))),
                List.of(bond("A", 1, null, "C", 2, null, "N")));
        ResidueGraph graph = ResidueGraph.builder(structure)
                .sequencePolicy(SequencePolicy.EXPLICIT_OR_CHAIN_ORDER)
                .build();
        ResidueGraphView view = graph.view(List.of(
                id("A", 3, null), id("A", 2, null)));

        assertEquals(
                List.of(id("A", 2, null), id("A", 3, null)),
                view.residueIds());
        assertEquals(1, view.sequenceEdges().size());
        assertEquals(1, view.withinDistance(
                2.0, AtomSelection.HEAVY).size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> graph.residues().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> graph.view(List.of(id("A", 99, null))));
        assertThrows(
                IllegalArgumentException.class,
                () -> view.view(List.of(id("A", 1, null))));

        Structure materialized = view.toStructure();
        assertEquals(List.of(second, third),
                materialized.getChains().getFirst().residues());
        assertTrue(materialized.bonds().isEmpty());
    }

    @Test
    void constructionAndQueriesAreDeterministic() {
        Structure structure = new Structure(List.of(new Chain("B", List.of(
                residue("SER", 3, null, atom("CA", 4.0)),
                residue("ALA", 1, null, atom("CA", 0.0)),
                residue("GLY", 2, null, atom("CA", 2.0))))));

        ResidueGraph first = ResidueGraph.from(structure);
        ResidueGraph second = ResidueGraph.from(structure);

        assertEquals(first.residueIds(), second.residueIds());
        assertEquals(
                first.withinDistance(5.0, AtomSelection.ALL),
                second.withinDistance(5.0, AtomSelection.ALL));
        assertEquals(
                first.atomProximities(
                        AtomDistanceCriterion.allAtomsWithin(5.0)),
                second.atomProximities(
                        AtomDistanceCriterion.allAtomsWithin(5.0)));
    }

    @Test
    void validatesCutoffsAndSelections() {
        ResidueGraph graph = ResidueGraph.from(new Structure(List.of()));

        assertThrows(
                IllegalArgumentException.class,
                () -> graph.withinDistance(0.0, AtomSelection.ALL));
        assertThrows(
                IllegalArgumentException.class,
                () -> AtomDistanceCriterion.heavyAtomsWithin(Double.NaN));
        assertThrows(
                NullPointerException.class,
                () -> graph.withinDistance(4.0, null));
    }

    private static ResidueId id(
            String chain,
            int number,
            Character insertionCode) {

        return new ResidueId(chain, number, insertionCode);
    }

    private static Residue residue(
            String name,
            int number,
            Character insertionCode,
            Atom... atoms) {

        return new Residue(
                name,
                number,
                insertionCode,
                List.of(atoms));
    }

    private static Atom atom(String name, double x) {
        return atom(name, Element.C, x);
    }

    private static Atom atom(
            String name,
            Element element,
            double x) {

        return Atom.builder()
                .pdbSerial((int) (x * 10.0) + 1)
                .name(name)
                .position(new Point3D(x, 0.0, 0.0))
                .element(element)
                .build();
    }

    private static Bond bond(
            String chain,
            int firstNumber,
            Character firstInsertionCode,
            String firstAtom,
            int secondNumber,
            Character secondInsertionCode,
            String secondAtom) {

        return new Bond(
                reference(
                        chain,
                        firstNumber,
                        firstInsertionCode,
                        firstAtom),
                reference(
                        chain,
                        secondNumber,
                        secondInsertionCode,
                        secondAtom),
                BondOrder.SINGLE);
    }

    private static AtomReference reference(
            String chain,
            int number,
            Character insertionCode,
            String atom) {

        return new AtomReference(
                chain,
                number,
                insertionCode == null ? ' ' : insertionCode,
                atom);
    }
}
