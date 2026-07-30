package totah.lab.topology;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import totah.lab.protein.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HydrogenOptimizer — second-pass rotamer / flip optimization.
 *
 * <p>Prerequisite: residues must already contain hydrogens from the first
 * geometric pass (ReceptorHydrogenationStage / ResidueHydrogenator).
 * The optimizer only repositions or removes existing H's; it does not
 * add missing ones.
 */
class HydrogenOptimizerTest {

    private HydrogenOptimizer optimizer;
    private List<Residue> environment;

    @BeforeEach
    void setUp() {
        ResidueTemplateProvider mockLib = MockAmberDatabase.createMockLibrary();
        AmberParameterSet mockLJ = MockAmberDatabase.createMockLJParams();
        optimizer = new HydrogenOptimizer(mockLib, mockLJ, 1.0);
        environment = new ArrayList<>();
    }

    // ==================== HELPERS ====================

    private Atom atom(String name, String symbol, double x, double y, double z) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(x, y, z))
                .bFactor(15.0)
                .element(Element.fromSymbol(symbol))
                .build();
    }

    private Residue residue(String name, int number, Atom... atoms) {
        return Residue.builder()
                .name(name).chain("A").number(number)
                .atoms(new ArrayList<>(List.of(atoms)))
                .build();
    }

    private Atom find(List<Atom> atoms, String name) {
        return atoms.stream().filter(a -> a.getName().equals(name)).findFirst().orElse(null);
    }

    private boolean has(List<Atom> atoms, String name) {
        return atoms.stream().anyMatch(a -> a.getName().equals(name));
    }

    private double dist(Atom a, Atom b) {
        Point3D p1 = a.getPosition();
        Point3D p2 = b.getPosition();
        double dx = p1.x() - p2.x();
        double dy = p1.y() - p2.y();
        double dz = p1.z() - p2.z();
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private double angleAt(Point3D a, Point3D b, Point3D c) {
        // angle ABC (vertex at b)
        double bax = a.x() - b.x(), bay = a.y() - b.y(), baz = a.z() - b.z();
        double bcx = c.x() - b.x(), bcy = c.y() - b.y(), bcz = c.z() - b.z();
        double lenBA = Math.sqrt(bax*bax + bay*bay + baz*baz);
        double lenBC = Math.sqrt(bcx*bcx + bcy*bcy + bcz*bcz);
        if (lenBA < 1e-12 || lenBC < 1e-12) return 0.0;
        double dot = bax*bcx + bay*bcy + baz*bcz;
        double cos = dot / (lenBA * lenBC);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    // ==================== ASN FLIP ====================

    @Test
    @DisplayName("ASN flips when OD1 clashes with external negative oxygen")
    void testAsnFlipClash() {
        // Pre-hydrogenated ASN: default orientation, ND2 on left with its H's
        Residue asn = residue("ASN", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("OD1", "O", 1.2, 3.0, 0.0),
                atom("ND2", "N", -1.2, 3.0, 0.0),
                atom("HD21", "H", -1.5, 3.5, 0.0),
                atom("HD22", "H", -1.5, 2.5, 0.0)
        );
        // External O near OD1 (right side) → repulsion should drive flip
        Residue env = residue("GLY", 2, atom("O", "O", 1.5, 3.0, 0.0));
        environment.add(asn);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(asn, environment);

        Atom od1 = find(opt, "OD1");
        Atom nd2 = find(opt, "ND2");
        assertNotNull(od1);
        assertNotNull(nd2);
        // After flip, OD1 should be on the left (-1.2)
        assertEquals(-1.2, od1.getPosition().x(), 1e-3, "OD1 must flip to left");
        assertEquals(1.2, nd2.getPosition().x(), 1e-3, "ND2 must flip to right");
        assertTrue(has(opt, "HD21"), "HD21 must exist on flipped ND2");
        assertTrue(has(opt, "HD22"), "HD22 must exist on flipped ND2");
    }

    @Test
    @DisplayName("ASN does NOT flip when unflipped is electrostatically favored")
    void testAsnNoFlipFavorable() {
        Residue asn = residue("ASN", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("OD1", "O", 1.2, 3.0, 0.0),
                atom("ND2", "N", -1.2, 3.0, 0.0),
                atom("HD21", "H", -1.5, 3.5, 0.0),
                atom("HD22", "H", -1.5, 2.5, 0.0)
        );
        // Positive Lys NZ near ND2 (left side) → keeps ND2 on left
        Residue env = residue("LYS", 2, atom("NZ", "N", -1.5, 3.0, 0.0));
        environment.add(asn);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(asn, environment);

        Atom od1 = find(opt, "OD1");
        Atom nd2 = find(opt, "ND2");
        assertNotNull(od1);
        assertNotNull(nd2);
        assertEquals(1.2, od1.getPosition().x(), 1e-3, "OD1 should stay on right");
        assertEquals(-1.2, nd2.getPosition().x(), 1e-3, "ND2 should stay on left");
    }

    @Test
    @DisplayName("ASN picks best orientation when both sides have clashes")
    void testAsnBothOrientationsClash() {
        Residue asn = residue("ASN", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("OD1", "O", 1.2, 3.0, 0.0),
                atom("ND2", "N", -1.2, 3.0, 0.0),
                atom("HD21", "H", -1.5, 3.5, 0.0),
                atom("HD22", "H", -1.5, 2.5, 0.0)
        );
        // Clash on both sides
        Residue env1 = residue("GLY", 2, atom("O", "O", 1.5, 3.0, 0.0));
        Residue env2 = residue("GLY", 3, atom("O", "O", -1.5, 3.0, 0.0));
        environment.add(asn);
        environment.add(env1);
        environment.add(env2);

        List<Atom> opt = optimizer.optimize(asn, environment);

        assertNotNull(find(opt, "OD1"));
        assertNotNull(find(opt, "ND2"));
        assertTrue(has(opt, "HD21") || has(opt, "HD22"), "Should have at least one amide H");
    }

    // ==================== GLN FLIP ====================

    @Test
    @DisplayName("GLN flips when OE1 clashes with external oxygen")
    void testGlnFlipClash() {
        Residue gln = residue("GLN", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("CD",  "C", 0.0, 3.5, 0.0),
                atom("OE1", "O", 1.2, 4.0, 0.0),
                atom("NE2", "N", -1.2, 4.0, 0.0),
                atom("HE21", "H", -1.5, 4.5, 0.0),
                atom("HE22", "H", -1.5, 3.5, 0.0)
        );
        Residue env = residue("GLY", 2, atom("O", "O", 1.5, 4.0, 0.0));
        environment.add(gln);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(gln, environment);

        Atom oe1 = find(opt, "OE1");
        Atom ne2 = find(opt, "NE2");
        assertNotNull(oe1);
        assertNotNull(ne2);
        assertEquals(-1.2, oe1.getPosition().x(), 1e-3, "OE1 must flip to left");
        assertEquals(1.2, ne2.getPosition().x(), 1e-3, "NE2 must flip to right");
        assertTrue(has(opt, "HE21"), "HE21 must exist");
        assertTrue(has(opt, "HE22"), "HE22 must exist");
    }

    // ==================== HIS TAUTOMER + RING FLIP ====================

    @Test
    @DisplayName("HIS selects HIE when NE2 can H-bond to an acceptor")
    void testHisSelectsHIE() {
        // Start as HID (HD1 present, no HE2); optimizer should switch to HIE
        Residue his = residue("HIS", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("ND1", "N", -0.5, 3.0, 0.0),
                atom("CD2", "C", 0.0, 3.5, 0.0),
                atom("CE1", "C", -0.5, 3.5, 0.0),
                atom("NE2", "N", 0.5, 3.0, 0.0),
                atom("HD1", "H", -0.8, 2.5, 0.0)   // HID state
        );
        // Acceptor near NE2 side → HIE favored
        Residue env = residue("ASP", 2, atom("OD1", "O", 1.5, 3.0, 0.0));
        environment.add(his);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(his, environment);

        assertFalse(has(opt, "HD1"), "Should remove HD1 when switching to HIE");
        assertTrue(has(opt, "HE2"), "Should select HIE (HE2 present)");
    }

    @Test
    @DisplayName("HIS selects HID when ND1 can H-bond to an acceptor")
    void testHisSelectsHID() {
        // Start as HIE (HE2 present, no HD1); optimizer should switch to HID
        Residue his = residue("HIS", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("ND1", "N", -0.5, 3.0, 0.0),
                atom("CD2", "C", 0.0, 3.5, 0.0),
                atom("CE1", "C", -0.5, 3.5, 0.0),
                atom("NE2", "N", 0.5, 3.0, 0.0),
                atom("HE2", "H", 0.8, 3.5, 0.0)   // HIE state
        );
        // Acceptor near ND1 (left side) → HID favored
        Residue env = residue("ASP", 2, atom("OD1", "O", -1.5, 3.0, 0.0));
        environment.add(his);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(his, environment);

        assertFalse(has(opt, "HE2"), "Should remove HE2 when switching to HID");
        assertTrue(has(opt, "HD1"), "Should select HID (HD1 present)");
    }

    @Test
    @DisplayName("HIS ring flips when flipped orientation has better H-bonds")
    void testHisRingFlip() {
        Residue his = residue("HIS", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("ND1", "N", -0.5, 3.0, 0.0),
                atom("CD2", "C", 0.0, 3.5, 0.0),
                atom("CE1", "C", -0.5, 3.5, 0.0),
                atom("NE2", "N", 0.5, 3.0, 0.0),
                atom("HE2", "H", 0.8, 3.5, 0.0)   // default HIE
        );
        // Acceptor near where CE1 would be after flip
        Residue env = residue("ASP", 2, atom("OD2", "O", 0.5, 4.0, 0.0));
        environment.add(his);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(his, environment);

        // Should have some His H present (either HD1 or HE2)
        assertTrue(has(opt, "HD1") || has(opt, "HE2"), "Must have at least one His H");
    }

    // ==================== HYDROXYL ROTATION ====================

    @Test
    @DisplayName("SER rotates OH toward a nearby hydrogen-bond acceptor")
    void testSerineRotationTowardAcceptor() {
        Residue ser = residue("SER", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("OG", "O", 0.0, 2.5, 0.0),
                atom("HG", "H", 0.5, 2.8, 0.0)   // default position, not optimal
        );
        // Acceptor above OG
        Residue env = residue("ASP", 2, atom("OD1", "O", 0.0, 5.0, 0.0));
        environment.add(ser);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(ser, environment);

        Atom hg = find(opt, "HG");
        assertNotNull(hg, "HG must remain");
        assertTrue(hg.getPosition().y() > 2.5,
                "HG should point upward toward acceptor");
    }

    @Test
    @DisplayName("THR rotates OG1 toward a nearby acceptor")
    void testThreonineRotation() {
        Residue thr = residue("THR", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("OG1", "O", 0.0, 2.5, 0.0),
                atom("CG2", "C", 1.5, 1.5, 0.0),
                atom("HG1", "H", 0.5, 2.8, 0.0)   // default
        );
        Residue env = residue("GLU", 2, atom("OE1", "O", 0.0, 5.0, 0.0));
        environment.add(thr);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(thr, environment);

        Atom hg1 = find(opt, "HG1");
        assertNotNull(hg1, "HG1 must remain");
        assertTrue(hg1.getPosition().y() > 2.5,
                "HG1 should point toward acceptor");
    }

    @Test
    @DisplayName("TYR rotates OH toward a nearby acceptor")
    void testTyrosineRotation() {
        Residue tyr = residue("TYR", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("CD1", "C", -1.0, 3.0, 0.0),
                atom("CD2", "C", 1.0, 3.0, 0.0),
                atom("CE1", "C", -1.0, 4.0, 0.0),
                atom("CE2", "C", 1.0, 4.0, 0.0),
                atom("CZ",  "C", 0.0, 4.5, 0.0),
                atom("OH",  "O", 0.0, 5.5, 0.0),
                atom("HH",  "H", 0.5, 5.8, 0.0)   // default
        );
        Residue env = residue("ASP", 2, atom("OD2", "O", 0.0, 7.0, 0.0));
        environment.add(tyr);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(tyr, environment);

        Atom hh = find(opt, "HH");
        assertNotNull(hh, "HH must remain");
        assertTrue(hh.getPosition().y() > 5.5,
                "HH should point away from ring toward acceptor");
    }

    // ==================== CYS (THIOL + DISULFIDE) ====================

    @Test
    @DisplayName("CYS rotates HG toward acceptor when not in disulfide")
    void testCysThiolRotation() {
        Residue cys = residue("CYS", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("SG", "S", 0.0, 2.5, 0.0),
                atom("HG", "H", 0.5, 2.8, 0.0)   // default
        );
        Residue env = residue("ASP", 2, atom("OD1", "O", 0.0, 4.5, 0.0));
        environment.add(cys);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(cys, environment);

        Atom hg = find(opt, "HG");
        assertNotNull(hg, "HG must remain on free CYS");
        assertTrue(hg.getPosition().y() > 2.5, "HG should point toward acceptor");
    }

    @Test
    @DisplayName("CYS in disulfide has HG removed by optimizer")
    void testCysDisulfideNoHG() {
        Residue cys1 = residue("CYS", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("SG", "S", 0.0, 2.5, 0.0),
                atom("HG", "H", 0.5, 2.8, 0.0)   // should be removed
        );
        // Partner CYS with SG at 2.0 Å (disulfide range)
        Residue cys2 = residue("CYS", 2,
                atom("CA", "C", 0.0, 0.0, 2.0),
                atom("CB", "C", 0.0, 1.5, 2.0),
                atom("SG", "S", 0.0, 2.5, 2.0)
        );
        environment.add(cys1);
        environment.add(cys2);

        List<Atom> opt = optimizer.optimize(cys1, environment);

        assertFalse(has(opt, "HG"), "Disulfide CYS must not have HG");
    }

    // ==================== LYS / ARG / TRP ====================

    @Test
    @DisplayName("LYS ammonium HZ points toward acceptor")
    void testLysAmmoniumRotation() {
        Residue lys = residue("LYS", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("CG", "C", 0.0, 2.5, 0.0),
                atom("CD", "C", 0.0, 3.5, 0.0),
                atom("CE", "C", 0.0, 4.5, 0.0),
                atom("NZ", "N", 0.0, 5.5, 0.0),
                atom("HZ1", "H", 0.5, 5.8, 0.0),
                atom("HZ2", "H", -0.5, 5.8, 0.0),
                atom("HZ3", "H", 0.0, 5.8, 0.5)
        );
        Residue env = residue("ASP", 2, atom("OD1", "O", 0.0, 7.5, 0.0));
        environment.add(lys);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(lys, environment);

        assertTrue(has(opt, "HZ1"), "HZ1 must remain");
        assertTrue(has(opt, "HZ2"), "HZ2 must remain");
        assertTrue(has(opt, "HZ3"), "HZ3 must remain");

        boolean anyUp = opt.stream()
                .filter(a -> a.getName().startsWith("HZ"))
                .anyMatch(a -> a.getPosition().y() > 5.5);
        assertTrue(anyUp, "At least one HZ should point toward acceptor");
    }

    @Test
    @DisplayName("ARG guanidinium HE and HHs are preserved")
    void testArgGuanidinium() {
        Residue arg = residue("ARG", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("CD",  "C", 0.0, 3.5, 0.0),
                atom("NE",  "N", 0.0, 4.5, 0.0),
                atom("CZ",  "C", 0.0, 5.5, 0.0),
                atom("NH1", "N", -1.0, 6.0, 0.0),
                atom("NH2", "N", 1.0, 6.0, 0.0),
                atom("HE",  "H", 0.0, 4.0, 0.0),
                atom("HH11", "H", -1.5, 6.5, 0.0),
                atom("HH12", "H", -0.5, 6.5, 0.0),
                atom("HH21", "H", 1.5, 6.5, 0.0),
                atom("HH22", "H", 0.5, 6.5, 0.0)
        );
        environment.add(arg);

        List<Atom> opt = optimizer.optimize(arg, environment);

        assertTrue(has(opt, "HE"), "HE must remain");
        assertTrue(has(opt, "HH11"), "HH11 must remain");
        assertTrue(has(opt, "HH12"), "HH12 must remain");
        assertTrue(has(opt, "HH21"), "HH21 must remain");
        assertTrue(has(opt, "HH22"), "HH22 must remain");
    }

    @Test
    @DisplayName("TRP indole HE1 is preserved")
    void testTrpIndoleNH() {
        Residue trp = residue("TRP", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("CD1", "C", -1.0, 3.0, 0.0),
                atom("CD2", "C", 1.0, 3.0, 0.0),
                atom("NE1", "N", -0.5, 3.5, 0.0),
                atom("CE2", "C", 0.5, 3.5, 0.0),
                atom("CE3", "C", 1.5, 3.5, 0.0),
                atom("CZ2", "C", 0.5, 4.5, 0.0),
                atom("CZ3", "C", 1.5, 4.5, 0.0),
                atom("CH2", "C", 1.0, 5.0, 0.0),
                atom("HE1", "H", -0.5, 3.0, 0.0)   // on NE1
        );
        environment.add(trp);

        List<Atom> opt = optimizer.optimize(trp, environment);

        assertTrue(has(opt, "HE1"), "HE1 must remain on NE1");
    }

    // ==================== METAL COORDINATION GUARD ====================

    @Test
    @DisplayName("SER near metal has HG removed by optimizer")
    void testMetalGuardSer() {
        Residue ser = residue("SER", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("OG", "O", 0.0, 2.5, 0.0),
                atom("HG", "H", 0.5, 2.8, 0.0)   // should be removed near metal
        );
        // Zn at 2.0 Å from OG (within 4 Å metal cutoff)
        Residue env = residue("ZN", 2, atom("ZN", "ZN", 0.0, 2.5, 2.0));
        environment.add(ser);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(ser, environment);

        assertFalse(has(opt, "HG"), "HG must be removed near metal");
    }

    // ==================== ACIDIC RESIDUES (LOW pH) ====================

    @Test
    @DisplayName("ASP at low pH rotates HD2 toward acceptor")
    void testAspProtonatedRotation() {
        Residue asp = residue("ASP", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("OD1", "O", -1.0, 3.0, 0.0),
                atom("OD2", "O", 1.0, 3.0, 0.0),
                atom("HD2", "H", 1.5, 3.5, 0.0)   // proton on OD2
        );
        Residue env = residue("GLU", 2, atom("OE1", "O", 1.0, 5.0, 0.0));
        environment.add(asp);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(asp, environment);

        Atom hd2 = find(opt, "HD2");
        assertNotNull(hd2, "HD2 must remain on protonated ASP");
        assertTrue(hd2.getPosition().y() > 2.5, "HD2 should point toward acceptor");
    }

    @Test
    @DisplayName("GLU at low pH rotates HE2 toward acceptor")
    void testGluProtonatedRotation() {
        Residue glu = residue("GLU", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("CD",  "C", 0.0, 3.5, 0.0),
                atom("OE1", "O", -1.0, 4.0, 0.0),
                atom("OE2", "O", 1.0, 4.0, 0.0),
                atom("HE2", "H", 1.5, 4.5, 0.0)   // proton on OE2
        );
        Residue env = residue("ASP", 2, atom("OD1", "O", 1.0, 6.0, 0.0));
        environment.add(glu);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(glu, environment);

        Atom he2 = find(opt, "HE2");
        assertNotNull(he2, "HE2 must remain on protonated GLU");
        assertTrue(he2.getPosition().y() > 3.5, "HE2 should point toward acceptor");
    }

    // ==================== H-BOND GEOMETRY ====================

    @Test
    @DisplayName("H-bond donor angle is within acceptable range for SER")
    void testSerHbAngleQuality() {
        Residue ser = residue("SER", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("OG", "O", 0.0, 2.5, 0.0),
                atom("HG", "H", 0.5, 2.8, 0.0)   // suboptimal start
        );
        // Acceptor OFF the OG-CB bond axis: an on-axis acceptor makes the
        // OG-HG...OD1 angle invariant (~86 deg) for every rotamer, because the
        // hydroxyl H rotates on a fixed 108.5 deg cone around the CB-OG bond.
        Residue env = residue("ASP", 2, atom("OD1", "O", 1.5, 4.5, 0.0));
        environment.add(ser);
        environment.add(env);

        List<Atom> opt = optimizer.optimize(ser, environment);

        Atom hg = find(opt, "HG");
        Atom og = find(opt, "OG");
        Atom acc = find(env.getAtoms(), "OD1");
        assertNotNull(hg);
        assertNotNull(og);
        assertNotNull(acc);

        double donorAngle = angleAt(og.getPosition(), hg.getPosition(), acc.getPosition());
        assertTrue(donorAngle > 120.0,
                "H-bond donor angle OG-HG...OD1 should be > 120°, was " + donorAngle);
    }

    // ==================== METHYL PASS-THROUGH ====================

    @Test
    @DisplayName("ALA passes through without modification")
    void testAlaPassThrough() {
        Residue ala = residue("ALA", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0)
        );
        environment.add(ala);

        List<Atom> opt = optimizer.optimize(ala, environment);

        assertEquals(2, opt.size(), "ALA should not add or remove atoms");
        assertEquals("CA", opt.get(0).getName());
        assertEquals("CB", opt.get(1).getName());
    }

    @Test
    @DisplayName("VAL passes through without modification")
    void testValPassThrough() {
        Residue val = residue("VAL", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG1", "C", 1.0, 2.0, 0.0),
                atom("CG2", "C", -1.0, 2.0, 0.0)
        );
        environment.add(val);

        List<Atom> opt = optimizer.optimize(val, environment);

        assertEquals(4, opt.size(), "VAL should not add or remove atoms");
    }

    @Test
    @DisplayName("MET passes through without modification")
    void testMetPassThrough() {
        Residue met = residue("MET", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("CG", "C", 0.0, 2.5, 0.0),
                atom("SD", "S", 0.0, 3.5, 0.0),
                atom("CE", "C", 0.0, 4.5, 0.0)
        );
        environment.add(met);

        List<Atom> opt = optimizer.optimize(met, environment);

        assertEquals(5, opt.size(), "MET should not add or remove atoms");
    }

    @Test
    @DisplayName("MSE is treated as MET and passes through")
    void testMseAsMet() {
        Residue mse = residue("MSE", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("CG", "C", 0.0, 2.5, 0.0),
                atom("SE", "SE", 0.0, 3.5, 0.0),
                atom("CE", "C", 0.0, 4.5, 0.0)
        );
        environment.add(mse);

        List<Atom> opt = optimizer.optimize(mse, environment);

        assertEquals(5, opt.size(), "MSE should pass through like MET");
    }

    @Test
    @DisplayName("PRO ring passes through without modification")
    void testProPassThrough() {
        Residue pro = residue("PRO", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("CG", "C", 0.0, 2.5, 0.0),
                atom("CD", "C", 0.0, 3.5, 0.0),
                atom("N",  "N", 0.0, -1.0, 0.0)
        );
        environment.add(pro);

        List<Atom> opt = optimizer.optimize(pro, environment);

        assertEquals(5, opt.size(), "PRO should not add or remove atoms");
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("Unknown residue returns atoms unchanged")
    void testUnknownResidue() {
        Residue unk = residue("XYZ", 1, atom("CA", "C", 0.0, 0.0, 0.0));
        environment.add(unk);

        List<Atom> opt = optimizer.optimize(unk, environment);

        assertEquals(1, opt.size());
        assertEquals("CA", opt.get(0).getName());
    }

    @Test
    @DisplayName("ASN with missing atoms returns original")
    void testAsnMissingAtoms() {
        Residue asn = residue("ASN", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("CG", "C", 0.0, 2.5, 0.0),
                atom("OD1", "O", 1.2, 3.0, 0.0)
        );
        environment.add(asn);

        List<Atom> opt = optimizer.optimize(asn, environment);

        assertEquals(4, opt.size(), "Should return original atoms when ND2 missing");
    }

    @Test
    @DisplayName("HIS with missing ring atoms returns original")
    void testHisMissingAtoms() {
        Residue his = residue("HIS", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0)
        );
        environment.add(his);

        List<Atom> opt = optimizer.optimize(his, environment);

        assertEquals(2, opt.size(), "Should return original when ring atoms missing");
    }

    @Test
    @DisplayName("SER with missing OG returns original")
    void testSerMissingOG() {
        Residue ser = residue("SER", 1,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0)
        );
        environment.add(ser);

        List<Atom> opt = optimizer.optimize(ser, environment);

        assertEquals(2, opt.size(), "Should return original when OG missing");
    }

    @Test
    @DisplayName("Empty environment still optimizes (self-scoring only)")
    void testEmptyEnvironment() {
        Residue asn = residue("ASN", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("OD1", "O", 1.2, 3.0, 0.0),
                atom("ND2", "N", -1.2, 3.0, 0.0),
                atom("HD21", "H", -1.5, 3.5, 0.0),
                atom("HD22", "H", -1.5, 2.5, 0.0)
        );
        environment.add(asn);

        List<Atom> opt = optimizer.optimize(asn, environment);

        assertNotNull(find(opt, "OD1"));
        assertNotNull(find(opt, "ND2"));
    }

    @Test
    @DisplayName("Multiple residues in environment do not interfere")
    void testMultipleEnvironmentResidues() {
        Residue asn = residue("ASN", 1,
                atom("CA",  "C", 0.0, 0.0, 0.0),
                atom("CB",  "C", 0.0, 1.5, 0.0),
                atom("CG",  "C", 0.0, 2.5, 0.0),
                atom("OD1", "O", 1.2, 3.0, 0.0),
                atom("ND2", "N", -1.2, 3.0, 0.0),
                atom("HD21", "H", -1.5, 3.5, 0.0),
                atom("HD22", "H", -1.5, 2.5, 0.0)
        );
        Residue glu = residue("GLU", 2, atom("OE1", "O", 1.5, 3.0, 0.0));
        Residue lys = residue("LYS", 3, atom("NZ", "N", -1.5, 3.0, 0.0));
        environment.add(asn);
        environment.add(glu);
        environment.add(lys);

        List<Atom> opt = optimizer.optimize(asn, environment);

        Atom od1 = find(opt, "OD1");
        assertNotNull(od1);
        assertEquals(-1.2, od1.getPosition().x(), 1e-3, "OD1 should flip away from OE1");
    }
}