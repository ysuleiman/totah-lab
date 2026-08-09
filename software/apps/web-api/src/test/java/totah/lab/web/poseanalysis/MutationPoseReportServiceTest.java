package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.PdbWriteOptions;
import totah.lab.hermes.file.pdb.writer.PdbWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationPoseReportServiceTest {

    // One compact pocket: the pose contacts only residue 4.
    private static final String[] NAMES_A = {
            "ALA", "LEU", "SER", "PHE", "VAL", "GLY"
    };
    private static final int[] NUMBERS = {1, 2, 3, 4, 5, 6};
    private static final double[][] CA_A = {
            {0, 0, 0},
            {10, 1, 1},
            {2, 8, 1},
            {5, 3, 9},
            {14, 7, 4},
            {3, 12, 6}
    };
    private static final double[][] POCKET_SPHERES = {
            {5, 3, 9},
            {4, 2, 8},
            {6, 4, 10},
            {5, 5, 9},
            {5, 1, 9},
            {4, 4, 10},
            {6, 2, 8},
            {5, 3, 7}
    };
    private static final double[][] WT_POSE = {
            {5, 3, 7},
            {5, 3, 11},
            {3, 3, 9},
            {7, 3, 9}
    };
    // 7B: the whole world rigidly shifted +0.5 in x.
    private static final double SHIFT = 0.5;

    @TempDir
    Path directory;

    private StubPoseAnalysisRepository repository;
    private Path mutantDir;

    @BeforeEach
    void setUp() throws IOException {
        repository = new StubPoseAnalysisRepository();

        Files.writeString(
                directory.resolve("receptor-7a.pdbqt"),
                PoseAnalysisTestData.receptorPdbqt(NAMES_A, NUMBERS, CA_A)
        );
        Files.writeString(
                directory.resolve("receptor-7b.pdbqt"),
                PoseAnalysisTestData.receptorPdbqt(
                        NAMES_A, NUMBERS, shifted(CA_A))
        );
        repository.addRun(5, 1, 1, "receptor-7a", "TESTA");
        repository.addRun(6, 2, 2, "receptor-7b", "TESTB");
        repository.addStructureArtifact(
                1,
                101,
                "AF-TESTA-F1-model_v6",
                directory.resolve("receptor-7a.pdbqt").toString()
        );
        repository.addStructureArtifact(
                2,
                102,
                "AF-TESTB-F1-model_v6",
                directory.resolve("receptor-7b.pdbqt").toString()
        );

        Path poseA = directory.resolve("pose-7a.pdbqt");
        Files.writeString(poseA, PoseAnalysisTestData.posePdbqt(WT_POSE));
        Path poseB = directory.resolve("pose-7b.pdbqt");
        Files.writeString(
                poseB,
                PoseAnalysisTestData.posePdbqt(shifted(WT_POSE))
        );
        repository.addPose(5, 7, "DCMB",
                "DCMB-R diffdock 7A rank1 conf-0.497", -7.0,
                poseA.toString());
        repository.addPose(6, 9, "DCMB",
                "DCMB-R diffdock 7B rank1 conf-0.813", -8.0,
                poseB.toString());

        pocketOnStructure(1, 1, POCKET_SPHERES);
        pocketOnStructure(2, 2, shifted(POCKET_SPHERES));

        mutantDir = directory.resolve("diffdock_TEST-F4L");
        writeMutantDir(mutantDir, "LEU");
    }

    @Test
    void producesTheComparisonClassificationAndConfidenceDelta()
            throws IOException {
        String report = service().report(
                List.of(entry(mutantDir)),
                7,
                9,
                1,
                List.of("F4L", "F9L")
        );

        // Availability: F4L analyzed, F9L reported missing.
        assertTrue(report.contains("F4L — analyzed"), report);
        assertTrue(report.contains(
                "F9L — MISSING: no local DiffDock data"), report);

        // Frame check: the mutant receptor is CA-identical to WT.
        assertTrue(report.contains("median displacement 0.000 A"),
                report);
        assertTrue(report.contains("identical to the WT 7A frame"),
                report);

        // Same-frame comparison vs WT 7A: every pose atom moved 0.3 A.
        // The shift crosses the 8 A shell threshold on residue 2, so
        // the contact set gains A:2 (Jaccard 1/2).
        assertTrue(report.contains("=== F4L —"), report);
        assertTrue(report.contains("RMSD 0.300 A"), report);
        assertTrue(report.contains("centroid shift 0.300 A"), report);
        assertTrue(report.contains("contact Jaccard 0.500"), report);
        assertTrue(report.contains(
                "contacts retained: A:4; gained: A:2; lost: -"),
                report);

        // Pocket before/after and the 7B-side aligned metrics.
        assertTrue(report.contains("Pocket: WT 7A FPOCKET pocket 1"
                + " (ASSIGNED) -> mutant FPOCKET pocket 1 (ASSIGNED)"),
                report);
        assertTrue(report.contains("aligned centroid shift"), report);

        // Classification line and the confidence delta (data only).
        assertTrue(report.contains(
                "Classification: computationally the pose is"), report);
        assertTrue(report.contains(
                "Confidence (DiffDock): WT 7A -0.497 -> mutant -0.550"),
                report);
        assertTrue(report.contains("-0.813"), report);
    }

    @Test
    void pairwiseSectionComparesTwoMutants() throws IOException {
        Path secondMutant = directory.resolve("diffdock_TESTB-F4M");
        writeMutantDir(secondMutant, "MET");

        String report = service().report(
                List.of(entry(mutantDir), entry(secondMutant)),
                7,
                9,
                1,
                List.of("F4L", "F4M")
        );

        assertTrue(report.contains("=== F4L-vs-F4M"), report);
        assertTrue(report.contains("both poses in the 7A frame"),
                report);
    }

    @Test
    void labelOverrideSyntaxParsesPathAndExplicitLabel() {
        MutationPoseReportService.MutantDirEntry override =
                MutationPoseReportService.MutantDirEntry.parse(
                        "/x/diffdock_wall=F39L+L40M+V41A+R42V+F43L");
        assertEquals(Path.of("/x/diffdock_wall"), override.directory());
        assertEquals("F39L+L40M+V41A+R42V+F43L",
                override.labelOverride());

        MutationPoseReportService.MutantDirEntry plain =
                MutationPoseReportService.MutantDirEntry.parse(
                        "/x/diffdock_METTL7A-F43L");
        assertEquals(Path.of("/x/diffdock_METTL7A-F43L"),
                plain.directory());
        assertEquals(null, plain.labelOverride());

        assertThrows(IllegalStateException.class,
                () -> MutationPoseReportService.MutantDirEntry.parse(
                        "/x/dir="));
        assertThrows(IllegalStateException.class,
                () -> MutationPoseReportService.MutantDirEntry.parse(
                        "/x/dir=NOTALABEL"));
    }

    @Test
    void compositeLabelVerifiesEveryMutatedPosition()
            throws IOException {
        // Directory name carries no parseable label; the override
        // supplies a composite one. WT fixture: PHE at 4, VAL at 5.
        Path wall = directory.resolve("diffdock_wall");
        writeMutantDir(wall, java.util.Map.of(4, "LEU", 5, "ALA"));

        String report = service().report(
                List.of(MutationPoseReportService.MutantDirEntry.parse(
                        wall + "=F4L+V5A")),
                7,
                9,
                1,
                List.of("F4L+V5A")
        );

        assertTrue(report.contains("=== F4L+V5A —"), report);
        assertTrue(report.contains(
                "LEU at A:4, ALA at A:5 (2 substitutions)"), report);
        // Both mutated positions are excluded from the frame check:
        // 6 residues minus 2 mutated = 4 shared CA atoms.
        assertTrue(report.contains("frame check: 4 shared CA atoms"),
                report);
        assertTrue(report.contains("F4L+V5A — analyzed"), report);
    }

    @Test
    void parsesBothDirectoryLabelPatterns() {        assertEquals("F43L", MutationPoseReportService
                .mutationLabelFromDir("diffdock_METTL7A-F43L"));
        assertEquals("L40M", MutationPoseReportService
                .mutationLabelFromDir("diffdock_7a_lm40"));
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> MutationPoseReportService
                        .mutationLabelFromDir("no-mutation-here")
        );
        assertTrue(exception.getMessage().contains(
                "Cannot parse a mutation label"),
                exception.getMessage());
    }

    @Test
    void missingDirectoryFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report(
                        List.of(entry(directory.resolve("no-such-dir"))),
                        7, 9, 1, List.of())
        );
        assertTrue(exception.getMessage().contains(
                "Mutant directory does not exist"),
                exception.getMessage());
    }

    @Test
    void missingRankFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report(
                        List.of(entry(mutantDir)), 7, 9, 2, List.of())
        );
        assertTrue(exception.getMessage().contains(
                "no rank 2 pose SDF"),
                exception.getMessage());
    }

    @Test
    void mutantResidueMismatchFailsWithAClearMessage() {
        Path mismatch = directory.resolve("diffdock_TEST-F4S");
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> {
                    writeMutantDir(mismatch, "LEU");
                    service().report(
                            List.of(entry(mismatch)), 7, 9, 1, List.of());
                }
        );
        assertTrue(exception.getMessage().contains(
                "expected SER"),
                exception.getMessage());
    }

    private MutationPoseReportService service() {
        return new MutationPoseReportService(
                new PoseAnalysisService(repository, directory.toString()),
                repository
        );
    }

    private static MutationPoseReportService.MutantDirEntry entry(
            Path directory
    ) {
        return new MutationPoseReportService.MutantDirEntry(
                directory, null);
    }

    /**
     * A DiffDock-style directory: target_protein.pdb (the WT CA
     * geometry with the residue at position 4 replaced) and one rank-1
     * pose SDF (the WT pose atoms moved 0.3 A in x).
     */
    private static void writeMutantDir(Path dir, String residueName)
            throws IOException {
        writeMutantDir(dir, java.util.Map.of(4, residueName));
    }

    /** As above, with one replacement residue name per position. */
    private static void writeMutantDir(
            Path dir,
            java.util.Map<Integer, String> namesByPosition
    ) throws IOException {
        Files.createDirectories(dir);
        new PdbWriter().write(
                mutantReceptor(namesByPosition),
                dir.resolve("target_protein.pdb"),
                PdbWriteOptions.defaults()
        );
        Files.writeString(
                dir.resolve("rank1_confidence-0.55.sdf"),
                sdf(shiftedPose(WT_POSE, 0.3))
        );
    }

    private static Structure mutantReceptor(
            java.util.Map<Integer, String> namesByPosition
    ) {
        List<Residue> residues = new ArrayList<>();
        for (int index = 0; index < NUMBERS.length; index++) {
            String name = namesByPosition.getOrDefault(
                    NUMBERS[index], NAMES_A[index]);
            double[] position = CA_A[index];
            residues.add(new Residue(name, NUMBERS[index], List.of(
                    Atom.builder()
                            .pdbSerial(index + 1)
                            .name("CA")
                            .position(new Point3D(
                                    position[0],
                                    position[1],
                                    position[2]))
                            .charge(0.0)
                            .occupancy(1.0)
                            .bFactor(0.0)
                            .element(Element.C)
                            .build()
            )));
        }
        return new Structure(List.of(new Chain("A", residues)));
    }

    private void pocketOnStructure(
            long structureId,
            long pocketId,
            double[][] spheres
    ) {
        repository.pockets.put(structureId, List.of(
                StubPoseAnalysisRepository.pocket(pocketId, 1, "FPOCKET")
        ));
        List<totah.lab.web.service.PocketAlphaSphereProjection> rows =
                new ArrayList<>();
        for (int index = 0; index < spheres.length; index++) {
            double[] sphere = spheres[index];
            rows.add(StubPoseAnalysisRepository.sphere(
                    pocketId, index,
                    sphere[0], sphere[1], sphere[2], 2.5
            ));
        }
        repository.spheres.put(structureId, rows);
        List<PosePocketResidueProjection> residues = new ArrayList<>();
        for (int index = 0; index < NUMBERS.length; index++) {
            residues.add(StubPoseAnalysisRepository.pocketResidue(
                    pocketId, "A", NUMBERS[index], NAMES_A[index]
            ));
        }
        repository.residues.put(structureId, residues);
    }

    private static double[][] shifted(double[][] positions) {
        return shiftedPose(positions, SHIFT);
    }

    private static double[][] shiftedPose(
            double[][] positions,
            double dx
    ) {
        double[][] shifted = new double[positions.length][];
        for (int index = 0; index < positions.length; index++) {
            shifted[index] = new double[]{
                    positions[index][0] + dx,
                    positions[index][1],
                    positions[index][2]
            };
        }
        return shifted;
    }

    /** Minimal V2000 SDF with one carbon per position. */
    private static String sdf(double[][] positions) {
        StringBuilder sdf = new StringBuilder();
        sdf.append("DCMB\n  test\n\n");
        sdf.append(String.format(
                Locale.ROOT,
                "%3d%3d  0  0  0  0            999 V2000",
                positions.length,
                positions.length - 1
        )).append('\n');
        for (double[] position : positions) {
            sdf.append(String.format(
                    Locale.ROOT,
                    "%10.4f%10.4f%10.4f %-3s 0  0  0  0  0  0  0  0"
                            + "  0  0  0  0",
                    position[0],
                    position[1],
                    position[2],
                    "C"
            )).append('\n');
        }
        for (int index = 1; index < positions.length; index++) {
            sdf.append(String.format(
                    Locale.ROOT,
                    "%3d%3d  1  0  0  0  0",
                    index,
                    index + 1
            )).append('\n');
        }
        sdf.append("M  END\n$$$$\n");
        return sdf.toString();
    }
}
