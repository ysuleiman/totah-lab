package totah.lab.hephaestus.model;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.topology.TopologyModel;
import totah.lab.hephaestus.charge.ChargeAssignment;
import totah.lab.hephaestus.flexibility.FlexibilityModel;
import totah.lab.hephaestus.typing.AtomTypeAssignment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PreparedProteinTest {

    @Test
    void shouldAddPreparationStateImmutably() {
        Protein protein = new Protein(
                "test",
                null,
                "test protein",
                null,
                null,
                null,
                new Structure(List.of()));
        TopologyModel topology = () -> "topology";

        PreparedProtein original = PreparedProtein.of(protein);
        PreparedProtein updated = original.withTopology(topology);

        assertTrue(original.topologyOptional().isEmpty());
        assertSame(topology, updated.topology());
        assertSame(protein, updated.protein());
    }

    @Test
    void shouldInvalidateAllDerivedStateForChangedStructure() {
        Protein originalProtein = protein("original");
        Protein changedProtein = protein("changed");
        TopologyModel topology = () -> "topology";
        ChargeAssignment charges = new ChargeAssignment("test", List.of());
        AtomTypeAssignment atomTypes = new AtomTypeAssignment("test", List.of());
        FlexibilityModel flexibility = FlexibilityModel.empty();
        PreparedProtein prepared = new PreparedProtein(
                originalProtein,
                topology,
                charges,
                atomTypes,
                flexibility,
                java.util.Map.of(
                        "residue-states", "derived",
                        "hydrogenation-report", "derived",
                        "hydrogen-optimization-report", "derived",
                        "pdbqt-export-validation-report", "derived",
                        "canonical-atom-resolution", "derived"));
        StructureChange change = new StructureChange(
                java.util.Set.of(
                        StructureChangeType.MUTATION,
                        StructureChangeType.ATOM_ADDITION,
                        StructureChangeType.ATOM_REMOVAL,
                        StructureChangeType.BOND_CHANGE,
                        StructureChangeType.COORDINATE_CHANGE),
                "C203N");

        PreparedProtein invalidated =
                prepared.withChangedStructure(changedProtein, change);

        assertSame(changedProtein, invalidated.protein());
        assertNull(invalidated.topology());
        assertNull(invalidated.charges());
        assertNull(invalidated.atomTypes());
        assertTrue(invalidated.flexibility().isEmpty());
        assertEquals(java.util.Map.of(
                PreparedProtein.STRUCTURE_CHANGE_ATTRIBUTE, change),
                invalidated.attributes());
        assertSame(topology, prepared.topology());
    }

    private static Protein protein(String id) {
        return new Protein(id, null, id, null, null, null, new Structure(List.of()));
    }
}
