package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.web.service.PocketAlphaSphereProjection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifferentialContactReportServiceTest {

    // Two receptors with the same fold: positions 1:1 aligned.
    // Position 4 (LYS on A, ALA on B) sits next to the pose — a
    // charge gain/loss with contacts on both sides. Position 6
    // (GLY on A, ALA on B) differs without any contact.
    private static final String[] NAMES_A = {
            "ALA", "LEU", "SER", "LYS", "VAL", "GLY"
    };
    private static final String[] NAMES_B = {
            "ALA", "LEU", "SER", "ALA", "VAL", "ALA"
    };
    private static final int[] NUMBERS = {1, 2, 3, 4, 5, 6};
    private static final double[][] CA_POSITIONS = {
            {0, 0, 0},
            {10, 1, 1},
            {2, 8, 1},
            {5, 3, 9},
            {14, 7, 4},
            {3, 12, 6}
    };
    private static final double[][] POSE = {
            {5, 3, 7},
            {5, 3, 11},
            {3, 3, 9},
            {7, 3, 9}
    };
    private static final double[][] POCKET_SPHERES = {
            {5, 3, 9},
            {4, 2, 8},
            {6, 4, 10},
            {5, 5, 9},
            {5, 1, 9}
    };

    @TempDir
    Path directory;

    private StubPoseAnalysisRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        repository = new StubPoseAnalysisRepository();

        Files.writeString(
                directory.resolve("receptor-a.pdbqt"),
                PoseAnalysisTestData.receptorPdbqt(
                        NAMES_A, NUMBERS, CA_POSITIONS)
        );
        Files.writeString(
                directory.resolve("receptor-b.pdbqt"),
                PoseAnalysisTestData.receptorPdbqt(
                        NAMES_B, NUMBERS, CA_POSITIONS)
        );

        Path poseA = directory.resolve("pose-a.pdbqt");
        Files.writeString(poseA, PoseAnalysisTestData.posePdbqt(POSE));
        Path poseB = directory.resolve("pose-b.pdbqt");
        Files.writeString(poseB, PoseAnalysisTestData.posePdbqt(POSE));

        repository.addRun(5, 1, 1, "receptor-a", "METTL7A");
        repository.addRun(6, 2, 2, "receptor-b", "METTL7B");
        repository.addStructureArtifact(
                1,
                101,
                "AF-TESTA-F1-model_v6",
                directory.resolve("receptor-a.pdbqt").toString()
        );
        repository.addStructureArtifact(
                2,
                102,
                "AF-TESTB-F1-model_v6",
                directory.resolve("receptor-b.pdbqt").toString()
        );
        repository.addPose(5, 7, "DCMB", "DCMB vina s1 m1", -7.0,
                poseA.toString());
        repository.addPose(6, 9, "DCMB", "DCMB vina s1 m1", -6.8,
                poseB.toString());

        structureOnePocket(1, 11);
        structureOnePocket(2, 22);
    }

    private void structureOnePocket(long structureId, long pocketId) {
        repository.pockets.put(structureId, List.of(
                StubPoseAnalysisRepository.pocket(pocketId, 1, "FPOCKET")
        ));
        List<PocketAlphaSphereProjection> spheres = new ArrayList<>();
        for (int index = 0; index < POCKET_SPHERES.length; index++) {
            double[] position = POCKET_SPHERES[index];
            spheres.add(StubPoseAnalysisRepository.sphere(
                    pocketId, index,
                    position[0], position[1], position[2], 2.5
            ));
        }
        repository.spheres.put(structureId, spheres);
        List<PosePocketResidueProjection> residues = new ArrayList<>();
        String[] names = structureId == 1 ? NAMES_A : NAMES_B;
        for (int index = 0; index < NUMBERS.length; index++) {
            residues.add(StubPoseAnalysisRepository.pocketResidue(
                    pocketId, "A", NUMBERS[index], names[index]
            ));
        }
        repository.residues.put(structureId, residues);
    }

    @Test
    void producesTheAlignedTableAndRankedCandidates() {
        String report = service().report("DCMB", 1, 2, null, null);

        // Header: both receptors with their default poses and pockets.
        assertTrue(report.contains(
                "Receptor A: METTL7A (receptor 1), run 5, pose 7"),
                report);
        assertTrue(report.contains(
                "Receptor B: METTL7B (receptor 2), run 6, pose 9"),
                report);
        assertTrue(report.contains("assigned pocket: FPOCKET pocket 1"),
                report);

        // A/B contact maps: only position 4 contacts, K on A, A on B.
        assertTrue(report.contains("A: K"), report);
        assertTrue(report.contains("A residues: 4"), report);
        assertTrue(report.contains("B: A"), report);
        assertTrue(report.contains("B residues: 4"), report);
        assertTrue(report.contains("diff: ."), report);

        // C: the aligned table carries the divergent contact position
        // and the divergent non-contact position.
        assertTrue(report.contains("LYS 4"), report);
        assertTrue(report.contains(
                "CONTACT_BOTH_DIFFERENT_RESIDUE"), report);
        assertTrue(report.contains("NONCONTACT_DIFFERENCE"), report);
        assertTrue(report.contains("RADICAL (charge gain/loss)"),
                report);
        assertTrue(report.contains("wall"), report);

        // D: the mapping behind the strings.
        assertTrue(report.contains("4 | 4 | LYS | 4 | ALA"), report);

        // E: both single-substitution candidates at tier 1 (direct
        // contact + charge gain/loss), A->B first.
        assertTrue(report.contains("1 | A->B | K4A"), report);
        assertTrue(report.contains("1 | B->A | A4K"), report);
        assertTrue(report.indexOf("K4A") < report.indexOf("A4K"),
                report);
        // Position 6 has no contact: no G6A/A6G candidates.
        assertTrue(!report.contains("G6A"), report);
    }

    @Test
    void poseIdOverrideReplacesTheDefaultPose() throws IOException {
        Path alternative = directory.resolve("pose-a-alt.pdbqt");
        Files.writeString(
                alternative,
                PoseAnalysisTestData.posePdbqt(POSE)
        );
        repository.addPose(5, 8, "DCMB", "DCMB vina s2 m1", -6.0,
                alternative.toString());

        String defaultReport = service().report("DCMB", 1, 2, null, null);
        assertTrue(defaultReport.contains("run 5, pose 7"),
                defaultReport);

        String overridden = service().report("DCMB", 1, 2, 8L, null);
        assertTrue(overridden.contains("run 5, pose 8"), overridden);
    }

    @Test
    void unknownLigandFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report("NOPE", 1, 2, null, null)
        );
        assertTrue(exception.getMessage().contains(
                "No docking runs of ligand NOPE for receptor 1"),
                exception.getMessage());
    }

    @Test
    void unknownPoseOverrideFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report("DCMB", 1, 2, 99L, null)
        );
        assertTrue(exception.getMessage().contains("No docking pose 99"),
                exception.getMessage());
    }

    @Test
    void poseOverrideFromTheWrongReceptorFailsWithAClearMessage() {
        // Pose 9 belongs to receptor 2's run; overriding receptor A
        // with it is an input error, not a comparison.
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report("DCMB", 1, 2, 9L, null)
        );
        assertTrue(exception.getMessage().contains(
                "belongs to receptor 2, not receptor 1"),
                exception.getMessage());
    }

    @Test
    void missingReceptorArtifactFailsWithAClearMessage() {
        repository.addRun(5, 1, 1, "no-such-artifact", "METTL7A");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report("DCMB", 1, 2, null, null)
        );
        assertTrue(exception.getMessage().contains("cannot be loaded"),
                exception.getMessage());
    }

    private DifferentialContactReportService service() {
        return new DifferentialContactReportService(
                new PoseAnalysisService(repository, directory.toString()),
                repository
        );
    }
}
