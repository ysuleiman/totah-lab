package totah.lab.ligand.typing;

import org.junit.jupiter.api.Test;
import totah.lab.chemistry.AtomChemicalProperties;
import totah.lab.chemistry.BondOrder;
import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LigandAd4AtomTyperTest {

    private final LigandAd4AtomTyper typer = new LigandAd4AtomTyper();

    @Test
    void distinguishesAliphaticAndAromaticCarbon() {
        MolecularGraph graph = graph(
                List.of(atom("C1", "C"), atom("C2", "C"), atom("H1", "H")),
                List.of(property("C1", 0, true), property("C2", 0, false),
                        property("H1", 0, false)),
                List.of(
                        bond(0, 1, BondOrder.AROMATIC, true),
                        bond(1, 2, BondOrder.SINGLE, false)));

        LigandAd4TypingResult result = typer.assign(graph);

        assertEquals(List.of("A", "A", "H"), types(result.graph()));
        assertEquals(2, result.typeCounts().get("A"));
        assertNotSame(graph.atoms().getFirst(), result.graph().atoms().getFirst());
        assertEquals(graph.bonds(), result.graph().bonds());
        assertEquals(graph.atomProperties(), result.graph().atomProperties());
    }

    @Test
    void distinguishesDonorAndNonDonorHydrogen() {
        MolecularGraph graph = graph(
                List.of(atom("N", "N"), atom("HN", "H"), atom("C", "C"),
                        atom("HC", "H")),
                properties("N", "HN", "C", "HC"),
                List.of(
                        bond(0, 1, BondOrder.SINGLE, false),
                        bond(2, 3, BondOrder.SINGLE, false)));

        assertEquals(List.of("NA", "HD", "C", "H"),
                types(typer.assign(graph).graph()));
    }

    @Test
    void distinguishesNitrogenAcceptorProtonatedAmideAndAromaticStates() {
        MolecularGraph graph = graph(
                List.of(
                        atom("NAMINE", "N"),
                        atom("NPLUS", "N"),
                        atom("NAMIDE", "N"),
                        atom("CCO", "C"),
                        atom("OCO", "O"),
                        atom("NAR", "N"),
                        atom("NARH", "N"),
                        atom("HAR", "H")),
                List.of(
                        property("NAMINE", 0, false),
                        property("NPLUS", 1, false),
                        property("NAMIDE", 0, false),
                        property("CCO", 0, false),
                        property("OCO", 0, false),
                        property("NAR", 0, true),
                        property("NARH", 0, true),
                        property("HAR", 0, false)),
                List.of(
                        bond(2, 3, BondOrder.SINGLE, false),
                        bond(3, 4, BondOrder.DOUBLE, false),
                        bond(5, 6, BondOrder.AROMATIC, true),
                        bond(6, 7, BondOrder.SINGLE, false)));

        assertEquals(List.of("NA", "N", "N", "C", "OA", "NA", "N", "HD"),
                types(typer.assign(graph).graph()));
    }

    @Test
    void typesOxygenCarboxylateSulfurPhosphateHalogensAndMetals() {
        MolecularGraph graph = graph(
                List.of(
                        atom("OPLUS", "O"), atom("OMINUS", "O"),
                        atom("STHIO", "S"), atom("SSULFONE", "S"),
                        atom("OSULFONE1", "O"), atom("OSULFONE2", "O"), atom("P", "P"),
                        atom("F", "F"), atom("CL", "Cl"), atom("BR", "Br"),
                        atom("I", "I"), atom("ZN", "Zn")),
                List.of(
                        property("OPLUS", 1, false), property("OMINUS", -1, false),
                        property("STHIO", 0, false), property("SSULFONE", 0, false),
                        property("OSULFONE1", 0, false),
                        property("OSULFONE2", 0, false), property("P", 0, false),
                        property("F", 0, false), property("CL", 0, false),
                        property("BR", 0, false), property("I", 0, false),
                        property("ZN", 2, false)),
                List.of(
                        bond(3, 4, BondOrder.DOUBLE, false),
                        bond(3, 5, BondOrder.DOUBLE, false)));

        assertEquals(
                List.of("O", "OA", "SA", "S", "OA", "OA", "P",
                        "F", "Cl", "Br", "I", "Zn"),
                types(typer.assign(graph).graph()));
    }

    @Test
    void rejectsUnsupportedElementsAndInvalidHydrogenTopology() {
        MolecularGraph silicon = graph(
                List.of(atom("SI", "Si")),
                List.of(property("SI", 0, false)),
                List.of());
        MolecularGraph freeHydrogen = graph(
                List.of(atom("H", "H")),
                List.of(property("H", 0, false)),
                List.of());

        assertThrows(IllegalArgumentException.class, () -> typer.assign(silicon));
        assertThrows(IllegalStateException.class, () -> typer.assign(freeHydrogen));
    }

    private List<String> types(MolecularGraph graph) {
        return graph.atoms().stream().map(Atom::getAutoDockType).toList();
    }

    private MolecularGraph graph(
            List<Atom> atoms,
            List<AtomChemicalProperties> properties,
            List<ChemicalBond> bonds) {
        return new MolecularGraph(atoms, bonds, properties);
    }

    private List<AtomChemicalProperties> properties(String... names) {
        return java.util.stream.IntStream.range(0, names.length)
                .mapToObj(index -> property(names[index], 0, false))
                .toList();
    }

    private Atom atom(String name, String symbol) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(0.0, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.fromSymbol(symbol))
                .build();
    }

    private AtomChemicalProperties property(
            String name,
            int formalCharge,
            boolean aromatic) {
        return new AtomChemicalProperties(name, formalCharge, aromatic, false, null);
    }

    private ChemicalBond bond(
            int first,
            int second,
            BondOrder order,
            boolean aromatic) {
        return new ChemicalBond(first, second, order, aromatic);
    }
}
