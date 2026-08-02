package totah.lab.hephaestus.receptor.hydrogen;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceptorHydrogenatorTest {

    private final ReceptorHydrogenator hydrogenator =
            new ReceptorHydrogenator();

    @Test
    void hydrogenatesEachChainAsAnIndependentTerminalSequence() {
        Residue residueA1 = glycine(1, 0.0);
        Residue residueA2 = glycine(2, 4.0);
        Residue residueB1 = glycine(3, 20.0);
        Residue residueB2 = glycine(4, 24.0);

        Chain chainA = new Chain("A", List.of(residueA1, residueA2));
        Chain chainB = new Chain("B", List.of(residueB1, residueB2));

        Map<String, String> templates = Map.of(
                "A:1", "NGLY",
                "A:2", "CGLY",
                "B:3", "NGLY",
                "B:4", "CGLY");

        List<Residue> outputA = hydrogenator.hydrogenate(
                chainA,
                ProtonationConfig.defaults(),
                templates);

        List<Residue> outputB = hydrogenator.hydrogenate(
                chainB,
                ProtonationConfig.defaults(),
                templates);

        assertEquals(2, outputA.size());
        assertEquals(2, outputB.size());
        assertTrue(outputA.getFirst().containsAtom("H1"));
        assertTrue(outputB.getFirst().containsAtom("H1"));
        assertFalse(outputA.getFirst().containsAtom("H"));
        assertFalse(outputB.getFirst().containsAtom("H"));
        assertTrue(outputA.getLast().containsAtom("OXT"));
        assertTrue(outputB.getLast().containsAtom("OXT"));
        assertEquals(List.of(1, 2), outputA.stream()
                .map(Residue::getNumber)
                .toList());
        assertEquals(List.of(3, 4), outputB.stream()
                .map(Residue::getNumber)
                .toList());
    }

    @Test
    void returnsNewResiduesWithoutChangingInputAtomOrder() {
        Residue input = glycine(7, 0.0);
        List<String> originalAtomNames = input.getAtoms()
                .stream()
                .map(Atom::getName)
                .toList();

        List<Residue> output = hydrogenator.hydrogenate(
                new Chain("A", List.of(input)),
                ProtonationConfig.defaults(),
                Map.of("A:7", "NGLY"));

        assertNotSame(input, output.getFirst());
        assertEquals(
                originalAtomNames,
                input.getAtoms().stream().map(Atom::getName).toList());
        assertEquals(4, input.getAtomCount());
        assertTrue(output.getFirst().getAtomCount() > input.getAtomCount());
        assertEquals(
                originalAtomNames,
                output.getFirst().getAtoms()
                        .subList(0, originalAtomNames.size())
                        .stream()
                        .map(Atom::getName)
                        .toList());
    }

    private Residue glycine(
            int residueNumber,
            double offset) {

        return new Residue(
                "GLY",
                residueNumber,
                List.of(
                        atom("N", Element.N, offset, 0.0, 0.0),
                        atom("CA", Element.C, offset + 1.45, 0.0, 0.0),
                        atom("C", Element.C, offset + 2.05, 1.35, 0.0),
                        atom("O", Element.O, offset + 1.45, 2.40, 0.0)));
    }

    private Atom atom(
            String name,
            Element element,
            double x,
            double y,
            double z) {

        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(x, y, z))
                .occupancy(1.0)
                .build();
    }
}
