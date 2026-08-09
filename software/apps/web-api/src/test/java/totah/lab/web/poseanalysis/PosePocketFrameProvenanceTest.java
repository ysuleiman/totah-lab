package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.web.poseanalysis.PoseAnalysisView.PosePocketAssignmentView;
import totah.lab.web.service.PocketAlphaSphereProjection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the METTL7B mixed-frame bug: sphere-derived
 * metrics must only ever come from the same artifact or a validated
 * rigid transform — never from an assumed shared frame.
 */
class PosePocketFrameProvenanceTest {

    private static final int RESIDUE_COUNT = 40;
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
    private static final double[][] POSE = {
            {5, 3, 7},
            {5, 3, 11},
            {3, 3, 9},
            {7, 3, 9}
    };
    private static final String[] NAMES = {
            "ALA", "LEU", "SER", "LYS", "VAL", "GLY", "PHE", "ARG"
    };

    @TempDir
    Path directory;

    private StubPoseAnalysisRepository repository;
    private double[][] residuePositions;

    @BeforeEach
    void setUp() throws IOException {
        repository = new StubPoseAnalysisRepository();
        residuePositions = residuePositions();
        Files.writeString(
                directory.resolve("receptor-1.pdbqt"),
                PoseAnalysisTestData.receptorPdbqt(
                        names(), numbers(), residuePositions)
        );
        Path pose = directory.resolve("pose.pdbqt");
        Files.writeString(pose, PoseAnalysisTestData.posePdbqt(POSE));
        repository.addRun(5, 1, "receptor-1", "TESTA");
        repository.addPose(5, 7, "LIG vina s1 m1", -7.0,
                pose.toString());
        repository.pockets.put(1L, List.of(
                StubPoseAnalysisRepository.pocket(1, 1, "FPOCKET")
        ));
        List<PosePocketResidueProjection> residues = new ArrayList<>();
        for (int number = 1; number <= RESIDUE_COUNT; number++) {
            residues.add(StubPoseAnalysisRepository.pocketResidue(
                    1, "A", number, "ALA"));
        }
        repository.residues.put(1L, residues);
        registerSpheres(POCKET_SPHERES);
    }

    /** A: receptor artifact == pocket structure artifact. */
    @Test
    void identicalArtifactAllowsSphereMetrics() {
        repository.addStructureArtifact(
                1, 101, "AF-TEST-F1-model_v6",
                directory.resolve("receptor-1.pdbqt").toString());

        PosePocketAssignmentView view = service().pocketAssignment(7);

        assertTrue(view.available());
        assertEquals("ASSIGNED", view.status());
        assertEquals("IDENTICAL_ARTIFACT",
                view.provenance().compatibility());
        assertEquals("AVAILABLE", view.provenance().sphereMetrics());
        assertEquals("v6",
                view.provenance().pocketStructureArtifact()
                        .modelVersion());
        assertEquals("ALPHA_SPHERES", view.metrics().containmentBasis());
        assertEquals(1.0, view.metrics().atomContainmentFraction(),
                1.0e-9);
        assertNotNull(view.metrics().atomWithin2AOfSphereFraction());
    }

    /**
     * C: different artifact, validated rigid transform (5 A
     * translation) — sphere metrics allowed through the transform and
     * labeled.
     */
    @Test
    void validatedTransformAllowsTransformedSphereMetrics()
            throws IOException {
        registerShiftedArtifact(5.0);

        PosePocketAssignmentView view = service().pocketAssignment(7);

        assertTrue(view.available());
        assertEquals("VALIDATED_TRANSFORM",
                view.provenance().compatibility());
        assertEquals("AVAILABLE", view.provenance().sphereMetrics());
        assertEquals(RESIDUE_COUNT,
                view.provenance().transformMatchedPairs());
        assertNotNull(view.provenance().transformRmsdAngstroms());
        assertTrue(view.provenance().note().contains("transformed"),
                view.provenance().note());
        // Spheres were moved into the receptor frame: the pose fully
        // occupies the pocket again.
        assertEquals("ALPHA_SPHERES", view.metrics().containmentBasis());
        assertEquals(1.0, view.metrics().atomContainmentFraction(),
                1.0e-9);
    }

    /**
     * D: a 27 A translation between otherwise identical structures is
     * never silently compatible — it only ever passes as an explicit
     * VALIDATED_TRANSFORM with the translation on record.
     */
    @Test
    void largeTranslationIsNeverSilentlyCompatible() throws IOException {
        registerShiftedArtifact(27.0);

        PosePocketAssignmentView view = service().pocketAssignment(7);

        assertTrue(view.available());
        assertEquals("VALIDATED_TRANSFORM",
                view.provenance().compatibility());
        assertTrue(view.provenance().note().contains("translation"),
                view.provenance().note());
        assertTrue(view.provenance().note().contains("27"),
                view.provenance().note());
    }

    /**
     * B: same accession, same residue numbering, genuinely different
     * coordinates (scaled copy) — the CA fit rejects the frame and the
     * sphere metrics are NOT_AVAILABLE.
     */
    @Test
    void differentCoordinatesAreIncompatible() throws IOException {
        Path scaled = directory.resolve("structure-scaled.pdbqt");
        Files.writeString(scaled, PoseAnalysisTestData.receptorPdbqt(
                names(), numbers(), scaled(residuePositions, 1.5)));
        repository.addStructureArtifact(
                1, 103, "AF-TEST-F1-model_v6", scaled.toString());

        PosePocketAssignmentView view = service().pocketAssignment(7);

        assertTrue(view.available());
        assertEquals("INCOMPATIBLE",
                view.provenance().compatibility());
        assertEquals("NOT_AVAILABLE",
                view.provenance().sphereMetrics());
        assertTrue(view.provenance().note()
                        .contains("INVALID_MIXED_FRAME"),
                view.provenance().note());
        // No sphere basis and no sphere metrics — the containment
        // falls back to frame-independent residue atoms.
        assertEquals("RESIDUE_ATOMS",
                view.metrics().containmentBasis());
        assertNull(view.metrics().atomWithin2AOfSphereFraction());
    }

    /**
     * E + F: with INCOMPATIBLE frames the contact-residue coverage is
     * still computed (it is frame-independent) and the output carries
     * the incompatibility label instead of normal-looking sphere
     * metrics.
     */
    @Test
    void contactCoverageSurvivesIncompatibleFrames() throws IOException {
        Path scaled = directory.resolve("structure-scaled.pdbqt");
        Files.writeString(scaled, PoseAnalysisTestData.receptorPdbqt(
                names(), numbers(), scaled(residuePositions, 1.5)));
        repository.addStructureArtifact(
                1, 103, "AF-TEST-F1-model_v6", scaled.toString());

        PosePocketAssignmentView view = service().pocketAssignment(7);

        assertTrue(view.metrics().contactResidueCoverage() > 0.0);
        assertTrue(view.provenance().note()
                        .contains("frame-independent"),
                view.provenance().note());
        assertEquals("NOT_AVAILABLE",
                view.provenance().sphereMetrics());
    }

    private void registerShiftedArtifact(double shift)
            throws IOException {
        Path shifted = directory.resolve("structure-shifted.pdbqt");
        Files.writeString(shifted, PoseAnalysisTestData.receptorPdbqt(
                names(), numbers(), shifted(residuePositions, shift)));
        repository.addStructureArtifact(
                1, 102, "AF-TEST-F1-model_v6", shifted.toString());
        registerSpheres(shifted(POCKET_SPHERES, shift));
    }

    private void registerSpheres(double[][] spheres) {
        List<PocketAlphaSphereProjection> rows = new ArrayList<>();
        for (int index = 0; index < spheres.length; index++) {
            double[] sphere = spheres[index];
            rows.add(StubPoseAnalysisRepository.sphere(
                    1, index, sphere[0], sphere[1], sphere[2], 2.5));
        }
        repository.spheres.put(1L, rows);
    }

    private PoseAnalysisService service() {
        return new PoseAnalysisService(repository, directory.toString());
    }

    private static double[][] residuePositions() {
        double[][] positions = new double[RESIDUE_COUNT][];
        for (int index = 0; index < RESIDUE_COUNT; index++) {
            int number = index + 1;
            if (number == 4) {
                positions[index] = new double[]{5, 3, 9};
                continue;
            }
            positions[index] = new double[]{
                    (2.5 * number) % 23 + 30,
                    (3.1 * number) % 17 + 10,
                    (1.9 * number) % 19 - 20
            };
        }
        return positions;
    }

    private static String[] names() {
        String[] names = new String[RESIDUE_COUNT];
        for (int index = 0; index < RESIDUE_COUNT; index++) {
            names[index] = NAMES[index % NAMES.length];
        }
        return names;
    }

    private static int[] numbers() {
        int[] numbers = new int[RESIDUE_COUNT];
        for (int index = 0; index < RESIDUE_COUNT; index++) {
            numbers[index] = index + 1;
        }
        return numbers;
    }

    private static double[][] shifted(double[][] positions, double d) {
        double[][] shifted = new double[positions.length][];
        for (int index = 0; index < positions.length; index++) {
            shifted[index] = new double[]{
                    positions[index][0] + d,
                    positions[index][1],
                    positions[index][2]
            };
        }
        return shifted;
    }

    private static double[][] scaled(double[][] positions, double f) {
        double[][] scaled = new double[positions.length][];
        for (int index = 0; index < positions.length; index++) {
            scaled[index] = new double[]{
                    positions[index][0] * f,
                    positions[index][1] * f,
                    positions[index][2] * f
            };
        }
        return scaled;
    }
}
