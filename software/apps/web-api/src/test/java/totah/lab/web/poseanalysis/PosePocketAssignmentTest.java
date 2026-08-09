package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.web.poseanalysis.PoseAnalysisView.PosePocketAssignmentView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosePocketAssignmentTest {

    private static final String[] RESIDUE_NAMES = {
            "ALA", "LEU", "SER", "LYS", "VAL", "GLY", "ALA"
    };
    private static final int[] RESIDUE_NUMBERS = {1, 2, 3, 4, 5, 6, 7};
    private static final double[][] RESIDUE_POSITIONS = {
            {0, 0, 0},
            {9, 1, 2},
            {2, 8, 1},
            {5, 3, 9},
            {11, 7, 4},
            {3, 12, 6},
            {100, 100, 100}
    };
    private static final double[][] POCKET_ONE_SPHERES = {
            {1, 1, 1},
            {4, 2, 0},
            {2, 5, 3},
            {7, 4, 2},
            {3, 3, 7},
            {9, 6, 5},
            {5, 8, 4},
            {0, 4, 6}
    };
    private static final double[][] POCKET_TWO_SPHERES = {
            {99, 99, 99},
            {101, 100, 100},
            {100, 101, 99},
            {98, 100, 101}
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
                StubPoseAnalysisRepository.pocket(1, 1, "FPOCKET"),
                StubPoseAnalysisRepository.pocket(2, 2, "FPOCKET")
        ));
        repository.spheres.put(1L, java.util.stream.Stream.concat(
                spheres(1, POCKET_ONE_SPHERES).stream(),
                spheres(2, POCKET_TWO_SPHERES).stream()
        ).toList());
        repository.residues.put(1L, List.of(
                StubPoseAnalysisRepository.pocketResidue(1, "A", 1, "ALA"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 2, "LEU"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 3, "SER"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 4, "LYS"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 5, "VAL"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 6, "GLY"),
                StubPoseAnalysisRepository.pocketResidue(2, "A", 7, "ALA")
        ));
    }

    @Test
    void poseInsideAPocketIsAssignedWithComponentMetrics()
            throws IOException {
        Path poseFile = directory.resolve("pose-inside.pdbqt");
        Files.writeString(
                poseFile,
                PoseAnalysisTestData.posePdbqt(INSIDE_POSE)
        );
        repository.addPose(5, 7, "LIG vina s1 m1", -7.0,
                poseFile.toString());

        PosePocketAssignmentView view = service().pocketAssignment(7);

        assertTrue(view.available());
        assertNull(view.unavailableReason());
        assertEquals(7, view.poseId());
        assertEquals("LIG vina s1 m1", view.label());
        assertEquals(-7.0, view.score(), 1.0e-9);
        assertEquals("ASSIGNED", view.status());
        assertFalse(view.ambiguous());
        assertNotNull(view.reason());
        // Vina affinity and assignment score stay separate fields.
        assertNotNull(view.assignmentScore());
        assertTrue(view.assignmentScore() > 0.0);
        assertTrue(view.assignmentScore() != view.score());

        assertNotNull(view.assignedPocket());
        assertEquals(1, view.assignedPocket().pocketId());
        assertEquals(1, view.assignedPocket().pocketNumber());
        assertEquals("FPOCKET", view.assignedPocket().source());

        assertNotNull(view.secondBestPocket());
        assertEquals(2, view.secondBestPocket().pocketId());
        assertNotNull(view.secondBestScore());
        assertEquals(
                view.assignmentScore() - view.secondBestScore(),
                view.scoreMargin(),
                1.0e-9
        );

        assertNotNull(view.metrics());
        assertEquals("ALPHA_SPHERES", view.metrics().containmentBasis());
        assertEquals(1.0, view.metrics().atomContainmentFraction(),
                1.0e-9);
        assertEquals(1.0, view.metrics().atomWithin2AOfSphereFraction(),
                1.0e-9);
        assertEquals(1.0, view.metrics().atomWithin3AOfSphereFraction(),
                1.0e-9);
        assertTrue(view.metrics().ligandCentroidDistance() >= 0.0);
        assertTrue(view.metrics().meanNearestSphereDistance() >= 0.0);
        assertTrue(view.metrics().maxNearestSphereDistance()
                >= view.metrics().meanNearestSphereDistance());
        assertTrue(view.metrics().contactResidueCoverage() >= 0.0);
        assertTrue(view.metrics().pocketContactCoverage() >= 0.0);
    }

    @Test
    void poseOutsideAllPocketsIsNotAssigned() throws IOException {
        Path poseFile = directory.resolve("pose-outside.pdbqt");
        Files.writeString(
                poseFile,
                PoseAnalysisTestData.posePdbqt(OUTSIDE_POSE)
        );
        repository.addPose(5, 8, "LIG vina s1 m1", -5.0,
                poseFile.toString());

        PosePocketAssignmentView view = service().pocketAssignment(8);

        assertTrue(view.available());
        assertEquals("NOT_ASSIGNED", view.status());
        assertNull(view.assignedPocket());
        assertNull(view.assignmentScore());
        assertFalse(view.ambiguous());
        assertNotNull(view.reason());
        // The rejected evidence stays visible.
        assertNotNull(view.metrics());
        assertEquals(0.0, view.metrics().atomContainmentFraction(),
                1.0e-9);
    }

    @Test
    void structureWithoutPocketsYieldsNoCandidates() throws IOException {
        repository.pockets.put(1L, List.of());
        repository.spheres.put(1L, List.of());
        repository.residues.put(1L, List.of());
        Path poseFile = directory.resolve("pose-inside.pdbqt");
        Files.writeString(
                poseFile,
                PoseAnalysisTestData.posePdbqt(INSIDE_POSE)
        );
        repository.addPose(5, 7, "LIG vina s1 m1", -7.0,
                poseFile.toString());

        PosePocketAssignmentView view = service().pocketAssignment(7);

        assertTrue(view.available());
        assertEquals("NOT_ASSIGNED", view.status());
        assertEquals("no candidate pockets", view.reason());
        assertNull(view.assignedPocket());
        assertNull(view.metrics());
    }

    @Test
    void missingReceptorArtifactDegradesWithReason() {
        repository.addRun(5, 1, "no-such-artifact", "METTL7B");
        repository.addPose(5, 7, "LIG vina s1 m1", -7.0,
                directory.resolve("pose.pdbqt").toString());

        PosePocketAssignmentView view = service().pocketAssignment(7);

        assertFalse(view.available());
        assertNotNull(view.unavailableReason());
        assertNull(view.status());
        assertNull(view.assignedPocket());
        assertNull(view.metrics());
    }

    private PoseAnalysisService service() {
        return new PoseAnalysisService(repository, directory.toString());
    }

    private static List<totah.lab.web.service.PocketAlphaSphereProjection>
            spheres(long pocketId, double[][] positions) {
        List<totah.lab.web.service.PocketAlphaSphereProjection> spheres =
                new java.util.ArrayList<>();
        for (int index = 0; index < positions.length; index++) {
            double[] position = positions[index];
            spheres.add(StubPoseAnalysisRepository.sphere(
                    pocketId,
                    index,
                    position[0],
                    position[1],
                    position[2],
                    2.5
            ));
        }
        return spheres;
    }
}
