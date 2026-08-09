package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.web.poseanalysis.MutationPreparationService
        .MutationPreparationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationPreparationServiceTest {

    @TempDir
    Path directory;

    private StubPoseAnalysisRepository repository;
    private Path output;

    @BeforeEach
    void setUp() throws IOException {
        repository = new StubPoseAnalysisRepository();
        Files.writeString(
                directory.resolve("receptor-wt.pdbqt"),
                wildTypeReceptor()
        );
        repository.addRun(5, 1, "receptor-wt", "METTL7A");
        output = directory.resolve("mutant/receptor-leu2.pdbqt");
    }

    @Test
    void pheToLeuMutatesOnlyTheTargetResidue() throws IOException {
        MutationPreparationResult result =
                service().prepare(5, "F2L", output);

        Structure wildType = result.wildTypeStructure();
        Structure mutant = result.mutantStructure();

        // Target residue: LEU at the same number/insertion code,
        // backbone coordinates untouched.
        Residue mutatedResidue = mutant
                .findResidue(new ResidueId("A", 2, null)).orElseThrow();
        assertEquals("LEU", mutatedResidue.getName());
        assertEquals(2, mutatedResidue.getNumber());
        assertEquals(
                List.of("N", "CA", "C", "O", "CB", "CG", "CD1", "CD2"),
                mutatedResidue.getAtoms().stream()
                        .map(Atom::getName)
                        .toList()
        );
        Residue wildTypeResidue = wildType
                .findResidue(new ResidueId("A", 2, null)).orElseThrow();
        for (String backbone : List.of("N", "CA", "C", "O")) {
            Point3D before = wildTypeResidue.findAtom(backbone)
                    .orElseThrow().getPosition();
            Point3D after = mutatedResidue.findAtom(backbone)
                    .orElseThrow().getPosition();
            assertEquals(before, after, "backbone " + backbone);
        }

        // Every other residue is atom-for-atom identical.
        assertResiduesIdentical(wildType, mutant, "A", 1);
        assertResiduesIdentical(wildType, mutant, "A", 3);

        // The new side chain carries the exemplar's AutoDock4 types
        // and charges, never invented values.
        Residue exemplar = wildType
                .findResidue(new ResidueId("A", 3, null)).orElseThrow();
        Map<String, Atom> exemplarAtoms = new java.util.HashMap<>();
        for (Atom atom : exemplar.getAtoms()) {
            exemplarAtoms.put(atom.getName(), atom);
        }
        for (String sideChain : List.of("CB", "CG", "CD1", "CD2")) {
            Atom atom = mutatedResidue.findAtom(sideChain).orElseThrow();
            assertEquals(
                    exemplarAtoms.get(sideChain).getAutoDockType(),
                    atom.getAutoDockType(),
                    sideChain + " type"
            );
            assertEquals(
                    exemplarAtoms.get(sideChain).getCharge(),
                    atom.getCharge(),
                    1.0e-9,
                    sideChain + " charge"
            );
        }

        // Provenance (single mutation == one-entry report list).
        assertEquals(1, result.mutations().size());
        MutationPreparationService.AppliedMutationReport applied =
                result.mutations().getFirst();
        assertEquals("F2L", applied.spec());
        assertEquals("PHE", applied.wildType());
        assertEquals("LEU", applied.mutant());
        assertNotNull(applied.rotamerId());
        assertEquals(new ResidueId("A", 3, null), applied.typingExemplar());
        assertTrue(result.rotamerMethod().contains("RotamerSelector"));

        // Atom counts: PHE 2 (11 atoms) becomes LEU (8 atoms).
        assertEquals(24, wildType.getAtomCount());
        assertEquals(21, mutant.getAtomCount());

        // The written PDBQT has LEU 2 and no PHE 2, and passes the
        // writer's per-atom type/charge validation.
        assertTrue(Files.isRegularFile(output));
        List<String> lines = Files.readAllLines(output);
        List<String> residueTwo = lines.stream()
                .filter(line -> line.startsWith("ATOM")
                        && line.substring(22, 26).trim().equals("2"))
                .toList();
        assertEquals(8, residueTwo.size());
        assertTrue(residueTwo.stream()
                .allMatch(line -> line.substring(17, 20).trim()
                        .equals("LEU")));
        assertTrue(lines.stream().noneMatch(line ->
                line.startsWith("ATOM")
                        && line.substring(17, 20).trim().equals("PHE")
                        && line.substring(22, 26).trim().equals("2")));
        assertEquals(21, lines.stream()
                .filter(line -> line.startsWith("ATOM"))
                .count());
    }

    @Test
    void compositeSpecAppliesEverySubstitutionSequentially()
            throws IOException {
        MutationPreparationResult result =
                service().prepare(5, " F2L , L3A ", output);

        // Both substitutions landed, in order, each with its rotamer.
        assertEquals(2, result.mutations().size());
        assertEquals("F2L,L3A", result.mutationSpec());
        assertEquals("F2L", result.mutations().get(0).spec());
        assertEquals("L3A", result.mutations().get(1).spec());
        assertEquals("PHE", result.mutations().get(0).wildType());
        assertEquals("LEU", result.mutations().get(0).mutant());
        assertEquals("LEU", result.mutations().get(1).wildType());
        assertEquals("ALA", result.mutations().get(1).mutant());
        assertNotNull(result.mutations().get(0).rotamerId());
        assertNotNull(result.mutations().get(1).rotamerId());

        Structure mutant = result.mutantStructure();
        assertEquals("LEU", mutant
                .findResidue(new ResidueId("A", 2, null))
                .orElseThrow().getName());
        assertEquals("ALA", mutant
                .findResidue(new ResidueId("A", 3, null))
                .orElseThrow().getName());
        // PHE 2 (11 atoms) -> LEU (8), LEU 3 (8) -> ALA (5).
        assertEquals(24 - 3 - 3, mutant.getAtomCount());

        // ALA 1 is atom-for-atom identical to wild type.
        assertResiduesIdentical(
                result.wildTypeStructure(), mutant, "A", 1);

        // The second substitution's typing exemplar is the wild-type
        // LEU 3 (read from the original structure, where it is still
        // LEU) for F2L, and ALA 1 for L3A.
        assertEquals(new ResidueId("A", 3, null),
                result.mutations().get(0).typingExemplar());
        assertEquals(new ResidueId("A", 1, null),
                result.mutations().get(1).typingExemplar());

        // The writer accepted every atom (typed side chains) and the
        // report lists each mutation with its rotamer.
        List<String> lines = Files.readAllLines(output);
        assertEquals(18, lines.stream()
                .filter(line -> line.startsWith("ATOM"))
                .count());
        String report = service().render(result);
        assertTrue(report.contains("F2L: PHE A:2 -> LEU"), report);
        assertTrue(report.contains("L3A: LEU A:3 -> ALA"), report);
        assertTrue(report.contains("(2 substitutions)"), report);
    }

    @Test
    void emptyElementInSpecListFailsClearly() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().prepare(5, "F2L,,L3A", output)
        );
        assertTrue(exception.getMessage().contains(
                "Empty mutation spec in list"),
                exception.getMessage());
    }

    @Test
    void pdbExtensionWritesStandardPdb() throws IOException {
        Path pdbOutput = directory.resolve("mutant/receptor-leu2.pdb");

        MutationPreparationResult result =
                service().prepare(5, "F2L", pdbOutput);

        assertTrue(Files.isRegularFile(pdbOutput));
        List<String> lines = Files.readAllLines(pdbOutput);
        // Standard PDB: 21 ATOM records (78 columns, element in
        // columns 77-78, no charge/AutoDock columns), TER and END.
        List<String> atoms = lines.stream()
                .filter(line -> line.startsWith("ATOM"))
                .toList();
        assertEquals(21, atoms.size());
        assertEquals("TER", lines.get(lines.size() - 2));
        assertEquals("END", lines.getLast());
        for (String line : atoms) {
            assertEquals(78, line.length(), line);
        }
        List<String> residueTwo = atoms.stream()
                .filter(line -> line.substring(22, 26).trim().equals("2"))
                .toList();
        assertEquals(8, residueTwo.size());
        assertTrue(residueTwo.stream()
                .allMatch(line -> line.substring(17, 20).trim()
                        .equals("LEU")));
        List<String> sideChain = residueTwo.stream()
                .filter(line -> List.of("CB", "CG", "CD1", "CD2")
                        .contains(line.substring(12, 16).trim()))
                .toList();
        assertEquals(4, sideChain.size());
        assertTrue(sideChain.stream()
                .allMatch(line -> line.substring(76, 78).trim()
                        .equals("C")));
        // The WT coordinates of the untouched backbone survive.
        assertTrue(residueTwo.stream()
                .anyMatch(line -> line.substring(12, 16).trim()
                        .equals("CA")));
        assertTrue(result.outputPath().toString().endsWith(".pdb"));
    }

    @Test
    void pdbqtExtensionKeepsThePdbqtFormat() throws IOException {
        service().prepare(5, "F2L", output);

        List<String> lines = Files.readAllLines(output);
        // PDBQT: charge and AutoDock4 type trail each ATOM record —
        // the line is longer than a PDB record and carries no TER/END
        // under the default options.
        String first = lines.stream()
                .filter(line -> line.startsWith("ATOM"))
                .findFirst()
                .orElseThrow();
        assertTrue(first.length() > 78, first);
        assertTrue(lines.stream().noneMatch(line -> line.equals("TER")
                || line.equals("END")));
    }

    @Test
    void compactSpecParserRejectsMalformedSpecs() {
        for (String malformed : List.of("", "43L", "F43", "F4L3", "F-43L")) {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> service().prepare(5, malformed, output),
                    malformed
            );
            assertTrue(exception.getMessage().contains("F43L"),
                    malformed + ": " + exception.getMessage());
        }
        IllegalStateException unknown = assertThrows(
                IllegalStateException.class,
                () -> service().prepare(5, "X2L", output)
        );
        assertTrue(unknown.getMessage().contains(
                "one-letter code"),
                unknown.getMessage());
    }

    @Test
    void wildTypeMismatchFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().prepare(5, "A2L", output)
        );
        assertEquals(
                "Mutation A2L expects ALA at A:2 but the receptor of"
                        + " run 5 has PHE there",
                exception.getMessage()
        );
    }

    @Test
    void missingResidueFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().prepare(5, "F99L", output)
        );
        assertTrue(exception.getMessage().contains("has no residue 99"),
                exception.getMessage());
    }

    @Test
    void unknownRunFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().prepare(99, "F2L", output)
        );
        assertEquals("No docking run 99", exception.getMessage());
    }

    private void assertResiduesIdentical(
            Structure wildType,
            Structure mutant,
            String chainId,
            int residueNumber
    ) {
        Residue before = wildType
                .findResidue(new ResidueId(chainId, residueNumber, null))
                .orElseThrow();
        Residue after = mutant
                .findResidue(new ResidueId(chainId, residueNumber, null))
                .orElseThrow();
        assertEquals(before.getName(), after.getName());
        assertEquals(before.getAtomCount(), after.getAtomCount());
        for (int index = 0; index < before.getAtomCount(); index++) {
            Atom a = before.getAtoms().get(index);
            Atom b = after.getAtoms().get(index);
            assertEquals(a.getName(), b.getName());
            assertEquals(a.getPosition(), b.getPosition());
            assertEquals(a.getCharge(), b.getCharge(), 1.0e-9);
            assertEquals(a.getAutoDockType(), b.getAutoDockType());
        }
    }

    private MutationPreparationService service() {
        return new MutationPreparationService(
                new PoseAnalysisService(repository, directory.toString()),
                repository
        );
    }

    /**
     * Three-residue receptor: ALA 1, PHE 2 (mutation target, backbone
     * plus full aromatic side chain), LEU 3 (typing exemplar, with
     * distinctive side-chain charges).
     */
    private static String wildTypeReceptor() {
        List<String> lines = new ArrayList<>();
        int serial = 1;
        serial = residue(lines, serial, "ALA", 1, new Object[][]{
                {"N", 0.0, 0.0, 0.0, -0.30, "NA"},
                {"CA", 1.4, 0.0, 0.0, 0.10, "C"},
                {"C", 2.0, 1.3, 0.0, 0.45, "C"},
                {"O", 1.4, 2.3, 0.0, -0.45, "OA"},
                {"CB", 1.9, -0.7, 1.2, -0.10, "C"}
        });
        serial = residue(lines, serial, "PHE", 2, new Object[][]{
                {"N", 9.0, 0.0, 0.0, -0.30, "NA"},
                {"CA", 10.0, 0.0, 0.0, 0.10, "C"},
                {"C", 10.6, 1.3, 0.0, 0.45, "C"},
                {"O", 10.0, 2.3, 0.0, -0.45, "OA"},
                {"CB", 10.7, -1.1, 0.6, -0.10, "C"},
                {"CG", 10.2, -2.5, 0.4, 0.00, "A"},
                {"CD1", 10.8, -3.6, 0.9, -0.10, "A"},
                {"CD2", 9.1, -2.7, -0.3, -0.10, "A"},
                {"CE1", 10.3, -4.9, 0.7, -0.10, "A"},
                {"CE2", 8.6, -4.0, -0.5, -0.10, "A"},
                {"CZ", 9.2, -5.1, 0.0, -0.10, "A"}
        });
        residue(lines, serial, "LEU", 3, new Object[][]{
                {"N", 19.0, 0.0, 0.0, -0.30, "NA"},
                {"CA", 20.0, 0.0, 0.0, 0.10, "C"},
                {"C", 20.6, 1.3, 0.0, 0.45, "C"},
                {"O", 20.0, 2.3, 0.0, -0.45, "OA"},
                {"CB", 20.7, -1.1, 0.6, -0.11, "C"},
                {"CG", 20.2, -2.5, 0.4, 0.05, "C"},
                {"CD1", 20.9, -3.2, 1.4, -0.12, "C"},
                {"CD2", 20.7, -3.1, -0.9, -0.12, "C"}
        });
        return String.join("\n", lines) + "\n";
    }

    private static int residue(            List<String> lines,
            int firstSerial,
            String name,
            int number,
            Object[][] atoms
    ) {
        int serial = firstSerial;
        for (Object[] atom : atoms) {
            lines.add(PoseAnalysisTestData.atom(
                    serial++,
                    (String) atom[0],
                    name,
                    "A",
                    number,
                    (double) atom[1],
                    (double) atom[2],
                    (double) atom[3],
                    (double) atom[4],
                    (String) atom[5]
            ));
        }
        return serial;
    }
}
