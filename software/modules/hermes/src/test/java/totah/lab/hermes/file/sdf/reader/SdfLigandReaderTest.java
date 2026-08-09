package totah.lab.hermes.file.sdf.reader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.hermes.file.sdf.SdfLigand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdfLigandReaderTest {

    @TempDir
    Path temporaryDirectory;

    private final SdfLigandReader reader = new SdfLigandReader();

    @Test
    void readsSingleRecordWithExplicitHydrogens() throws Exception {
        SdfLigand model = reader.readModel(write("methane.sdf", String.join("\n",
                "Methane",
                "  unit-test",
                "",
                counts(5, 4),
                atom(0.0, 0.0, 0.0, "C", 0),
                atom(0.6, 0.6, 0.6, "H", 0),
                atom(-0.6, -0.6, 0.6, "H", 0),
                atom(-0.6, 0.6, -0.6, "H", 0),
                atom(0.6, -0.6, -0.6, "H", 0),
                bond(1, 2, 1),
                bond(1, 3, 1),
                bond(1, 4, 1),
                bond(1, 5, 1),
                "M  END",
                "$$$$")));

        assertEquals("Methane", model.title());
        assertEquals(5, model.atomCount());
        assertTrue(model.hasExplicitHydrogens());
        assertEquals(1, model.fragments().size());

        Ligand ligand = model.ligand();
        assertEquals("MET", residue(ligand).getName());
        assertEquals("L", ligand.structure().getChains().getFirst().id());
        assertEquals(List.of("C1", "H2", "H3", "H4", "H5"),
                residue(ligand).getAtoms().stream().map(Atom::getName).toList());
        assertEquals(Element.C, residue(ligand).getAtoms().get(0).getElement());
        assertEquals(0.6, residue(ligand).getAtoms().get(1).getPosition().x(), 1.0e-9);
        assertEquals(4, ligand.structure().bonds().size());
        assertTrue(ligand.formalCharge().isNeutral());
    }

    @Test
    void preservesBondOrdersIncludingAromatic() throws Exception {
        SdfLigand model = reader.readModel(write("orders.sdf", String.join("\n",
                "orders",
                "  unit-test",
                "",
                counts(4, 3),
                atom(0.0, 0.0, 0.0, "C", 0),
                atom(1.3, 0.0, 0.0, "C", 0),
                atom(2.5, 0.0, 0.0, "N", 0),
                atom(3.7, 0.0, 0.0, "O", 0),
                bond(1, 2, 2),
                bond(2, 3, 3),
                bond(3, 4, 4),
                "M  END",
                "$$$$")));

        assertEquals(BondOrder.DOUBLE, model.bonds().get(0).order());
        assertEquals(BondOrder.TRIPLE, model.bonds().get(1).order());
        assertEquals(BondOrder.AROMATIC, model.bonds().get(2).order());
        assertTrue(model.bonds().get(2).aromatic());
        assertFalse(model.bonds().get(0).aromatic());
        assertEquals(BondOrder.DOUBLE, model.ligand().structure().bonds().get(0).order());
    }

    @Test
    void appliesMChgLinesOverAtomBlockChargeCodes() throws Exception {
        SdfLigand model = reader.readModel(write("charged.sdf", String.join("\n",
                "charged",
                "  unit-test",
                "",
                counts(2, 1),
                atom(0.0, 0.0, 0.0, "N", 3),
                atom(1.5, 0.0, 0.0, "O", 0),
                bond(1, 2, 1),
                "M  CHG  1   2  -1",
                "M  END",
                "$$$$")));

        assertEquals(List.of(1, -1), model.formalCharges());
        assertTrue(model.ligand().formalCharge().isNeutral());
    }

    @Test
    void rejectsDuplicateBonds() {
        IOException exception = assertThrows(IOException.class, () -> parse(String.join("\n",
                "dup",
                "  unit-test",
                "",
                counts(2, 2),
                atom(0.0, 0.0, 0.0, "C", 0),
                atom(1.5, 0.0, 0.0, "C", 0),
                bond(1, 2, 1),
                bond(2, 1, 1),
                "M  END",
                "$$$$")));
        assertTrue(exception.getMessage().contains("Duplicate bond"));
    }

    @Test
    void rejectsBondReferencingMissingAtom() {
        IOException exception = assertThrows(IOException.class, () -> parse(String.join("\n",
                "oob",
                "  unit-test",
                "",
                counts(2, 1),
                atom(0.0, 0.0, 0.0, "C", 0),
                atom(1.5, 0.0, 0.0, "C", 0),
                bond(1, 3, 1),
                "M  END",
                "$$$$")));
        assertTrue(exception.getMessage().contains("outside the atom block"));
    }

    @Test
    void rejectsUnsupportedBondType() {
        IOException exception = assertThrows(IOException.class, () -> parse(String.join("\n",
                "bond-type",
                "  unit-test",
                "",
                counts(2, 1),
                atom(0.0, 0.0, 0.0, "C", 0),
                atom(1.5, 0.0, 0.0, "C", 0),
                bond(1, 2, 8),
                "M  END",
                "$$$$")));
        assertTrue(exception.getMessage().contains("Unsupported SDF bond type"));
    }

    @Test
    void rejectsRadicalChargeCode() {
        IOException exception = assertThrows(IOException.class, () -> parse(String.join("\n",
                "radical",
                "  unit-test",
                "",
                counts(1, 0),
                atom(0.0, 0.0, 0.0, "C", 4),
                "M  END",
                "$$$$")));
        assertTrue(exception.getMessage().contains("Radical"));
    }

    @Test
    void rejectsV3000Records() {
        IOException exception = assertThrows(IOException.class, () -> parse(String.join("\n",
                "v3000",
                "  unit-test",
                "",
                "  0  0  0  0  0  0            999 V3000",
                "M  V30 BEGIN CTAB",
                "M  END")));
        assertTrue(exception.getMessage().contains("V3000"));
    }

    @Test
    void rejectsMultiMoleculeFiles() {
        String record = String.join("\n",
                "first",
                "  unit-test",
                "",
                counts(1, 0),
                atom(0.0, 0.0, 0.0, "C", 0),
                "M  END",
                "$$$$");
        String second = String.join("\n",
                "second",
                "  unit-test",
                "",
                counts(1, 0),
                atom(0.0, 0.0, 0.0, "N", 0),
                "M  END",
                "$$$$");
        IOException exception = assertThrows(IOException.class,
                () -> parse(record + "\n" + second));
        assertTrue(exception.getMessage().contains("Multi-molecule"));
    }

    @Test
    void toleratesPropertyBlocksAfterTheRecord() throws IOException {
        String record = String.join("\n",
                "first",
                "  unit-test",
                "",
                counts(1, 0),
                atom(0.0, 0.0, 0.0, "C", 0),
                "M  END",
                ">  <PUBCHEM_COMPOUND_CID>  (1) ",
                "1215",
                "",
                ">  <PUBCHEM_CONFORMER_RMSD>  (1) ",
                "0.4",
                "",
                "$$$$");
        SdfLigand parsed = parse(record);
        assertEquals(1, parsed.ligand().structure()
                .getChains().getFirst().residues().getFirst()
                .getAtomCount());
    }

    @Test
    void rejectsTruncatedRecords() {
        IOException exception = assertThrows(IOException.class, () -> parse(String.join("\n",
                "truncated",
                "  unit-test",
                "",
                counts(2, 1),
                atom(0.0, 0.0, 0.0, "C", 0),
                "M  END")));
        assertTrue(exception.getMessage().contains("truncated"));
    }

    @Test
    void detectsDisconnectedFragments() throws Exception {
        SdfLigand model = reader.readModel(write("salt.sdf", String.join("\n",
                "salt",
                "  unit-test",
                "",
                counts(3, 1),
                atom(0.0, 0.0, 0.0, "C", 0),
                atom(1.5, 0.0, 0.0, "N", 0),
                atom(5.0, 5.0, 5.0, "Na", 0),
                bond(1, 2, 1),
                "M  END",
                "$$$$")));

        List<List<Integer>> fragments = model.fragments();
        assertEquals(2, fragments.size());
        assertEquals(List.of(0, 1), fragments.get(0));
        assertEquals(List.of(2), fragments.get(1));
    }

    @Test
    void declaresSupportedExtensions() {
        assertTrue(reader.supports(Path.of("ligand.sdf")));
        assertTrue(reader.supports(Path.of("ligand.mol")));
        assertFalse(reader.supports(Path.of("structure.pdb")));
    }

    private SdfLigand parse(String content) throws IOException {
        return reader.readModel(write("input.sdf", content));
    }

    private Path write(String name, String content) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private static Residue residue(Ligand ligand) {
        return ligand.structure().getChains().getFirst().residues().getFirst();
    }

    private static String counts(int atoms, int bonds) {
        return String.format(Locale.US, "%3d%3d  0  0  0  0  0  0  0  0999 V2000", atoms, bonds);
    }

    private static String atom(double x, double y, double z, String symbol, int chargeCode) {
        return String.format(Locale.US, "%10.4f%10.4f%10.4f %-3s 0%3d  0  0  0  0  0",
                x, y, z, symbol, chargeCode);
    }

    private static String bond(int first, int second, int type) {
        return String.format(Locale.US, "%3d%3d%3d  0  0  0", first, second, type);
    }
}
