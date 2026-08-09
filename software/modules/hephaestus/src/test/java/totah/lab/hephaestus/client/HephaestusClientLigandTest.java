package totah.lab.hephaestus.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.validation.ValidationException;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.pdbqt.validation.PdbqtValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HephaestusClientLigandTest {

    @TempDir
    Path temporaryDirectory;

    private final HephaestusClient client = HephaestusClients.createDefault();

    @Test
    void preparesSdfLigandAndWritesValidatedPdbqt() throws Exception {
        Path sdf = writeEthanolSdf();

        LigandPreparationResult result = client.prepareLigand(
                sdf, LigandPreparationOptions.defaults());

        assertTrue(result.successful());
        assertFalse(result.hasWarnings());
        assertTrue(client.validatePreparedLigand(result.preparedLigand()).valid());

        Path output = client.writePreparedLigand(
                result.preparedLigand(), temporaryDirectory.resolve("ethanol.pdbqt"));
        assertTrue(Files.exists(output));
        assertTrue(new PdbqtValidator().validateLigandPdbqt(output).valid());
    }

    @Test
    void prepareAndWriteLigandChainsPreparationAndExport() throws Exception {
        Path output = client.prepareAndWriteLigand(
                writeEthanolSdf(),
                temporaryDirectory.resolve("combined.pdbqt"),
                LigandPreparationOptions.defaults());

        assertTrue(Files.exists(output));
        assertTrue(new PdbqtValidator().validateLigandPdbqt(output).valid());
    }

    @Test
    void refusesToWriteUnpreparedLigand() throws Exception {
        Path sdf = writeEthanolSdf();
        Ligand ligand = new totah.lab.hermes.file.sdf.reader.SdfLigandReader().read(sdf);

        assertThrows(ValidationException.class, () -> client.writePreparedLigand(
                PreparedLigand.of(ligand), temporaryDirectory.resolve("raw.pdbqt")));
    }

    @Test
    void preparesCcdBackedLigandThroughLigandOverload() throws Exception {
        Path pdb = Path.of(getClass().getResource(
                "/ligand/4E1J-glycerol-panel.pdb").toURI());
        var residue = new PdbReader().read(pdb)
                .findResidue("A", 601).orElseThrow();
        Ligand ligand = new Ligand("GOL-A-601", "Glycerol", "GOL", null, null, null,
                new Structure(List.of(new Chain("A", List.of(residue)))));

        LigandPreparationResult result = client.prepareLigand(
                ligand, LigandPreparationOptions.defaults());

        assertTrue(result.successful());
    }

    private Path writeEthanolSdf() throws Exception {
        StringBuilder text = new StringBuilder("ETH\n  unit-test\n\n");
        String[] symbols = {"C", "C", "O", "H", "H", "H", "H", "H", "H"};
        int[][] bonds = {{1, 2, 1}, {2, 3, 1}, {3, 4, 1}, {1, 5, 1}, {1, 6, 1},
                {1, 7, 1}, {2, 8, 1}, {2, 9, 1}};
        text.append(String.format(Locale.US, "%3d%3d  0  0  0  0  0  0  0  0999 V2000",
                symbols.length, bonds.length)).append('\n');
        for (int index = 0; index < symbols.length; index++) {
            text.append(String.format(Locale.US, "%10.4f%10.4f%10.4f %-3s 0  0  0  0  0  0",
                    index * 1.5, (index % 3) * 1.5, (index % 2) * 1.5, symbols[index]))
                    .append('\n');
        }
        for (int[] bond : bonds) {
            text.append(String.format(Locale.US, "%3d%3d%3d  0  0  0",
                    bond[0], bond[1], bond[2])).append('\n');
        }
        text.append("M  END\n$$$$\n");
        Path path = temporaryDirectory.resolve("ethanol.sdf");
        Files.writeString(path, text.toString());
        return path;
    }
}
