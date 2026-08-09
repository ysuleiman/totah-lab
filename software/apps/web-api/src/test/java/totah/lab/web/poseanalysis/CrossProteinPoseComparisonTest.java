package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.web.poseanalysis.PoseAnalysisView.CrossProteinPoseComparisonView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossProteinPoseComparisonTest {

    // Same compact, non-collinear layout as the athena comparator's
    // own fixtures: six CA residues, eight alpha spheres, a four-atom
    // pose inside the sphere cluster.
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
    private static final double[][] POSE = {
            {3, 3, 2},
            {4, 4, 3},
            {5, 3, 4},
            {4, 2, 3}
    };

    @TempDir
    Path directory;

    private StubPoseAnalysisRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        repository = new StubPoseAnalysisRepository();
        String receptor = PoseAnalysisTestData.receptorPdbqt(
                RESIDUE_NAMES,
                RESIDUE_NUMBERS,
                RESIDUE_POSITIONS
        );
        Files.writeString(directory.resolve("receptor-1.pdbqt"), receptor);
        Files.writeString(directory.resolve("receptor-2.pdbqt"), receptor);

        Path queryPose = directory.resolve("pose-query.pdbqt");
        Files.writeString(queryPose, PoseAnalysisTestData.posePdbqt(POSE));
        Path candidatePose = directory.resolve("pose-candidate.pdbqt");
        Files.writeString(
                candidatePose,
                PoseAnalysisTestData.posePdbqt(POSE)
        );

        // Two receptors (two structures) with the same fold: query run
        // 5 on structure 1, candidate run 6 on structure 2.
        repository.addRun(5, 1, "receptor-1", "METTL7B");
        repository.addRun(6, 2, "receptor-2", "METTL7A");
        repository.addStructureArtifact(
                1,
                101,
                "AF-TESTB-F1-model_v6",
                directory.resolve("receptor-1.pdbqt").toString()
        );
        repository.addStructureArtifact(
                2,
                102,
                "AF-TESTA-F1-model_v6",
                directory.resolve("receptor-2.pdbqt").toString()
        );
        repository.addPose(5, 7, "LIG vina s1 m1", -7.0,
                queryPose.toString());
        repository.addPose(6, 8, "LIG vina s1 m1", -6.8,
                candidatePose.toString());

        for (long structureId : new long[]{1L, 2L}) {
            long pocketId = structureId;
            repository.pockets.put(structureId, List.of(
                    StubPoseAnalysisRepository.pocket(
                            pocketId, 1, "FPOCKET")
            ));
            List<totah.lab.web.service.PocketAlphaSphereProjection>
                    spheres = new ArrayList<>();
            for (int index = 0; index < POCKET_SPHERES.length; index++) {
                double[] position = POCKET_SPHERES[index];
                spheres.add(StubPoseAnalysisRepository.sphere(
                        pocketId,
                        index,
                        position[0],
                        position[1],
                        position[2],
                        2.5
                ));
            }
            repository.spheres.put(structureId, spheres);
            repository.residues.put(structureId, List.of(
                    StubPoseAnalysisRepository.pocketResidue(
                            pocketId, "A", 1, "ALA"),
                    StubPoseAnalysisRepository.pocketResidue(
                            pocketId, "A", 2, "LEU"),
                    StubPoseAnalysisRepository.pocketResidue(
                            pocketId, "A", 3, "SER"),
                    StubPoseAnalysisRepository.pocketResidue(
                            pocketId, "A", 4, "LYS"),
                    StubPoseAnalysisRepository.pocketResidue(
                            pocketId, "A", 5, "VAL"),
                    StubPoseAnalysisRepository.pocketResidue(
                            pocketId, "A", 6, "GLY")
            ));
        }
    }

    @Test
    void identicalSitesAreSameHomologousSiteWithZeroAlignedDistance() {
        CrossProteinPoseComparisonView view =
                service().crossProteinComparison(7, 8);

        assertTrue(view.available());
        assertNull(view.unavailableReason());

        assertNotNull(view.query());
        assertEquals(7, view.query().poseId());
        assertEquals(-7.0, view.query().score(), 1.0e-9);
        assertEquals("METTL7B", view.query().target());
        assertNotNull(view.query().assignedPocket());
        assertEquals(1, view.query().assignedPocket().pocketId());

        assertNotNull(view.candidate());
        assertEquals(8, view.candidate().poseId());
        assertEquals("METTL7A", view.candidate().target());
        assertNotNull(view.candidate().assignedPocket());
        assertEquals(2, view.candidate().assignedPocket().pocketId());

        assertEquals("SAME_HOMOLOGOUS_SITE", view.relationship());
        assertTrue(view.pocketsStructurallyHomologous());
        assertNotNull(view.pocketSimilarity());
        assertTrue(view.pocketSimilarity() >= 0.3);
        assertNotNull(view.alignedLigandCentroidDistance());
        assertEquals(0.0, view.alignedLigandCentroidDistance(), 1.0e-6);
        assertNotNull(view.alignedLigandRmsd());
        assertEquals(0.0, view.alignedLigandRmsd(), 1.0e-6);
        assertTrue(view.sharedAlignedContactResidues() >= 0);
        assertTrue(view.contactResidueSimilarity() >= 0.0
                && view.contactResidueSimilarity() <= 1.0);
        assertNotNull(view.reason());
    }

    @Test
    void missingCandidateReceptorDegradesWithReason() {
        repository.addRun(6, 2, "no-such-artifact", "METTL7A");

        CrossProteinPoseComparisonView view =
                service().crossProteinComparison(7, 8);

        assertFalse(view.available());
        assertNotNull(view.unavailableReason());
        assertNull(view.query());
        assertNull(view.candidate());
        assertNull(view.relationship());
        assertNull(view.pocketSimilarity());
    }

    private PoseAnalysisService service() {
        return new PoseAnalysisService(repository, directory.toString());
    }
}
