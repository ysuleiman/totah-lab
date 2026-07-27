package totah.lab.topology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Amber template library supplies per-atom charges/types at load time and
 * is the reference bond set for validating the distance-based topology.
 * Templates load from /amber/lib on the classpath (main resources).
 */
public class AmberResidueTemplateLibraryTest {

    private final AmberResidueTemplateLibrary library = AmberResidueTemplateLibrary.getInstance();

    @Test
    public void templatesExistForAllStandardResidues() {
        for (String name : List.of("ALA", "ARG", "ASN", "ASP", "CYS", "GLN", "GLU",
                "GLY", "HIS", "ILE", "LEU", "LYS", "MET", "PHE", "PRO", "SER",
                "THR", "TRP", "TYR", "VAL")) {
            // HIS ships as HID/HIE/HIP in the Amber libs, plain HIS may be absent
            if (name.equals("HIS")) continue;
            assertNotNull(library.getTemplate(name), "no Amber template for " + name);
        }
        assertNotNull(library.getTemplate("HID"), "no Amber template for HID");
    }

    @Test
    public void lysTemplateCarriesFullSideChainBondSet() {
        ResidueTemplate lys = library.getTemplate("LYS");
        assertNotNull(lys, "LYS template missing");
        // LYS is defined in both all_amino94.lib and amino12.lib; the library
        // keeps the latest definition instead of appending, so exactly 22 atoms
        assertEquals(22, lys.getAtoms().size(),
                "LYS should define exactly 22 atoms (duplicate definitions replaced, not appended)");

        assertBond(lys, "N", "CA");
        assertBond(lys, "CA", "C");
        assertBond(lys, "C", "O");
        assertBond(lys, "CA", "CB");
        assertBond(lys, "CB", "CG");
        assertBond(lys, "CG", "CD");
        assertBond(lys, "CD", "CE");
        assertBond(lys, "CE", "NZ");
    }

    @Test
    public void lysTemplateChargesMatchPublishedAmberValues() {
        ResidueTemplate lys = library.getTemplate("LYS");
        assertEquals(-0.3479, lys.getAtom("N").getCharge(), 1e-4, "LYS N charge");
        assertEquals(-0.5894, lys.getAtom("O").getCharge(), 1e-4, "LYS O charge");
        assertEquals("N", lys.getAtom("N").getAmberType(), "LYS N amber type");
        assertEquals("O", lys.getAtom("O").getAmberType(), "LYS O amber type");
    }

    @Test
    public void pheTemplateClosesTheAromaticRing() {
        ResidueTemplate phe = library.getTemplate("PHE");
        assertNotNull(phe, "PHE template missing");
        assertBond(phe, "CB", "CG");
        assertBond(phe, "CG", "CD1");
        assertBond(phe, "CD1", "CE1");
        assertBond(phe, "CE1", "CZ");
        assertBond(phe, "CZ", "CE2");
        assertBond(phe, "CE2", "CD2");
        assertBond(phe, "CD2", "CG");
    }

    @Test
    public void tysPrepiTemplatesLoadWithRespChargesAndSulfateConnectivity() {
        ResidueTemplate tys = library.getTemplate("TYS");
        ResidueTemplate ntys = library.getTemplate("NTYS");
        ResidueTemplate ctys = library.getTemplate("CTYS");
        assertNotNull(tys, "TYS PREPI template missing");
        assertNotNull(ntys, "NTYS PREPI template missing");
        assertNotNull(ctys, "CTYS PREPI template missing");

        assertEquals(24, tys.getAtoms().size(), "TYS should define 24 non-dummy atoms");
        assertEquals(-1.0, totalCharge(tys), 1e-4, "mid-chain TYS total charge");
        assertEquals(0.0, totalCharge(ntys), 1e-4, "N-terminal NTYS total charge");
        assertEquals(-2.0, totalCharge(ctys), 1e-4, "C-terminal CTYS total charge");

        assertEquals("SO", tys.getAtom("S").getAmberType(), "TYS sulfate sulfur type");
        assertEquals(1.2494, tys.getAtom("S").getCharge(), 1e-4, "TYS sulfate sulfur RESP charge");
        assertEquals("O2", ctys.getAtom("OXT").getAmberType(), "CTYS terminal OXT type");
        assertBond(tys, "CZ", "OH");
        assertBond(tys, "OH", "S");
        assertBond(tys, "S", "O1");
        assertBond(tys, "S", "O2");
        assertBond(tys, "S", "O3");
        assertBond(tys, "CD2", "CG");
    }

    @Test
    public void unknownResidueHasNoTemplate() {
        assertNull(library.getTemplate("NOTARES"),
                "unknown residue names must not resolve to a template");
    }

    @Test
    public void rejectsTysPrepiWhenPublishedChargeIsCorrupted(@TempDir Path tempDir)
            throws Exception {
        String prepi = readResource("/amber/prep/tys/MTYS.prepi")
                .replace("1.249400", "1.300000");
        Path file = tempDir.resolve("MTYS.prepi");
        Files.writeString(file, prepi, StandardCharsets.UTF_8);

        AmberResidueTemplateLibrary isolated = newIsolatedLibrary();
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> isolated.load(file));
        assertTrue(error.getMessage().startsWith("TYS charge must be -1.0, found:"),
                "unexpected validation error: " + error.getMessage());
        assertNull(isolated.getTemplate("TYS"), "invalid TYS template must not be registered");
    }

    @Test
    public void templateBondsAreNotDuplicated() {
        ResidueTemplate lys = library.getTemplate("LYS");
        Set<String> seen = new HashSet<>();
        for (BondTemplate bond : lys.getBonds()) {
            String key = bond.getAtom1().compareTo(bond.getAtom2()) < 0
                    ? bond.getAtom1() + "-" + bond.getAtom2()
                    : bond.getAtom2() + "-" + bond.getAtom1();
            assertTrue(seen.add(key), "duplicate LYS template bond: " + key);
        }
    }

    private static void assertBond(ResidueTemplate template, String a, String b) {
        boolean found = template.getBonds().stream().anyMatch(bond ->
                (bond.getAtom1().equals(a) && bond.getAtom2().equals(b))
                        || (bond.getAtom1().equals(b) && bond.getAtom2().equals(a)));
        assertTrue(found, "template " + template.getName() + " lacks bond " + a + "-" + b);
    }

    private static double totalCharge(ResidueTemplate template) {
        return template.getAtoms().stream()
                .mapToDouble(AtomTemplate::getCharge)
                .sum();
    }

    private static String readResource(String name) throws IOException {
        try (InputStream in = AmberResidueTemplateLibraryTest.class.getResourceAsStream(name)) {
            return new String(Objects.requireNonNull(in, "missing resource " + name).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }

    private static AmberResidueTemplateLibrary newIsolatedLibrary() throws ReflectiveOperationException {
        Constructor<AmberResidueTemplateLibrary> constructor =
                AmberResidueTemplateLibrary.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
