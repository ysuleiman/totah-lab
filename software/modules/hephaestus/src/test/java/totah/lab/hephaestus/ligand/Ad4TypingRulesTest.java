package totah.lab.hephaestus.ligand;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.structure.Atom;
import totah.lab.hephaestus.ligand.operation.SdfLigandTopologyOperation;
import totah.lab.hermes.file.sdf.SdfLigand;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AD4 typing rules vs Meeko's ad4_types.json: aromaticity perception
 * from Kekulé bond tables, donor/acceptor nitrogen, sulfur variants,
 * charged heteroatoms. Each fixture is a minimal V2000 SDF.
 */
class Ad4TypingRulesTest {

    @TempDir
    Path temporaryDirectory;

    private final SdfLigandReader reader = new SdfLigandReader();

    @Test
    void kekuleBenzeneTypesAllCarbonsAromatic() throws Exception {
        // Alternating single/double bonds, no aromatic flags.
        List<String> types = types("KEK",
                atoms("C", "C", "C", "C", "C", "C",
                        "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 2), bond(3, 4, 1),
                        bond(4, 5, 2), bond(5, 6, 1), bond(6, 1, 2),
                        bond(1, 7, 1), bond(2, 8, 1), bond(3, 9, 1),
                        bond(4, 10, 1), bond(5, 11, 1), bond(6, 12, 1)));

        assertEquals(List.of("A", "A", "A", "A", "A", "A",
                "H", "H", "H", "H", "H", "H"), types);
    }

    @Test
    void kekuleNaphthaleneTypesFusedRingsAromatic() throws Exception {
        List<String> types = types("NAP",
                atoms("C", "C", "C", "C", "C", "C", "C", "C", "C", "C",
                        "H", "H", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 2), bond(2, 3, 1), bond(3, 4, 2),
                        bond(4, 9, 1), bond(9, 10, 2), bond(10, 1, 1),
                        bond(5, 6, 2), bond(6, 7, 1), bond(7, 8, 2),
                        bond(8, 9, 1), bond(10, 5, 1),
                        bond(1, 11, 1), bond(2, 12, 1), bond(3, 13, 1),
                        bond(4, 14, 1), bond(5, 15, 1), bond(6, 16, 1),
                        bond(7, 17, 1), bond(8, 18, 1)));

        assertTrue(types.subList(0, 10).stream().allMatch("A"::equals),
                types.toString());
    }

    @Test
    void pyridineNitrogenStaysAnAcceptor() throws Exception {
        List<String> types = types("PYR",
                atoms("C", "C", "C", "C", "C", "N",
                        "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 2), bond(2, 3, 1), bond(3, 4, 2),
                        bond(4, 5, 1), bond(5, 6, 2), bond(6, 1, 1),
                        bond(1, 7, 1), bond(2, 8, 1), bond(3, 9, 1),
                        bond(4, 10, 1), bond(5, 11, 1)));

        assertEquals("NA", types.get(5));
        assertTrue(types.subList(0, 5).stream().allMatch("A"::equals));
    }

    @Test
    void pyrroleNitrogenIsADonor() throws Exception {
        List<String> types = types("PRL",
                atoms("N", "C", "C", "C", "C",
                        "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 2), bond(3, 4, 1),
                        bond(4, 5, 2), bond(5, 1, 1),
                        bond(1, 6, 1), bond(2, 7, 1), bond(3, 8, 1),
                        bond(4, 9, 1), bond(5, 10, 1)));

        assertEquals("N", types.get(0));
        assertTrue(types.subList(1, 5).stream().allMatch("A"::equals));
        assertEquals("HD", types.get(5));
    }

    @Test
    void anilineNitrogenIsADonor() throws Exception {
        List<String> types = types("ANI",
                atoms("C", "C", "C", "C", "C", "C", "N",
                        "H", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 2), bond(3, 4, 1),
                        bond(4, 5, 2), bond(5, 6, 1), bond(6, 1, 2),
                        bond(1, 7, 1),
                        bond(7, 8, 1), bond(7, 9, 1),
                        bond(2, 10, 1), bond(3, 11, 1), bond(4, 12, 1),
                        bond(5, 13, 1), bond(6, 14, 1)));

        assertEquals("N", types.get(6));
    }

    @Test
    void amideNitrogenIsADonor() throws Exception {
        List<String> types = types("ACE",
                atoms("C", "C", "O", "N", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 2), bond(2, 4, 1),
                        bond(4, 5, 1), bond(4, 6, 1),
                        bond(1, 7, 1), bond(1, 8, 1), bond(1, 9, 1)));

        assertEquals("N", types.get(3));
        assertEquals("OA", types.get(2));
        assertEquals("HD", types.get(4));
    }

    @Test
    void aliphaticAmineStaysAnAcceptor() throws Exception {
        List<String> types = types("DMA",
                atoms("N", "C", "C", "H", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(1, 3, 1), bond(1, 4, 1),
                        bond(2, 5, 1), bond(2, 6, 1), bond(2, 7, 1),
                        bond(3, 8, 1), bond(3, 9, 1), bond(3, 10, 1)));

        assertEquals("NA", types.get(0));
    }

    @Test
    void chargedAmmoniumIsADonor() throws Exception {
        List<String> types = types("AMM",
                atoms("N", "C", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(1, 3, 1), bond(1, 4, 1),
                        bond(1, 5, 1), bond(2, 6, 1)),
                "M  CHG  1   1   1");

        assertEquals("N", types.get(0));
    }

    @Test
    void sulfurVariantsFollowMeeko() throws Exception {
        // Thioether: aliphatic two-connected sulfur -> SA.
        assertEquals("SA", types("DMS",
                atoms("C", "S", "C", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 1),
                        bond(1, 4, 1), bond(1, 5, 1), bond(1, 6, 1),
                        bond(3, 7, 1), bond(3, 8, 1), bond(3, 9, 1)))
                .get(1));
        // Sulfoxide: three-connected -> S.
        assertEquals("S", types("DSO",
                atoms("C", "S", "C", "O", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 1), bond(2, 4, 2),
                        bond(1, 5, 1), bond(1, 6, 1), bond(1, 7, 1),
                        bond(3, 8, 1), bond(3, 9, 1), bond(3, 10, 1)))
                .get(1));
        // Disulfide: two-connected aliphatic -> SA (Meeko's [SX2]).
        assertEquals("SA", types("DMD",
                atoms("C", "S", "S", "C", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 1), bond(3, 4, 1),
                        bond(1, 5, 1), bond(1, 6, 1), bond(1, 7, 1),
                        bond(4, 8, 1), bond(4, 9, 1), bond(4, 10, 1)))
                .get(1));
    }

    @Test
    void thiopheneSulfurIsNotAnAcceptor() throws Exception {
        List<String> types = types("THI",
                atoms("S", "C", "C", "C", "C", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 2), bond(3, 4, 1),
                        bond(4, 5, 2), bond(5, 1, 1),
                        bond(2, 6, 1), bond(3, 7, 1), bond(4, 8, 1),
                        bond(5, 9, 1)));

        assertEquals("S", types.get(0));
        assertTrue(types.subList(1, 5).stream().allMatch("A"::equals));
    }

    @Test
    void cyclopentadieneIsNotAromatic() throws Exception {
        // Two doubles but the CH2 carbon carries no lone pair.
        List<String> types = types("CPD",
                atoms("C", "C", "C", "C", "C",
                        "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 2), bond(2, 3, 1), bond(3, 4, 2),
                        bond(4, 5, 1), bond(5, 1, 1),
                        bond(1, 6, 1), bond(2, 7, 1), bond(3, 8, 1),
                        bond(4, 9, 1), bond(5, 10, 1), bond(5, 11, 1)));

        assertTrue(types.subList(0, 5).stream().allMatch("C"::equals),
                types.toString());
    }

    @Test
    void kekuleQuinolinePerceivesTheNitrogenRing() throws Exception {
        // Fused 6,6 system with a shared single bond: the N ring holds
        // only two in-ring doubles, so per-ring rules miss it; pi
        // counting over the fused system does not.
        List<String> types = types("QUI",
                atoms("N", "C", "C", "C", "C", "C", "C", "C", "C", "C",
                        "H", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 2), bond(2, 3, 1), bond(3, 4, 2),
                        bond(4, 10, 1), bond(10, 5, 2), bond(5, 6, 1),
                        bond(6, 7, 2), bond(7, 8, 1), bond(8, 9, 2),
                        bond(9, 10, 1), bond(9, 1, 1),
                        bond(2, 11, 1), bond(3, 12, 1), bond(4, 13, 1),
                        bond(5, 14, 1), bond(6, 15, 1), bond(7, 16, 1),
                        bond(8, 17, 1)));

        assertTrue(types.subList(1, 10).stream().allMatch("A"::equals),
                types.toString());
        assertEquals("NA", types.get(0));
    }

    @Test
    void tetralinKeepsOnlyTheAromaticRing() throws Exception {
        // Benzene fused to cyclohexane (RDKit Kekulé: fusion bond
        // single): the aromatic ring must be perceived, the aliphatic
        // ring must not poison it.
        List<String> types = types("TET",
                atoms("C", "C", "C", "C", "C", "C", "C", "C", "C", "C",
                        "H", "H", "H", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 2), bond(2, 3, 1), bond(3, 4, 2),
                        bond(4, 5, 1), bond(5, 6, 2), bond(6, 1, 1),
                        bond(4, 7, 1), bond(7, 8, 1), bond(8, 9, 1),
                        bond(9, 10, 1), bond(10, 3, 1),
                        bond(1, 11, 1), bond(2, 12, 1), bond(5, 13, 1),
                        bond(6, 14, 1), bond(7, 15, 1), bond(8, 16, 1),
                        bond(9, 17, 1), bond(10, 18, 1)));

        assertTrue(types.subList(0, 6).stream().allMatch("A"::equals),
                types.toString());
        assertTrue(types.subList(6, 10).stream().allMatch("C"::equals),
                types.toString());
    }

    @Test
    void coumarinPyroneRingIsAromatic() throws Exception {
        // RDKit's Kekulé coumarin: pyrone ring holds a single in-ring
        // double plus the ring-oxygen donor and two fusion atoms.
        List<String> types = types("COU",
                atoms("O", "C", "C", "C", "C", "C", "C", "C", "C", "C",
                        "O", "H", "H", "H", "H", "H"),
                bonds(bond(1, 2, 1), bond(2, 3, 1), bond(3, 4, 2),
                        bond(4, 5, 1), bond(5, 6, 2), bond(6, 7, 1),
                        bond(7, 8, 2), bond(8, 9, 1), bond(9, 10, 2),
                        bond(10, 1, 1), bond(8, 3, 1), bond(2, 11, 2),
                        bond(4, 12, 1), bond(5, 13, 1), bond(6, 14, 1),
                        bond(7, 15, 1), bond(9, 16, 1)));

        assertEquals("OA", types.get(0));
        assertTrue(types.subList(1, 10).stream().allMatch("A"::equals),
                types.toString());
    }

    @Test
    void hydrogenOnFluorineOrPhosphorusIsPolar() throws Exception {
        assertEquals(List.of("F", "HD"), types("HF",
                atoms("F", "H"), bonds(bond(1, 2, 1))));
    }

    private List<String> types(String title, String[] symbols,
                               int[][] bonds, String... propertyLines)
            throws IOException {
        StringBuilder text = new StringBuilder();
        text.append(title).append("\n  unit-test\n\n");
        text.append(String.format(Locale.US,
                "%3d%3d  0  0  0  0  0  0  0  0999 V2000",
                symbols.length, bonds.length)).append('\n');
        for (int index = 0; index < symbols.length; index++) {
            text.append(String.format(Locale.US,
                    "%10.4f%10.4f%10.4f %-3s 0  0  0  0  0  0",
                    index * 1.5, (index % 3) * 1.5, (index % 2) * 1.5,
                    symbols[index])).append('\n');
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
        SdfLigand model = reader.readModel(path);

        LigandPreparationResult result = DefaultLigandPreparer.sdf(model)
                .prepare(new LigandPreparationRequest(model.ligand()));
        assertTrue(result.successful(),
                () -> result.issues().toString());
        return result.preparedLigand().ligand().structure().getChains()
                .getFirst().residues().getFirst().getAtoms().stream()
                .map(Atom::getAutoDockType)
                .toList();
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
