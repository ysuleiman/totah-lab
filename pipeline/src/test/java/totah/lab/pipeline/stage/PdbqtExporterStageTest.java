package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.report.AD4AtomTypingReport;
import totah.lab.pipeline.report.PdbqtExportReport;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.Topology;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PDBQT flex-tree export (buildFlexTree via the public
 * stage entry point). The fixtures are a synthetic LYS A:33 (full side chain
 * with hydrogens) plus a trailing PHE A:34 that must stay rigid.
 *
 * Expected LYS flex tree (flex-file serials assigned in emission order):
 *   ROOT: CA(1) HA(2)
 *   BRANCH 1 3  : CB(3) HB2(4) HB3(5)      (chi1, parent CA in ROOT)
 *   BRANCH 3 6  : CG(6) HG2(7) HG3(8)
 *   BRANCH 6 9  : CD(9) HD2(10) HD3(11)
 *   BRANCH 9 12 : CE(12) HE2(13) HE3(14) NZ(15) HZ1(16) HZ2(17) HZ3(18)
 */
public class PdbqtExporterStageTest {

    @TempDir
    Path tempPath;

    // ---- LYS A:33 local indices -------------------------------------------
    // 0 N, 1 H, 2 CA, 3 HA, 4 CB, 5 HB2, 6 HB3, 7 CG, 8 HG2, 9 HG3, 10 CD,
    // 11 HD2, 12 HD3, 13 CE, 14 HE2, 15 HE3, 16 NZ, 17 HZ1, 18 HZ2, 19 HZ3,
    // 20 C, 21 O
    private static final String[] LYS_ATOMS = {
            "N", "H", "CA", "HA", "CB", "HB2", "HB3", "CG", "HG2", "HG3",
            "CD", "HD2", "HD3", "CE", "HE2", "HE3", "NZ", "HZ1", "HZ2", "HZ3",
            "C", "O"};

    private static final double[][] LYS_COORDS = {
            {0.00, 0.00, 0.00},   // N
            {0.97, 0.20, 0.00},   // H   (amide H -> rigid backbone)
            {1.46, 0.00, 0.00},   // CA
            {1.46, 0.20, -1.08},  // HA
            {2.005, -1.42, 0.00}, // CB
            {2.90, -1.85, 0.55},  // HB2 (spurious CA contact is 2.41 A away)
            {1.25, -2.20, -0.30}, // HB3
            {3.45, -1.75, 0.35},  // CG
            {3.90, -0.95, 0.95},  // HG2
            {3.20, -2.55, 1.00},  // HG3
            {4.35, -2.85, -0.20}, // CD
            {5.25, -2.45, -0.60}, // HD2
            {3.85, -3.50, -0.95}, // HD3
            {4.95, -3.60, 1.00},  // CE
            {4.20, -4.35, 1.35},  // HE2
            {5.60, -4.25, 0.50},  // HE3
            {5.70, -2.85, 2.05},  // NZ
            {6.55, -2.35, 1.75},  // HZ1
            {5.10, -2.20, 2.55},  // HZ2
            {6.00, -3.55, 2.70},  // HZ3
            {2.005, 1.419, 0.00}, // C
            {1.230, 2.375, 0.00}  // O
    };

    // ---- PHE A:34 local indices: 22 N, 23 CA, 24 C, 25 O, 26 CB ------------
    private static final String[] PHE_ATOMS = {"N", "CA", "C", "O", "CB"};

    private static final double[][] PHE_COORDS = {
            {3.319, 1.628, 0.00},
            {4.259, 0.510, 0.00},
            {3.523, -0.822, 0.00},
            {4.159, -1.876, 0.00},
            {5.716, 1.015, 0.00}
    };

    @Test
    public void rigidOnlyExportWritesEveryAtomAndNoFlexFile() throws Exception {
        PipelineContext context = baseContext(null);
        new PdbqtExporterStage().run(context);

        Path pdbqt = tempPath.resolve("prepared_receptor.pdbqt");
        assertTrue(Files.exists(pdbqt), "prepared_receptor.pdbqt not written");
        List<String> atomLines = atomLines(Files.readAllLines(pdbqt));
        assertEquals(27, atomLines.size(),
                "rigid-only export must contain all 22 LYS + 5 PHE atoms");
        assertFalse(Files.exists(tempPath.resolve("prepared_flex.pdbqt")),
                "no flex file should be written without flex_residues");
        PdbqtExportReport report = context.require(ContextKeys.PDBQT_EXPORT_REPORT);
        assertEquals(2, report.residueCount());
        assertEquals(27, report.atomCount());
        assertEquals(0, report.flexResidueCount());
        assertEquals(pdbqt.toString(), report.receptorPath());
        assertNull(report.flexPath());
    }

    @Test
    public void rigidOnlyExportRetainsExplicitNonpolarHydrogens() throws Exception {
        PipelineContext context = baseContext(null);
        new PdbqtExporterStage().run(context);

        List<String> atomLines = atomLines(Files.readAllLines(
                tempPath.resolve("prepared_receptor.pdbqt")));
        Map<String, String> typeByName = new HashMap<>();
        for (String line : atomLines) {
            typeByName.put(atomName(line), atomType(line));
        }

        assertEquals("H", typeByName.get("HA"),
                "nonpolar alpha hydrogen must stay explicit in receptor PDBQT");
        assertEquals("H", typeByName.get("HB2"),
                "nonpolar side-chain hydrogen must stay explicit in receptor PDBQT");
        assertEquals("H", typeByName.get("HB3"),
                "nonpolar side-chain hydrogen must stay explicit in receptor PDBQT");
    }

    @Test
    public void missingAd4TypingReportIsRejected() {
        PipelineContext context = baseContext(null);
        context.remove(ContextKeys.AD4_ATOM_TYPING_REPORT);

        Exception e = assertThrows(IllegalStateException.class,
                () -> new PdbqtExporterStage().run(context));
        assertTrue(e.getMessage().contains(ContextKeys.AD4_ATOM_TYPING_REPORT));
    }

    @Test
    public void missingAutoDockTypeIsRejected() {
        Residue broken = lysine33().toBuilder()
                .atoms(lysine33().getAtoms().stream()
                        .map(atom -> atom.getName().equals("N")
                                ? atom.toBuilder().autoDockType(null).build()
                                : atom)
                        .toList())
                .build();
        List<Residue> residues = List.of(broken, phenylalanine34());
        PipelineContext context = baseContext(null);
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
        context.put(ContextKeys.PROTEIN_TOPOLOGY, topologyFor(residues));

        Exception e = assertThrows(IllegalStateException.class,
                () -> new PdbqtExporterStage().run(context));
        assertTrue(e.getMessage().contains("AutoDock4 type"));
    }

    @Test
    public void illegalAutoDockTypeIsRejected() {
        Residue broken = lysine33().toBuilder()
                .atoms(lysine33().getAtoms().stream()
                        .map(atom -> atom.getName().equals("N")
                                ? atom.toBuilder().autoDockType("Xx").build()
                                : atom)
                        .toList())
                .build();
        List<Residue> residues = List.of(broken, phenylalanine34());
        PipelineContext context = baseContext(null);
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
        context.put(ContextKeys.PROTEIN_TOPOLOGY, topologyFor(residues));

        Exception e = assertThrows(IllegalStateException.class,
                () -> new PdbqtExporterStage().run(context));
        assertTrue(e.getMessage().contains("AutoDock4 type"));
    }

    @Test
    public void nonFiniteChargeIsRejected() {
        Residue broken = lysine33().toBuilder()
                .atoms(lysine33().getAtoms().stream()
                        .map(atom -> atom.getName().equals("N")
                                ? atom.toBuilder().charge(Double.POSITIVE_INFINITY).build()
                                : atom)
                        .toList())
                .build();
        List<Residue> residues = List.of(broken, phenylalanine34());
        PipelineContext context = baseContext(null);
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
        context.put(ContextKeys.PROTEIN_TOPOLOGY, topologyFor(residues));

        Exception e = assertThrows(IllegalStateException.class,
                () -> new PdbqtExporterStage().run(context));
        assertTrue(e.getMessage().contains("Non-finite charge"));
    }

    @Test
    public void rootContainsExactlyCaAndHa() throws Exception {
        List<String> flex = runFlexExport();
        PdbqtExportReport report = contextAfterFlexExport;
        assertEquals(1, report.flexResidueCount());

        List<String> rootNames = new ArrayList<>();
        boolean inRoot = false;
        for (String line : flex) {
            if (line.equals("ROOT")) { inRoot = true; continue; }
            if (line.equals("ENDROOT")) break;
            if (inRoot) rootNames.add(atomName(line));
        }
        assertEquals(List.of("CA", "HA"), rootNames,
                "ROOT must hold CA and its hydrogen only");
    }

    @Test
    public void lysFlexTreeHasFourNestedChiBranches() throws Exception {
        List<String> flex = runFlexExport();

        List<int[]> branches = branchPairs(flex, "BRANCH");
        List<int[]> endBranches = branchPairs(flex, "ENDBRANCH");

        assertEquals(4, branches.size(), "LYS must have 4 BRANCH records (one per chi bond)");
        assertArrayEquals(new int[]{1, 3}, branches.get(0), "chi1 branch must be CA->CB");
        assertArrayEquals(new int[]{3, 6}, branches.get(1), "chi2 branch must be CB->CG");
        assertArrayEquals(new int[]{6, 9}, branches.get(2), "chi3 branch must be CG->CD");
        assertArrayEquals(new int[]{9, 12}, branches.get(3), "chi4 branch must be CD->CE");

        assertEquals(4, endBranches.size(), "each BRANCH needs a matching ENDBRANCH");
        // Nesting: ENDBRANCH records close in reverse order
        for (int i = 0; i < 4; i++) {
            assertArrayEquals(branches.get(3 - i), endBranches.get(i),
                    "ENDBRANCH " + i + " does not close the most recent BRANCH");
        }
    }

    @Test
    public void everySideChainAtomAppearsExactlyOnceInFlexFile() throws Exception {
        List<String> flex = runFlexExport();
        Map<Integer, String> serialToName = serialToNameMap(flex);

        Map<Integer, String> expected = new LinkedHashMap<>();
        expected.put(1, "CA");   expected.put(2, "HA");
        expected.put(3, "CB");   expected.put(4, "HB2");  expected.put(5, "HB3");
        expected.put(6, "CG");   expected.put(7, "HG2");  expected.put(8, "HG3");
        expected.put(9, "CD");   expected.put(10, "HD2"); expected.put(11, "HD3");
        expected.put(12, "CE");  expected.put(13, "HE2"); expected.put(14, "HE3");
        expected.put(15, "NZ");  expected.put(16, "HZ1");
        expected.put(17, "HZ2"); expected.put(18, "HZ3");

        assertEquals(18, atomLines(flex).size(),
                "flex file must hold exactly the 18 LYS side-chain atoms");
        assertEquals(expected, serialToName,
                "every LYS side-chain atom must appear exactly once, in chain order");
    }

    @Test
    public void spuriousHydrogenContactDoesNotPullHydrogenIntoRoot() throws Exception {
        // The fixture topology carries a spurious CA...HB2 contact (2.41 A,
        // inside the distance-based bond cutoff). If the tree builder walked
        // hydrogen edges, HB2 would attach to CA and land in ROOT.
        List<String> flex = runFlexExport();
        Map<Integer, String> serialToName = serialToNameMap(flex);

        assertEquals("HB2", serialToName.get(4),
                "HB2 must stay attached to CB (serial 4 in chi1 branch), not CA");
        List<String> rigid = atomLines(Files.readAllLines(
                tempPath.resolve("prepared_receptor.pdbqt")));
        assertTrue(rigid.stream().noneMatch(l -> atomName(l).equals("HB2")),
                "HB2 leaked into the rigid receptor");
    }

    @Test
    public void backboneAtomsStayInRigidReceptor() throws Exception {
        List<String> flex = runFlexExport();
        List<String> rigid = atomLines(Files.readAllLines(
                tempPath.resolve("prepared_receptor.pdbqt")));

        // Rigid file: LYS backbone (N, H, C, O) + the whole PHE residue
        assertEquals(9, rigid.size(),
                "rigid receptor must keep LYS backbone + all 5 PHE atoms");
        assertEquals(List.of("N", "H", "C", "O", "N", "CA", "C", "O", "CB"),
                rigid.stream().map(this::atomName).toList(),
                "unexpected atoms left in rigid receptor");

        // Vina rejects any non-ATOM tag in the rigid receptor
        List<String> rigidAll = Files.readAllLines(
                tempPath.resolve("prepared_receptor.pdbqt"));
        assertTrue(rigidAll.stream().allMatch(l -> l.startsWith("ATOM")),
                "rigid receptor must contain ATOM records only: " + rigidAll);

        // No backbone atom may be duplicated into the flex file
        Map<Integer, String> flexAtoms = serialToNameMap(flex);
        assertFalse(flexAtoms.containsValue("N"), "backbone N leaked into flex file");
        assertFalse(flexAtoms.containsValue("C"), "backbone C leaked into flex file");
        assertFalse(flexAtoms.containsValue("O"), "backbone O leaked into flex file");
        // The amide H is rigid; the only H-named atom anywhere is LYS H
        assertFalse(flexAtoms.containsValue("H"), "amide H leaked into flex file");
    }

    @Test
    public void noTorsdofAnywhereKeepsVinaHappy() throws Exception {
        // Verified against the Vina 1.2.0 binary: it aborts with
        // "Unknown or inappropriate tag found in flex residue" when the flex
        // file carries a TORSDOF record. Meeko omits TORSDOF for the same
        // reason, so we must never regress to writing it.
        List<String> flex = runFlexExport();
        assertTrue(flex.stream().noneMatch(l -> l.startsWith("TORSDOF")),
                "Vina rejects TORSDOF in the flex file: " + flex);

        List<String> rigid = Files.readAllLines(
                tempPath.resolve("prepared_receptor.pdbqt"));
        assertTrue(rigid.stream().noneMatch(l -> l.startsWith("TORSDOF")),
                "rigid receptor must not carry TORSDOF either: " + rigid);
    }

    @Test
    public void unknownFlexResidueEntryIsRejected() {
        PipelineContext context = baseContext(List.of("B:99"));
        Exception e = assertThrows(IllegalArgumentException.class,
                () -> new PdbqtExporterStage().run(context));
        assertTrue(e.getMessage().contains("B:99"),
                "error should name the offending entry: " + e.getMessage());
    }

    @Test
    public void malformedFlexResidueEntryIsRejected() {
        PipelineContext context = baseContext(List.of("A-33"));
        assertThrows(IllegalArgumentException.class,
                () -> new PdbqtExporterStage().run(context),
                "entries without 'chain:number' format must be rejected");
    }

    @Test
    public void hetatmFlexResidueIsRejected() {
        Residue mse = Residue.builder()
                .name("MSE").number(40).chain("A")
                .atoms(List.of(atom("CA", "C", 9.0, 9.0, 0.0)))
                .build();
        List<Residue> residues = new ArrayList<>(allResidues());
        residues.add(mse);

        PipelineContext context = baseContext(List.of("A:40"));
        context.put("protein_residues", residues);
        context.put(ContextKeys.PROTEIN_TOPOLOGY, topologyFor(residues));

        Exception e = assertThrows(IllegalArgumentException.class,
                () -> new PdbqtExporterStage().run(context));
        assertTrue(e.getMessage().contains("not a standard amino acid"),
                "flexible HETATM must be rejected: " + e.getMessage());
    }

    // ==================== FIXTURES ====================

    /** Runs the stage in flex mode and returns the prepared_flex.pdbqt lines. */
    private PdbqtExportReport contextAfterFlexExport;

    private List<String> runFlexExport() throws Exception {
        PipelineContext context = baseContext(List.of("A:33"));
        new PdbqtExporterStage().run(context);
        contextAfterFlexExport = context.require(ContextKeys.PDBQT_EXPORT_REPORT);

        Path flex = tempPath.resolve("prepared_flex.pdbqt");
        assertTrue(Files.exists(flex), "prepared_flex.pdbqt not written");
        List<String> lines = Files.readAllLines(flex);
        assertEquals("BEGIN_RES LYS A 33", lines.get(0), "flex file must open with BEGIN_RES");
        assertTrue(lines.contains("END_RES LYS A 33"),
                "flex file must contain END_RES: " + lines);
        return lines;
    }

    private PipelineContext baseContext(List<String> flexEntries) {
        List<Residue> residues = allResidues();
        PipelineContext context = new PipelineContext(tempPath, tempPath);
        context.put("protein_residues", residues);
        context.put(ContextKeys.PROTEIN_TOPOLOGY, topologyFor(residues));
        context.put(ContextKeys.AD4_ATOM_TYPING_REPORT,
                new AD4AtomTypingReport(residues.size(),
                        residues.stream().mapToInt(Residue::getAtomCount).sum(), Map.of("C", 1)));
        if (flexEntries != null) {
            context.put(ContextKeys.FLEX_RESIDUES, flexEntries);
        }
        return context;
    }

    private static List<Residue> allResidues() {
        return List.of(lysine33(), phenylalanine34());
    }

    private static Residue lysine33() {
        List<Atom> atoms = new ArrayList<>();
        for (int i = 0; i < LYS_ATOMS.length; i++) {
            atoms.add(atom(LYS_ATOMS[i], elementOf(LYS_ATOMS[i]),
                    LYS_COORDS[i][0], LYS_COORDS[i][1], LYS_COORDS[i][2]));
        }
        return Residue.builder().name("LYS").number(33).chain("A").atoms(atoms).build();
    }

    private static Residue phenylalanine34() {
        List<Atom> atoms = new ArrayList<>();
        for (int i = 0; i < PHE_ATOMS.length; i++) {
            atoms.add(atom(PHE_ATOMS[i], elementOf(PHE_ATOMS[i]),
                    PHE_COORDS[i][0], PHE_COORDS[i][1], PHE_COORDS[i][2]));
        }
        return Residue.builder().name("PHE").number(34).chain("A").atoms(atoms).build();
    }

    /** Bond graph matching the fixtures, including a spurious CA...HB2 contact. */
    private static Topology topologyFor(List<Residue> residues) {
        int[][] bonds = {
                // LYS backbone + side chain (local == flat for residue 0)
                {0, 1}, {0, 2}, {2, 3}, {2, 4}, {2, 20}, {20, 21},
                {4, 5}, {4, 6}, {4, 7},
                {7, 8}, {7, 9}, {7, 10},
                {10, 11}, {10, 12}, {10, 13},
                {13, 14}, {13, 15}, {13, 16},
                {16, 17}, {16, 18}, {16, 19},
                {2, 5},            // spurious CA...HB2 distance contact
                {20, 22},          // peptide bond LYS C -> PHE N
                // PHE (flat indices, base 22)
                {22, 23}, {23, 24}, {23, 26}, {24, 25}
        };
        List<Topology.Edge> edges = new ArrayList<>();
        for (int[] b : bonds) {
            edges.add(new Topology.Edge(b[0], b[1], 1.5));
        }
        int atomCount = residues.stream().mapToInt(Residue::getAtomCount).sum();
        return new Topology(atomCount, edges);
    }

    private static String elementOf(String atomName) {
        return switch (atomName.charAt(0)) {
            case 'H' -> "H";
            case 'N' -> "N";
            case 'O' -> "O";
            default -> "C";
        };
    }

    private static Atom atom(String name, String element, double x, double y, double z) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(x, y, z))
                .amberType(element)
                .autoDockType(ad4Type(element))
                .charge("H".equals(element) ? 0.05 : -0.05)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.fromSymbol(element))
                .build();
    }

    private static String ad4Type(String element) {
        return switch (element) {
            case "H" -> "H";
            case "N" -> "N";
            case "O" -> "OA";
            default -> "C";
        };
    }

    // ==================== PARSING HELPERS ====================

    private List<String> atomLines(List<String> lines) {
        return lines.stream()
                .filter(l -> l.startsWith("ATOM") || l.startsWith("HETATM"))
                .toList();
    }

    private String atomName(String atomLine) {
        return atomLine.trim().split("\\s+")[2];
    }

    private String atomType(String atomLine) {
        String[] fields = atomLine.trim().split("\\s+");
        return fields[fields.length - 1];
    }

    /** Parent/child serial pairs of all lines starting with the given keyword. */
    private List<int[]> branchPairs(List<String> lines, String keyword) {
        List<int[]> pairs = new ArrayList<>();
        for (String line : lines) {
            String[] fields = line.trim().split("\\s+");
            if (fields[0].equals(keyword)) {
                pairs.add(new int[]{Integer.parseInt(fields[1]), Integer.parseInt(fields[2])});
            }
        }
        return pairs;
    }

    private Map<Integer, String> serialToNameMap(List<String> lines) {
        Map<Integer, String> map = new HashMap<>();
        for (String line : atomLines(lines)) {
            String[] fields = line.trim().split("\\s+");
            map.put(Integer.parseInt(fields[1]), fields[2]);
        }
        return map;
    }
}
