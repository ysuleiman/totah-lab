package totah.lab.hephaestus.receptor.operation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.residue.ResidueState;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidueStateAssignmentOperationTest {

    @Test
    void singleResidueChainIsTreatedAsNTerminusOnly() {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(alanine(1)))));

        PreparedProtein output = new ResidueStateAssignmentOperation()
                .apply(PreparedProtein.of(protein(structure)),
                        ReceptorPreparationOptions.defaults())
                .value();

        Map<String, ResidueState> states = states(output);

        ResidueState state = states.get("A:1");
        assertEquals("NALA", state.amberTemplateName());
        assertTrue(state.nTerminus());
        assertFalse(state.cTerminus());
    }

    @Test
    void monoatomicMetalIonsPassThroughWithoutResidueState() {
        Residue zinc = new Residue("ZN", 3, List.of(
                Atom.builder()
                        .name("ZN")
                        .element(Element.ZN)
                        .position(new Point3D(10.0, 0.0, 0.0))
                        .occupancy(1.0)
                        .build()));

        Structure structure = new Structure(List.of(
                new Chain("A", List.of(alanine(1), alanine(2), zinc))));

        PreparedProtein output = new ResidueStateAssignmentOperation()
                .apply(PreparedProtein.of(protein(structure)),
                        ReceptorPreparationOptions.defaults())
                .value();

        Map<String, ResidueState> states = states(output);

        assertTrue(states.containsKey("A:1"));
        assertTrue(states.containsKey("A:2"));
        assertFalse(states.containsKey("A:3"));
        assertEquals(3, output.protein().structure().getResidueCount());
        assertEquals("ZN", output.protein().structure()
                .getChains().getFirst().residues().get(2).getName());
    }

    @SuppressWarnings("unchecked")
    private Map<String, ResidueState> states(PreparedProtein output) {
        return (Map<String, ResidueState>) output.attributes().get(
                ResidueStateAssignmentOperation.RESIDUE_STATES_ATTRIBUTE);
    }

    private Protein protein(Structure structure) {
        return new Protein(
                "protein", null, "protein", null, null, null, structure);
    }

    private Residue alanine(int number) {
        double offset = (number - 1) * 3.8;
        return new Residue("ALA", number, List.of(
                atom("N", Element.N, offset),
                atom("CA", Element.C, offset + 1.45),
                atom("C", Element.C, offset + 2.45),
                atom("O", Element.O, offset + 3.05),
                atom("CB", Element.C, offset + 1.45)));
    }

    private Atom atom(String name, Element element, double x) {
        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(x, 0.0, 0.0))
                .occupancy(1.0)
                .build();
    }
}
