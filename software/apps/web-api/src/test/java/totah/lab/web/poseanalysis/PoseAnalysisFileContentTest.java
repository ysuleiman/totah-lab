package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.web.service.PocketAlphaSphereProjection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoseAnalysisFileContentTest {

    @TempDir
    Path directory;

    @Test
    void poseFileContentReadsTheRecordedPoseFile() throws IOException {
        Path pose = directory.resolve("pose.pdbqt");
        Files.writeString(pose, "MODEL 1\nENDMDL\n");
        PoseAnalysisService service = service(new StubRepository(
                Optional.of(pose(7, pose)), Optional.empty()));

        assertEquals("MODEL 1\nENDMDL\n", service.poseFileContent(7));
    }

    @Test
    void poseFileContentRejectsUnknownPoses() {
        PoseAnalysisService service = service(new StubRepository(
                Optional.empty(), Optional.empty()));

        assertThrows(
                ResponseStatusException.class,
                () -> service.poseFileContent(99)
        );
    }

    @Test
    void receptorFileContentResolvesTheArtifactStore() throws IOException {
        Files.writeString(directory.resolve("receptor-1.pdbqt"), "ATOM\n");
        PoseAnalysisService service = service(new StubRepository(
                Optional.empty(),
                Optional.of(run(5, "receptor-1"))
        ));

        assertEquals("ATOM\n", service.receptorFileContent(5));
    }

    @Test
    void receptorFileContentRejectsUnknownRuns() {
        PoseAnalysisService service = service(new StubRepository(
                Optional.empty(), Optional.empty()));

        assertThrows(
                ResponseStatusException.class,
                () -> service.receptorFileContent(99)
        );
    }

    private PoseAnalysisService service(StubRepository repository) {
        return new PoseAnalysisService(
                repository,
                directory.toString()
        );
    }

    private static PoseProjection pose(long id, Path file) {
        return new PoseProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getLigandLabel() {
                return "LIG vina s1 m1";
            }

            @Override
            public Double getVinaScore() {
                return -7.0;
            }

            @Override
            public String getPoseFile() {
                return file.toString();
            }
        };
    }

    private static PoseRunProjection run(long id, String artifactId) {
        return new PoseRunProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getReceptorId() {
                return 1L;
            }

            @Override
            public Long getStructureId() {
                return 1L;
            }

            @Override
            public String getTargetName() {
                return "METTL7B";
            }

            @Override
            public String getUniProtId() {
                return "Q6UX53";
            }

            @Override
            public String getMethod() {
                return "vina";
            }

            @Override
            public String getReceptorArtifactId() {
                return artifactId;
            }

            @Override
            public Long getPoseCount() {
                return 1L;
            }

            @Override
            public Double getBestScore() {
                return -7.0;
            }
        };
    }

    private record StubRepository(
            Optional<PoseProjection> pose,
            Optional<PoseRunProjection> run
    ) implements PoseAnalysisRepository {

        @Override
        public List<DockingTargetProjection> findDockingTargets() {
            return List.of();
        }

        @Override
        public List<LigandOptionProjection> findLigandsForReceptor(
                long receptorId, String query, int limit) {
            return List.of();
        }

        @Override
        public Optional<LigandOptionProjection> findLigandOption(
                long receptorId, String ligandId) {
            return Optional.empty();
        }

        @Override
        public List<LigandRunOptionProjection> findLigandRunsForReceptor(
                long receptorId, String query, int limit) {
            return List.of();
        }

        @Override
        public Optional<String> findSiblingReceptorArtifactId(
                long receptorId) {
            return Optional.empty();
        }

        @Override
        public List<PoseRunProjection> findRunsForLigand(
                long receptorId, String ligandId) {
            return List.of();
        }

        @Override
        public List<PoseProjection> findPoses(long runId,
                String ligandId) {
            return List.of();
        }

        @Override
        public Optional<PoseProjection> findPose(long poseId) {
            return pose.filter(candidate -> candidate.getId() == poseId);
        }

        @Override
        public Optional<PoseRunProjection> findRun(long runId) {
            return run.filter(candidate -> candidate.getId() == runId);
        }

        @Override
        public Optional<PoseRunProjection> findRunForPose(long poseId) {
            return run.filter(candidate ->
                    pose.map(candidatePose -> candidatePose.getId() == poseId)
                            .orElse(false));
        }

        @Override
        public List<String> findBiohubArtifactLocations(long receptorId) {
            return List.of();
        }

        @Override
        public List<PoseProjection> findPosesByRunId(long runId) {
            return List.of();
        }

        @Override
        public List<PosePocketProjection> findPocketsByStructureId(
                long structureId) {
            return List.of();
        }

        @Override
        public List<PocketAlphaSphereProjection>
                findAlphaSpheresByStructureId(long structureId) {
            return List.of();
        }

        @Override
        public List<PosePocketResidueProjection>
                findPocketResiduesByStructureId(long structureId) {
            return List.of();
        }

        @Override
        public Optional<StructureArtifactProjection>
                findStructureArtifact(long structureId) {
            return Optional.empty();
        }

        @Override
        public List<StructureArtifactProjection>
                findPocketedStructureArtifacts() {
            return List.of();
        }
    }
}
