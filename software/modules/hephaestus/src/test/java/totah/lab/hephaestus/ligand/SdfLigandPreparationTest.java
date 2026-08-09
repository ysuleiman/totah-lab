package totah.lab.hephaestus.ligand;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.structure.Atom;
import totah.lab.hephaestus.ligand.flexibility.LigandFlexibilityModel;
import totah.lab.hephaestus.ligand.operation.LigandPdbqtExportOperation;
import totah.lab.hephaestus.ligand.operation.SdfLigandTopologyOperation;
import totah.lab.hephaestus.ligand.topology.LigandTopology;
import totah.lab.hephaestus.model.Severity;
import totah.lab.hermes.file.sdf.SdfLigand;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;
import totah.lab.hermes.file.pdbqt.validation.PdbqtValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdfLigandPreparationTest {

    @TempDir
    Path temporaryDirectory;

    private final SdfLigandReader reader = new SdfLigandReader();

    @Test
    void buildsTopologyFromParsedBondsAndCharges() throws Exception {
        SdfLigand model = model("ETH", atoms("C", "C", "O", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 1), bond(3, 4, 1),
                        bond(1, 5, 1), bond(1, 6, 1), bond(1, 7, 1),
                        bond(2, 8, 1), bond(2, 9, 1)));

        LigandPreparationResult result = prepare(model);

        assertTrue(result.successful());
        LigandTopology topology =
                (LigandTopology) result.preparedLigand().topology();
        assertEquals("ETH", topology.componentId());
        assertEquals(9, topology.atomCount());
        assertEquals(8, topology.bonds().size());
        assertTrue(topology.missingHydrogens().isEmpty());
        assertEquals(BondOrder.SINGLE, topology.bonds().get(0).order());
        assertTrue(topology.atomProperties().stream()
                .allMatch(property -> property.formalCharge() == 0));
    }

    @Test
    void assignsFiniteChargesSummingToFormalCharge() throws Exception {
        // Acetate: deprotonated carboxylate oxygen carries -1 via M  CHG.
        SdfLigand model = model("ACT", atoms("C", "C", "O", "O", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 2), bond(2, 4, 1),
                        bond(1, 5, 1), bond(1, 6, 1), bond(1, 7, 1)),
                "M  CHG  1   4  -1");

        LigandPreparationResult result = prepare(model);

        assertTrue(result.successful());
        List<Atom> atoms = atoms(result);
        assertTrue(atoms.stream().allMatch(atom -> Double.isFinite(atom.getCharge())));
        double total = atoms.stream().mapToDouble(Atom::getCharge).sum();
        assertEquals(-1.0, total, 1.0e-6);
        assertEquals(-1, result.preparedLigand().ligand().formalCharge().value());
    }

    @Test
    void assignsKnownAd4Types() throws Exception {
        SdfLigand model = model("ETH", atoms("C", "C", "O", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 1), bond(3, 4, 1),
                        bond(1, 5, 1), bond(1, 6, 1), bond(1, 7, 1),
                        bond(2, 8, 1), bond(2, 9, 1)));

        LigandPreparationResult result = prepare(model);

        List<String> types = atoms(result).stream().map(Atom::getAutoDockType).toList();
        assertEquals(List.of("C", "C", "OA", "HD", "H", "H", "H", "H", "H"), types);
    }

    @Test
    void typesAromaticCarbonsAsA() throws Exception {
        SdfLigand model = model("BEN", atoms("C", "C", "C", "C", "C", "C",
                        "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 4), bond(2, 3, 4), bond(3, 4, 4),
                        bond(4, 5, 4), bond(5, 6, 4), bond(6, 1, 4),
                        bond(1, 7, 1), bond(2, 8, 1), bond(3, 9, 1),
                        bond(4, 10, 1), bond(5, 11, 1), bond(6, 12, 1)));

        LigandPreparationResult result = prepare(model);

        List<String> types = atoms(result).stream().map(Atom::getAutoDockType).toList();
        assertEquals(List.of("A", "A", "A", "A", "A", "A",
                "H", "H", "H", "H", "H", "H"), types);
        assertEquals(0, torsions(result));
    }

    @Test
    void rejectsUnsupportedElements() throws Exception {
        SdfLigand model = model("XXX", atoms("Xx", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(1, 3, 1), bond(1, 4, 1), bond(1, 5, 1)));

        UnsupportedLigandException exception = assertThrows(
                UnsupportedLigandException.class, () -> prepare(model));
        assertEquals(LigandUnsupportedReason.UNSUPPORTED_ELEMENT_FOR_CHARGE,
                exception.getReason());
    }

    @Test
    void rejectsDisconnectedInputByDefault() throws Exception {
        SdfLigand model = disconnectedEthanolWater();

        UnsupportedLigandException exception = assertThrows(
                UnsupportedLigandException.class, () -> prepare(model));
        assertEquals(LigandUnsupportedReason.DISCONNECTED_GRAPH, exception.getReason());
    }

    @Test
    void selectsLargestHeavyAtomFragmentWhenEnabled() throws Exception {
        SdfLigand model = disconnectedEthanolWater();
        LigandPreparationOptions options = selectingFragments();

        LigandPreparationResult result = DefaultLigandPreparer.sdf(model)
                .prepare(new LigandPreparationRequest(model.ligand(), options));

        assertTrue(result.successful());
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.severity() == Severity.WARNING
                        && issue.code().equals(
                        SdfLigandTopologyOperation.LARGEST_FRAGMENT_ISSUE_CODE)));
        // Water fragment (3 atoms) is stripped; ethanol (9 atoms) remains.
        assertEquals(9, atoms(result).size());
        assertEquals(9, ((LigandTopology) result.preparedLigand().topology()).atomCount());
        assertTrue(atoms(result).stream()
                .noneMatch(atom -> atom.getName().equals("O10")));
    }

    @Test
    void rejectsHydrogenlessInputWhenHydrogensAreRequested() throws Exception {
        SdfLigand model = model("ETH", atoms("C", "C"), bonds(bond(1, 2, 1)));

        UnsupportedLigandException exception = assertThrows(
                UnsupportedLigandException.class, () -> prepare(model));
        assertEquals(LigandUnsupportedReason.UNUSABLE_HYDROGEN_REFERENCE_GEOMETRY,
                exception.getReason());
    }

    @Test
    void rotatableBondRulesExcludeRingDoubleAndAmideBonds() throws Exception {
        // Butane: only the central C2-C3 bond is rotatable.
        assertEquals(1, torsions(prepare(model("BUT",
                atoms("C", "C", "C", "C", "H", "H", "H", "H", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 1), bond(3, 4, 1),
                        bond(1, 5, 1), bond(1, 6, 1), bond(1, 7, 1),
                        bond(2, 8, 1), bond(2, 9, 1), bond(3, 10, 1), bond(3, 11, 1),
                        bond(4, 12, 1), bond(4, 13, 1), bond(4, 14, 1))))));
        // Ethene: the double bond is not rotatable.
        assertEquals(0, torsions(prepare(model("ETH",
                atoms("C", "C", "H", "H", "H", "H"),
                bonds(bond(1, 2, 2), bond(1, 3, 1), bond(1, 4, 1),
                        bond(2, 5, 1), bond(2, 6, 1))))));
        // Cyclopropane: ring bonds are not rotatable.
        assertEquals(0, torsions(prepare(model("CYC",
                atoms("C", "C", "C", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 1), bond(3, 1, 1),
                        bond(1, 4, 1), bond(1, 5, 1), bond(2, 6, 1),
                        bond(2, 7, 1), bond(3, 8, 1), bond(3, 9, 1))))));
        // Acetamide: the amide C-N bond is not rotatable.
        assertEquals(0, torsions(prepare(model("ACE",
                atoms("C", "C", "O", "N", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 2), bond(2, 4, 1),
                        bond(4, 5, 1), bond(4, 6, 1),
                        bond(1, 7, 1), bond(1, 8, 1), bond(1, 9, 1))))));
    }

    @Test
    void exportsDeterministicBalancedPdbqt() throws Exception {
        SdfLigand model = model("BUT",
                atoms("C", "C", "C", "C", "H", "H", "H", "H", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 1), bond(3, 4, 1),
                        bond(1, 5, 1), bond(1, 6, 1), bond(1, 7, 1),
                        bond(2, 8, 1), bond(2, 9, 1), bond(3, 10, 1), bond(3, 11, 1),
                        bond(4, 12, 1), bond(4, 13, 1), bond(4, 14, 1)));

        String first = export(prepare(model), "first.pdbqt");
        String second = export(prepare(model), "second.pdbqt");

        assertEquals(first, second);
        List<String> lines = first.lines().toList();
        assertEquals(1, lines.stream().filter("ROOT"::equals).count());
        assertEquals(1, lines.stream().filter("ENDROOT"::equals).count());
        assertEquals(lines.stream().filter(line -> line.startsWith("BRANCH ")).count(),
                lines.stream().filter(line -> line.startsWith("ENDBRANCH ")).count());
        assertEquals(14, lines.stream().filter(line -> line.startsWith("ATOM")).count());
        assertEquals(14, lines.stream()
                .filter(line -> line.startsWith("ATOM"))
                .map(line -> line.substring(6, 11).trim()).distinct().count());
        assertEquals("TORSDOF 1", lines.stream()
                .filter(line -> line.startsWith("TORSDOF")).findFirst().orElseThrow());
        Path written = temporaryDirectory.resolve("first.pdbqt");
        assertTrue(new PdbqtValidator().validateLigandPdbqt(written).valid());
    }

    private LigandPreparationResult prepare(SdfLigand model) {
        return DefaultLigandPreparer.sdf(model)
                .prepare(new LigandPreparationRequest(model.ligand()));
    }

    private LigandPreparationOptions selectingFragments() {
        LigandPreparationOptions defaults = LigandPreparationOptions.defaults();
        return new LigandPreparationOptions(
                defaults.addHydrogens(), defaults.generateProtonationStates(),
                defaults.generateTautomers(), defaults.assignCharges(),
                defaults.assignAtomTypes(), defaults.generateConformers(),
                defaults.maximumConformers(), true);
    }

    private SdfLigand disconnectedEthanolWater() throws IOException {
        return model("MIX",
                atoms("C", "C", "O", "H", "H", "H", "H", "H", "H", "O", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 1), bond(3, 4, 1),
                        bond(1, 5, 1), bond(1, 6, 1), bond(1, 7, 1),
                        bond(2, 8, 1), bond(2, 9, 1), bond(10, 11, 1), bond(10, 12, 1)));
    }

    private String export(LigandPreparationResult result, String name) throws IOException {
        Path output = temporaryDirectory.resolve(name);
        new LigandPdbqtExportOperation().export(result.preparedLigand(), output);
        return Files.readString(output);
    }

    private int torsions(LigandPreparationResult result) {
        return ((LigandFlexibilityModel) result.preparedLigand().attributes()
                .get(LigandFlexibilityModel.ATTRIBUTE_KEY)).torsionalDegreesOfFreedom();
    }

    private List<Atom> atoms(LigandPreparationResult result) {
        return result.preparedLigand().ligand().structure().getChains()
                .getFirst().residues().getFirst().getAtoms();
    }

    private SdfLigand model(String title, String[] symbols, int[][] bonds,
                            String... propertyLines) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append(title).append("\n  unit-test\n\n");
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
        for (String line : propertyLines) {
            text.append(line).append('\n');
        }
        text.append("M  END\n$$$$\n");
        Path path = temporaryDirectory.resolve(title + ".sdf");
        Files.writeString(path, text.toString());
        return reader.readModel(path);
    }

    private static String[] atoms(String... symbols) {
        return symbols;
    }

    private static int[][] bonds(int[]... bonds) {
        return bonds;
    }

    private static int[] bond(int first, int second, int type) {
        return new int[]{first, second, type};
    }
}
