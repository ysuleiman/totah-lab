package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PoseAnalysisControllerTest {

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

    @TempDir
    Path directory;

    @Test
    void pocketAssignmentEndpointExposesTheAssignmentJsonShape()
            throws Exception {
        StubPoseAnalysisRepository repository =
                new StubPoseAnalysisRepository();
        Files.writeString(
                directory.resolve("receptor-1.pdbqt"),
                PoseAnalysisTestData.receptorPdbqt(
                        RESIDUE_NAMES,
                        RESIDUE_NUMBERS,
                        RESIDUE_POSITIONS
                )
        );
        Path poseFile = directory.resolve("pose.pdbqt");
        Files.writeString(
                poseFile,
                PoseAnalysisTestData.posePdbqt(INSIDE_POSE)
        );
        repository.addRun(5, 1, "receptor-1", "METTL7B");
        repository.addStructureArtifact(
                1,
                101,
                "AF-TEST-F1-model_v6",
                directory.resolve("receptor-1.pdbqt").toString()
        );
        repository.addPose(5, 7, "LIG vina s1 m1", -7.0,
                poseFile.toString());
        repository.pockets.put(1L, List.of(
                StubPoseAnalysisRepository.pocket(1, 1, "FPOCKET")
        ));
        List<totah.lab.web.service.PocketAlphaSphereProjection> spheres =
                new ArrayList<>();
        for (int index = 0; index < POCKET_SPHERES.length; index++) {
            double[] position = POCKET_SPHERES[index];
            spheres.add(StubPoseAnalysisRepository.sphere(
                    1, index, position[0], position[1], position[2], 2.5
            ));
        }
        repository.spheres.put(1L, spheres);
        repository.residues.put(1L, List.of(
                StubPoseAnalysisRepository.pocketResidue(1, "A", 1, "ALA"),
                StubPoseAnalysisRepository.pocketResidue(1, "A", 2, "LEU")
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PoseAnalysisController(new PoseAnalysisService(
                        repository,
                        directory.toString()
                ))
        ).build();

        mockMvc.perform(get("/api/docking-poses/7/pocket-assignment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poseId").value(7))
                .andExpect(jsonPath("$.label").value("LIG vina s1 m1"))
                .andExpect(jsonPath("$.score").value(-7.0))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.reason").isString())
                .andExpect(jsonPath("$.assignedPocket.pocketId").value(1))
                .andExpect(jsonPath("$.assignedPocket.pocketNumber")
                        .value(1))
                .andExpect(jsonPath("$.assignedPocket.source")
                        .value("FPOCKET"))
                .andExpect(jsonPath("$.assignmentScore").isNumber())
                .andExpect(jsonPath("$.scoreMargin").isNumber())
                .andExpect(jsonPath("$.ambiguous").value(false))
                .andExpect(jsonPath("$.metrics.containmentBasis")
                        .value("ALPHA_SPHERES"))
                .andExpect(jsonPath("$.metrics.atomContainmentFraction")
                        .value(1.0))
                .andExpect(jsonPath(
                        "$.metrics.atomWithin2AOfSphereFraction")
                        .value(1.0))
                .andExpect(jsonPath("$.metrics.ligandCentroidDistance")
                        .isNumber())
                .andExpect(jsonPath("$.metrics.contactResidueCoverage")
                        .isNumber())
                .andExpect(jsonPath("$.metrics.pocketContactCoverage")
                        .isNumber());
    }
}
