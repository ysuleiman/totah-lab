package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.web.poseanalysis.PoseAnalysisView.PocketOccupancyEntryView;
import totah.lab.web.poseanalysis.PoseAnalysisView.PocketOccupancyView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosePocketOccupancyTest {

    private static final String[] RESIDUE_NAMES = {
            "ALA", "LEU", "SER", "LYS", "VAL", "GLY"
    };
    private static final int[] RESIDUE_NUMBERS = {1, 2, 3, 4, 5, 6};
    private static final double[][] RESIDUE_POSITIONS = {
            {0, 0, 0},
            {9, 1, 2},
            {2, 8, 1},
            {5, 3, 9},
            {11, 7, 4},
            {3, 12, 6}
    };
    private static final double[][] POCKET_SPHERES = {
            {1, 1, 1},
            {4, 2, 0},
            {2, 5, 3},
            {7, 4, 2},
            {3, 3, 7},
            {9, 6, 5},
            {5, 8, 4},
            {0, 4, 6}
    };
    private static final double[][] INSIDE_POSE = {
            {3, 3, 2},
            {4, 4, 3},
            {5, 3, 4},
            {4, 2, 3}
    };
    private static final double[][] OUTSIDE_POSE = {
            {200, 200, 200},
            {202, 201, 200}
    };

    @TempDir
    Path directory;

    private StubPoseAnalysisRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        repository = new StubPoseAnalysisRepository();
        Files.writeString(
                directory.resolve("receptor-1.pdbqt"),
                PoseAnalysisTestData.receptorPdbqt(
                        RESIDUE_NAMES,
                        RESIDUE_NUMBERS,
                        RESIDUE_POSITIONS
                )
        );
        repository.addRun(5, 1, "receptor-1", "METTL7B");
        repository.addStructureArtifact(
                1,
                101,
                "AF-TEST-F1-model_v6",
                directory.resolve("receptor-1.pdbqt").toString()
        );
        repository.pockets.put(1L, List.of(
                StubPoseAnalysisRepository.pocket(1, 1, "FPOCKET")
        ));
        List<totah.lab.web.service.PocketAlphaSphereProjection> spheres =
                new java.util.ArrayList<>();
        for (int index = 0; index < POCKET_SPHERES.length; index++) {
            double[] position = POCKET_SPHERES[index];
            spheres.add(StubPoseAnalysisRepository.sphere(
                    1, index, position[0], position[1], position[2], 2.5
            ));
        }
        repository.spheres.put(1L, spheres);
        repository.residues.put(1L, List.of(
                StubPoseAnalysisRepository.pocketResidue(1, "A", 1, "ALA"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 2, "LEU"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 3, "SER"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 4, "LYS"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 5, "VAL"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 6, "GLY")
        ));

        Path insideOne = directory.resolve("pose-1.pdbqt");
        Files.writeString(
                insideOne,
                PoseAnalysisTestData.posePdbqt(INSIDE_POSE)
        );
        Path insideTwo = directory.resolve("pose-2.pdbqt");
        Files.writeString(
                insideTwo,
                PoseAnalysisTestData.posePdbqt(INSIDE_POSE)
        );
        Path outside = directory.resolve("pose-3.pdbqt");
        Files.writeString(
                outside,
                PoseAnalysisTestData.posePdbqt(OUTSIDE_POSE)
        );
        repository.addPose(5, 7, "LIG vina s1 m1", -7.0,
                insideOne.toString());
        repository.addPose(5, 8, "LIG vina s2 m1", -6.5,
                insideTwo.toString());
        repository.addPose(5, 9, "LIG vina s3 m1", -5.0,
                outside.toString());
    }

    @Test
    void aggregatesPosesByAssignedPocket() {
        PocketOccupancyView view = service().pocketOccupancy(5);

        assertTrue(view.available());
        assertEquals(5, view.runId());
        assertEquals(1, view.notAssignedCount());
        assertEquals(0, view.ambiguousCount());
        assertEquals(1, view.entries().size());

        PocketOccupancyEntryView entry = view.entries().getFirst();
        assertEquals(1, entry.pocketId());
        assertEquals(1, entry.pocketNumber());
        assertEquals("FPOCKET", entry.source());
        assertEquals(2, entry.poseCount());
        assertEquals(2.0 / 3.0, entry.fractionOfPoses(), 1.0e-9);
        // Vina affinities of the assigned poses, separate from the
        // assignment scores.
        assertEquals(-7.0, entry.bestAffinity(), 1.0e-9);
        assertEquals(-6.75, entry.medianAffinity(), 1.0e-9);
        assertTrue(entry.bestAssignmentScore()
                >= entry.meanAssignmentScore());
        assertTrue(entry.meanAssignmentScore() > 0.0);
        assertEquals(
                List.of("LIG vina s1 m1", "LIG vina s2 m1"),
                entry.poseLabels()
        );
    }

    @Test
    void missingReceptorArtifactDegradesWithReason() {
        repository.addRun(5, 1, "no-such-artifact", "METTL7B");

        PocketOccupancyView view = service().pocketOccupancy(5);

        assertFalse(view.available());
        assertNotNull(view.unavailableReason());
        assertEquals(List.of(), view.entries());
        assertEquals(0, view.notAssignedCount());
        assertEquals(0, view.ambiguousCount());
    }

    private PoseAnalysisService service() {
        return new PoseAnalysisService(repository, directory.toString());
    }
}
