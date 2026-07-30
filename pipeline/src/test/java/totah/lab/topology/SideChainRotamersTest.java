package totah.lab.topology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The chi-bond table is the single source of truth for PDBQT torsion trees:
 * a wrong entry silently produces a wrong flex tree (or a dropped torsion).
 */
public class SideChainRotamersTest {

    private static final Set<String> STANDARD_20 = Set.of(
            "ALA", "ARG", "ASN", "ASP", "CYS", "GLN", "GLU", "GLY", "HIS", "ILE",
            "LEU", "LYS", "MET", "PHE", "PRO", "SER", "THR", "TRP", "TYR", "VAL");

    @Test
    public void allTwentyStandardAminoAcidsResolve() {
        for (String name : STANDARD_20) {
            assertTrue(SideChainRotamers.isStandardAminoAcid(name),
                    "standard amino acid not recognized: " + name);
            assertNotNull(SideChainRotamers.chiBonds(name),
                    "chi bond list is null for " + name);
        }
    }

    @Test
    public void nonStandardResiduesDoNotResolve() {
        for (String name : List.of("MSE", "UNK", "HOH", "LIG", "")) {
            assertFalse(SideChainRotamers.isStandardAminoAcid(name),
                    "non-standard residue recognized as standard: '" + name + "'");
            assertTrue(SideChainRotamers.chiBonds(name).isEmpty(),
                    "non-standard residue has chi bonds: '" + name + "'");
        }
    }

    @Test
    public void lysineHasFourChiBondsInOrder() {
        List<SideChainRotamers.ChiBond> chi = SideChainRotamers.chiBonds("LYS");
        assertEquals(4, chi.size(), "LYS should have 4 chi bonds");
        assertChi(chi.get(0), "CA", "CB");
        assertChi(chi.get(1), "CB", "CG");
        assertChi(chi.get(2), "CG", "CD");
        assertChi(chi.get(3), "CD", "CE");
    }

    @Test
    public void aromaticRingsAreRigidAfterChi1() {
        // PHE/TYR/TRP/HIS side chains beyond CB are ring-locked: only CA-CB rotates
        for (String name : List.of("PHE", "TYR", "TRP", "HIS")) {
            List<SideChainRotamers.ChiBond> chi = SideChainRotamers.chiBonds(name);
            assertEquals(1, chi.size(), name + " ring should be rigid after chi1");
            assertChi(chi.get(0), "CA", "CB");
        }
    }

    @Test
    public void prolineGlycineAlanineHaveNoChiBonds() {
        for (String name : List.of("PRO", "GLY", "ALA")) {
            assertTrue(SideChainRotamers.chiBonds(name).isEmpty(),
                    name + " should have no rotatable chi bonds");
        }
    }

    @Test
    public void chiBondsFormAConnectedChainStartingAtCa() {
        for (String name : STANDARD_20) {
            List<SideChainRotamers.ChiBond> chi = SideChainRotamers.chiBonds(name);
            if (chi.isEmpty()) continue;
            assertEquals("CA", chi.get(0).parent(),
                    name + " chi1 must start at CA");
            for (int i = 1; i < chi.size(); i++) {
                assertEquals(chi.get(i - 1).child(), chi.get(i).parent(),
                        name + " chi bond " + i + " does not continue the chain");
            }
        }
    }

    @Test
    public void chiBondsReferencePlausibleHeavyAtomNames() {
        for (String name : STANDARD_20) {
            for (SideChainRotamers.ChiBond chi : SideChainRotamers.chiBonds(name)) {
                for (String atom : List.of(chi.parent(), chi.child())) {
                    assertTrue(atom.matches("[A-Z][A-Z0-9]{1,2}"),
                            name + " references implausible atom name '" + atom + "'");
                    assertFalse(atom.startsWith("H"),
                            name + " chi bond involves hydrogen '" + atom + "'");
                    assertFalse(Set.of("N", "C", "O", "OXT").contains(atom),
                            name + " chi bond involves backbone atom '" + atom + "'");
                }
            }
        }
    }

    @Test
    public void terminalMethylAndAmineRotationsAreExcluded() {
        // LYS stops at CD-CE (CE-NZ terminal amine excluded), ARG at CD-NE, MET at CG-SD
        assertLastChi("LYS", "CD", "CE");
        assertLastChi("ARG", "CD", "NE");
        assertLastChi("MET", "CG", "SD");
        assertLastChi("GLU", "CG", "CD");
    }

    @Test
    public void chiBondListsAreImmutable() {
        List<SideChainRotamers.ChiBond> chi = SideChainRotamers.chiBonds("LYS");
        assertThrows(UnsupportedOperationException.class,
                () -> chi.add(new SideChainRotamers.ChiBond("X", "Y")),
                "chi bond table must not be mutable");
    }

    private static void assertChi(SideChainRotamers.ChiBond chi, String parent, String child) {
        assertEquals(parent, chi.parent(), "chi bond parent");
        assertEquals(child, chi.child(), "chi bond child");
    }

    private static void assertLastChi(String residue, String parent, String child) {
        List<SideChainRotamers.ChiBond> chi = SideChainRotamers.chiBonds(residue);
        SideChainRotamers.ChiBond last = chi.get(chi.size() - 1);
        assertChi(last, parent, child);
    }
}
