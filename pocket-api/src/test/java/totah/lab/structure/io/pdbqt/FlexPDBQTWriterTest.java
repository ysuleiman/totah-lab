package totah.lab.structure.io.pdbqt;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.docking.torsion.TorsionBranch;
import totah.lab.docking.torsion.TorsionTree;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FlexPDBQTWriter}.
 */
class FlexPDBQTWriterTest {

    private StringWriter stringWriter;
    private BufferedWriter bufferedWriter;
    private FlexPDBQTWriter writer;

    @BeforeEach
    void setUp() {
        stringWriter = new StringWriter();
        bufferedWriter = new BufferedWriter(stringWriter);
        writer = new FlexPDBQTWriter(bufferedWriter);
    }

    /* ================================================================
     *  Helpers
     * ================================================================ */

    private Residue makeResidue(String name, String chain, int number, List<Atom> atoms) {
        return Residue.builder()
                .name(name)
                .chain(chain)
                .number(number)
                .insertionCode(' ')
                .atoms(atoms)
                .build();
    }

    private Atom makeAtom(String atomName, String elementSymbol) {
        return Atom.builder()
                .name(atomName)
                .autoDockType(autoDockType(elementSymbol))
                .position(new Point3D(1.0, 2.0, 3.0))
                .charge(0.0)
                .bFactor(0.0)
                .occupancy(1.0)
                .element(Element.builder()
                        .symbol(elementSymbol)
                        .atomicNumber(atomicNumber(elementSymbol))
                        .atomicMass(0.0)
                        .covalentRadius(0.0)
                        .vdwRadius(0.0)
                        .build())
                .build();
    }

    private String autoDockType(String elementSymbol) {
        return switch (elementSymbol) {
            case "H" -> "H";
            case "O" -> "OA";
            case "N" -> "N";
            case "S" -> "S";
            default -> "C";
        };
    }

    private int atomicNumber(String elementSymbol) {
        return switch (elementSymbol) {
            case "H" -> 1;
            case "C" -> 6;
            case "N" -> 7;
            case "O" -> 8;
            case "S" -> 16;
            default -> 0;
        };
    }

    private TorsionTree makeTree(int... rootIdxs) {
        TorsionTree tree = new TorsionTree();
        for (int i : rootIdxs) {
            tree.addRootAtom(i);
        }
        return tree;
    }

    private TorsionBranch makeBranch(int parentIdx, int childIdx, int... movingIdxs) {
        List<Integer> moving = Arrays.stream(movingIdxs).boxed().collect(Collectors.toList());
        return new TorsionBranch(parentIdx, childIdx, moving);
    }

    private String output() throws IOException {
        bufferedWriter.flush();
        return stringWriter.toString();
    }

    /* ================================================================
     *  Tests
     * ================================================================ */

    @Test
    @DisplayName("Empty map produces no output")
    void emptyMap() throws IOException {
        writer.write(Collections.emptyMap());
        assertEquals("", output());
    }

    @Test
    @DisplayName("Rigid residue writes BEGIN_RES, ROOT, ENDROOT, END_RES")
    void rigidResidueStructure() throws IOException {
        List<Atom> atoms = List.of(
                makeAtom("CA", "C"),
                makeAtom("C", "C"),
                makeAtom("O", "O")
        );
        Residue res = makeResidue("ALA", "A", 42, atoms);
        TorsionTree tree = makeTree(0, 1, 2);

        Map<Residue, TorsionTree> map = new LinkedHashMap<>();
        map.put(res, tree);

        writer.write(map);
        String out = output();

        assertTrue(out.contains("BEGIN_RES ALA A 42"));
        assertTrue(out.contains("ROOT"));
        assertTrue(out.contains("ENDROOT"));
        assertTrue(out.contains("END_RES ALA A 42"));
        assertFalse(out.contains("BRANCH"), "Rigid residue should not contain BRANCH");
    }

    @Test
    @DisplayName("Serials are local and sequential per residue")
    void serialsAreLocal() throws IOException {
        List<Atom> atoms = List.of(
                makeAtom("CA", "C"),  // 0 -> serial 1
                makeAtom("CB", "C"),  // 1 -> serial 2
                makeAtom("CG", "C"),  // 2 -> serial 3
                makeAtom("CD", "C")   // 3 -> serial 4
        );
        Residue res = makeResidue("LYS", "A", 10, atoms);

        TorsionBranch branch = makeBranch(1, 2, 2, 3);
        TorsionTree tree = makeTree(0, 1);
        tree.addRootBranch(branch);

        Map<Residue, TorsionTree> map = new LinkedHashMap<>();
        map.put(res, tree);

        writer.write(map);
        String out = output();

        assertTrue(out.contains("BRANCH 2 3"),
                "BRANCH line must use correct local serials: expected 'BRANCH 2 3'");
        assertTrue(out.contains("ENDBRANCH 2 3"));
    }

    @Test
    @DisplayName("Serial numbering resets for each residue")
    void serialsResetPerResidue() throws IOException {
        List<Atom> atoms = List.of(makeAtom("CA", "C"), makeAtom("CB", "C"));
        Residue res1 = makeResidue("ALA", "A", 1, atoms);
        TorsionBranch b1 = makeBranch(0, 1, 1);
        TorsionTree t1 = makeTree(0);
        t1.addRootBranch(b1);

        Residue res2 = makeResidue("SER", "B", 2, atoms);
        TorsionBranch b2 = makeBranch(0, 1, 1);
        TorsionTree t2 = makeTree(0);
        t2.addRootBranch(b2);

        Map<Residue, TorsionTree> map = new LinkedHashMap<>();
        map.put(res1, t1);
        map.put(res2, t2);

        writer.write(map);
        String out = output();

        long branch12count = Arrays.stream(out.split("\n"))
                .filter(l -> l.equals("BRANCH 1 2"))
                .count();

        assertEquals(2, branch12count,
                "Each residue must start its own serial sequence at 1");
    }

    @Test
    @DisplayName("Single branch emits BRANCH/ENDBRANCH pair")
    void singleBranchPair() throws IOException {
        List<Atom> atoms = List.of(
                makeAtom("CA", "C"),  // 0
                makeAtom("CB", "C"),  // 1
                makeAtom("CG", "C")   // 2
        );
        Residue res = makeResidue("ASP", "A", 5, atoms);

        TorsionBranch branch = makeBranch(0, 1, 1, 2);
        TorsionTree tree = makeTree(0);
        tree.addRootBranch(branch);

        Map<Residue, TorsionTree> map = new LinkedHashMap<>();
        map.put(res, tree);

        writer.write(map);
        String out = output();

        assertTrue(out.contains("BRANCH"));
        assertTrue(out.contains("ENDBRANCH"));
    }

    @Test
    @DisplayName("Nested branches produce correctly nested BRANCH/ENDBRANCH blocks")
    void nestedBranchNesting() throws IOException {
        List<Atom> atoms = List.of(
                makeAtom("CA", "C"),  // 0 -> s1
                makeAtom("CB", "C"),  // 1 -> s2
                makeAtom("CG", "C"),  // 2 -> s3
                makeAtom("CD", "C"),  // 3 -> s4
                makeAtom("HB2", "H")  // 4 -> s5
        );
        Residue res = makeResidue("LYS", "A", 20, atoms);

        TorsionBranch inner = makeBranch(1, 2, 2, 3, 4);
        TorsionBranch outer = makeBranch(0, 1, 1);
        outer.getChildren().add(inner);

        TorsionTree tree = makeTree(0);
        tree.addRootBranch(outer);

        Map<Residue, TorsionTree> map = new LinkedHashMap<>();
        map.put(res, tree);

        writer.write(map);
        String[] lines = output().split("\n");

        List<String> branchLines = Arrays.stream(lines)
                .filter(l -> l.startsWith("BRANCH") || l.startsWith("ENDBRANCH"))
                .toList();

        assertEquals(4, branchLines.size(),
                "Expected 2 BRANCH + 2 ENDBRANCH lines");

        assertEquals("BRANCH 1 2", branchLines.get(0));
        assertEquals("BRANCH 2 3", branchLines.get(1));
        assertEquals("ENDBRANCH 2 3", branchLines.get(2));
        assertEquals("ENDBRANCH 1 2", branchLines.get(3));
    }

    @Test
    @DisplayName("Child atom is the first ATOM record inside a BRANCH block")
    void childAtomFirst() throws IOException {
        List<Atom> atoms = List.of(
                makeAtom("CA", "C"),  // 0
                makeAtom("CB", "C"),  // 1 - child
                makeAtom("CG", "C"),  // 2 - moving
                makeAtom("HB2", "H")  // 3 - moving
        );
        Residue res = makeResidue("ARG", "A", 1, atoms);

        TorsionBranch branch = makeBranch(0, 1, 1, 2, 3);
        TorsionTree tree = makeTree(0);
        tree.addRootBranch(branch);

        Map<Residue, TorsionTree> map = new LinkedHashMap<>();
        map.put(res, tree);

        writer.write(map);
        String[] lines = output().split("\n");

        int branchIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("BRANCH")) {
                branchIdx = i;
                break;
            }
        }
        assertTrue(branchIdx >= 0, "BRANCH line not found");

        String firstInside = lines[branchIdx + 1];
        assertTrue(firstInside.startsWith("ATOM") || firstInside.startsWith("HETATM"),
                "First record after BRANCH must be the child atom, got: " + firstInside);
    }

    @Test
    @DisplayName("Child atom is not duplicated when it also appears in movingAtoms")
    void childNotDuplicated() throws IOException {
        List<Atom> atoms = List.of(
                makeAtom("CA", "C"),  // 0
                makeAtom("CB", "C"),  // 1 - child
                makeAtom("CG", "C")   // 2 - moving
        );
        Residue res = makeResidue("ASP", "A", 1, atoms);

        TorsionBranch branch = makeBranch(0, 1, 1, 2);
        TorsionTree tree = makeTree(0);
        tree.addRootBranch(branch);

        Map<Residue, TorsionTree> map = new LinkedHashMap<>();
        map.put(res, tree);

        writer.write(map);
        String[] lines = output().split("\n");

        int branchStart = -1, endBranch = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("BRANCH")) branchStart = i;
            if (lines[i].startsWith("ENDBRANCH")) endBranch = i;
        }
        assertTrue(branchStart >= 0 && endBranch > branchStart);

        int atomRecords = 0;
        for (int i = branchStart + 1; i < endBranch; i++) {
            if (lines[i].startsWith("ATOM") || lines[i].startsWith("HETATM")) {
                atomRecords++;
            }
        }
        assertEquals(2, atomRecords,
                "Branch should contain exactly 2 atoms (CB and CG), not 3");
    }

    @Test
    @DisplayName("Multiple residues are separated by a blank line")
    void multiResidueBlankLine() throws IOException {
        List<Atom> atoms = List.of(makeAtom("CA", "C"));
        Residue res1 = makeResidue("ALA", "A", 1, atoms);
        TorsionTree t1 = makeTree(0);

        Residue res2 = makeResidue("SER", "B", 2, atoms);
        TorsionTree t2 = makeTree(0);

        Map<Residue, TorsionTree> map = new LinkedHashMap<>();
        map.put(res1, t1);
        map.put(res2, t2);

        writer.write(map);
        String[] lines = output().split("\n");

        boolean foundSeparator = false;
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].startsWith("END_RES") && lines[i + 1].trim().isEmpty()) {
                foundSeparator = true;
                break;
            }
        }
        assertTrue(foundSeparator,
                "A blank line must separate consecutive residue blocks");
    }

    @Test
    @DisplayName("Integration: writes readable file to disk")
    void fileIntegration(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test_flex.pdbqt");

        List<Atom> atoms = List.of(
                makeAtom("CA", "C"),
                makeAtom("CB", "C"),
                makeAtom("CG", "C")
        );
        Residue res = makeResidue("GLU", "A", 100, atoms);
        TorsionBranch branch = makeBranch(0, 1, 1, 2);
        TorsionTree tree = makeTree(0);
        tree.addRootBranch(branch);

        Map<Residue, TorsionTree> map = new LinkedHashMap<>();
        map.put(res, tree);

        try (BufferedWriter fw = Files.newBufferedWriter(file);
             FlexPDBQTWriter flexWriter = new FlexPDBQTWriter(fw)) {
            flexWriter.write(map);
        }

        String content = Files.readString(file);
        assertTrue(content.contains("BEGIN_RES GLU A 100"));
        assertTrue(content.contains("END_RES GLU A 100"));
        assertTrue(content.contains("BRANCH"));
        assertTrue(content.contains("ENDBRANCH"));
    }
}
