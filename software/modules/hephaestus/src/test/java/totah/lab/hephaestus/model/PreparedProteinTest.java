package totah.lab.hephaestus.model;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.topology.TopologyModel;

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
}
