package totah.lab.hephaestus.client;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.gaia.molecule.Protein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HephaestusClientContractTest {
    @Test
    void publicFacadeExposesOnlyTruthfulOperations() throws Exception {
        var prepare = HephaestusClient.class.getMethod(
                "prepareReceptor", Path.class, ReceptorPreparationOptions.class);
        assertTrue(Arrays.asList(prepare.getExceptionTypes())
                .contains(IOException.class));
        HephaestusClient.class.getMethod(
                "prepareReceptor", Protein.class,
                ReceptorPreparationOptions.class);

        HephaestusClient.class.getMethod(
                "prepareAndWriteReceptor", Path.class, Path.class,
                ReceptorPreparationOptions.class);
        HephaestusClient.class.getMethod(
                "validatePreparedProtein", PreparedProtein.class);
        HephaestusClient.class.getMethod(
                "writePreparedReceptor", PreparedProtein.class, Path.class);
        HephaestusClient.class.getMethod("validatePdbqt", Path.class);
        HephaestusClient.class.getMethod(
                "validateFlexiblePdbqt", Path.class, Path.class);

        HephaestusClient.class.getMethod(
                "prepareLigand", Path.class, LigandPreparationOptions.class);
        HephaestusClient.class.getMethod(
                "prepareLigand", Ligand.class, LigandPreparationOptions.class);
        HephaestusClient.class.getMethod(
                "prepareAndWriteLigand", Path.class, Path.class,
                LigandPreparationOptions.class);
        HephaestusClient.class.getMethod(
                "writePreparedLigand", PreparedLigand.class, Path.class);
        HephaestusClient.class.getMethod(
                "validatePreparedLigand", PreparedLigand.class);

        assertEquals(12, HephaestusClient.class.getDeclaredMethods().length);
    }
}
