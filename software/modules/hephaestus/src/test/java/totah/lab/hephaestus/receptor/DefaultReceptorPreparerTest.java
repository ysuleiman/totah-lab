package totah.lab.hephaestus.receptor;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultReceptorPreparerTest {

    @Test
    void shouldCarryPreparedProteinAcrossOperations() {
        Protein protein = new Protein(
                "test",
                null,
                "test protein",
                null,
                null,
                null,
                new Structure(List.of()));

        ReceptorPreparationOperation operation =
                (prepared, options) ->
                        OperationResult.success(
                                prepared.withAttribute("cleaned", true));

        ReceptorPreparationResult result =
                new DefaultReceptorPreparer(List.of(operation))
                        .prepare(new ReceptorPreparationRequest(protein));

        assertEquals(
                true,
                result.preparedProtein().attributes().get("cleaned"));
        assertTrue(result.successful());
    }
}
