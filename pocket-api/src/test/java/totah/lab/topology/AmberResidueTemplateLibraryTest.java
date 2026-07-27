package totah.lab.topology;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
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
    public void unknownResidueHasNoTemplate() {
        assertNull(library.getTemplate("NOTARES"),
                "unknown residue names must not resolve to a template");
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
}
