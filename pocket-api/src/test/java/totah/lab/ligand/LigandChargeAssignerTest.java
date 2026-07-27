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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandChargeAssignerTest {

    @Test
    void assignsFiniteGasteigerChargesAndPreservesNeutralTotal() {
        MolecularGraph water = graph(
                List.of(atom("O", "O", 0.0), atom("H1", "H", 1.0), atom("H2", "H", -1.0)),
                List.of(
                        property("O", 0, false, 0),
                        property("H1", 0, false, 1),
                        property("H2", 0, false, 2)),
                List.of(
                        new ChemicalBond(0, 1, BondOrder.SINGLE, false),
                        new ChemicalBond(0, 2, BondOrder.SINGLE, false)));

        LigandChargeAssignmentResult result = new LigandChargeAssigner().assign(water);

        assertEquals(0.0, result.totalPartialCharge(), 1.0e-12);
        assertEquals(0, result.totalFormalCharge());
        assertEquals("GasteigerModel", result.source());
        assertEquals(water.atoms().size(), result.graph().atoms().size());
        assertNotSame(water.atoms().get(0), result.graph().atoms().get(0));
        assertEquals(water.atoms().get(0).getPosition(),
                result.graph().atoms().get(0).getPosition());
        assertTrue(result.graph().atoms().stream()
                .allMatch(atom -> Double.isFinite(atom.getCharge())));
        assertTrue(result.graph().atoms().get(0).getCharge() < 0.0);
    }

    @Test
    void preservesPositiveCcdFormalCharge() {
        MolecularGraph ammoniumCenter = graph(
                List.of(atom("N", "N", 0.0)),
                List.of(property("N", 1, false, 0)),
                List.of());

        LigandChargeAssignmentResult result =
                new LigandChargeAssigner().assign(ammoniumCenter);

        assertEquals(1, result.totalFormalCharge());
        assertEquals(1.0, result.totalPartialCharge(), 1.0e-12);
        assertEquals(1.0, result.graph().atoms().getFirst().getCharge(), 1.0e-12);
    }

    @Test
    void exposesBondOrderFormalChargeAndAromaticityToChargeModels() {
        MolecularGraph graph = graph(
                List.of(atom("C1", "C", 0.0), atom("C2", "C", 1.0)),
                List.of(
                        property("C1", -1, true, 0),
                        property("C2", 0, true, 1)),
                List.of(new ChemicalBond(0, 1, BondOrder.DOUBLE, true)));

        LigandChargeSystem system = new LigandChargeSystem(graph);

        assertEquals(-1, system.getFormalCharge(0));
        assertEquals(2.0, system.getBondOrder(0, 1));
        assertEquals(2.0, system.getBondOrder(1, 0));
        assertTrue(system.isAromatic(0));
    }

    @Test
    void rejectsElementsWithoutGasteigerParameters() {
        MolecularGraph iron = graph(
                List.of(atom("FE", "Fe", 0.0)),
                List.of(property("FE", 2, false, 0)),
                List.of());

        assertThrows(IllegalArgumentException.class,
                () -> new LigandChargeAssigner().assign(iron));
    }

    private MolecularGraph graph(
            List<Atom> atoms,
            List<AtomChemicalProperties> properties,
            List<ChemicalBond> bonds) {
        return new MolecularGraph(atoms, bonds, properties);
    }

    private Atom atom(String name, String symbol, double x) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(x, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.builder().symbol(symbol).build())
                .build();
    }

    private AtomChemicalProperties property(
            String name,
            int formalCharge,
            boolean aromatic,
            int depositedIndex) {
        return new AtomChemicalProperties(
                name, formalCharge, aromatic, false, depositedIndex);
    }
}
