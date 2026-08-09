package totah.lab.hephaestus;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hermes.file.pdb.reader.PdbReader;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HephaestusTest {

    @Test
    void preparesLigandWithCanonicalDefaults() throws Exception {
        Ligand ligand = glycerolLigand();

        PreparedLigand prepared = Hephaestus.prepareLigand(ligand);

        assertEquals(ligand.id(), prepared.ligand().id());
        assertTrue(prepared.topologyOptional().isPresent());
        assertTrue(prepared.chargesOptional().isPresent());
        assertTrue(prepared.atomTypesOptional().isPresent());
    }

    @Test
    void exposesDetailedPreparationResult() throws Exception {
        var result = Hephaestus.prepareLigandDetailed(
                glycerolLigand(),
                LigandPreparationOptions.defaults());

        assertTrue(result.successful());
        assertNotNull(result.preparedLigand());
    }

    @Test
    void rejectsNullInputsBeforeBuildingPreparationClient() {
        assertThrows(
                NullPointerException.class,
                () -> Hephaestus.prepareLigand(null));
        assertThrows(
                NullPointerException.class,
                () -> Hephaestus.prepareReceptor(null));
    }

    private Ligand glycerolLigand() throws Exception {
        Path pdb = Path.of(getClass().getResource(
                "/ligand/4E1J-glycerol-panel.pdb").toURI());
        var residue = new PdbReader().read(pdb)
                .findResidue("A", 601)
                .orElseThrow();
        return new Ligand(
                "GOL-A-601",
                "Glycerol",
                "GOL",
                null,
                null,
                null,
                new Structure(List.of(new Chain("A", List.of(residue)))));
    }
}
