package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.web.poseanalysis.PoseAnalysisView.ContactProfileView;
import totah.lab.web.service.PocketAlphaSphereProjection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoseContactProfileTest {

    @TempDir
    Path directory;

    @Test
    void computesContactsForTheRequestedPose() throws IOException {
        Files.writeString(directory.resolve("receptor-1.pdbqt"),
                String.join("\n", List.of(
                        atom(1, "N", "ALA", "A", 10, 0, 1.8, 0, 0.0, "NA"),
                        atom(2, "CA", "ALA", "A", 10, 0, 0, 0, 0.1, "C")
                )) + "\n");
        Path pose = directory.resolve("pose.pdbqt");
        Files.writeString(pose, String.join("\n", List.of(
                "MODEL 1",
                "ROOT",
                atom(3, "C1", "UNL", "L", 1, 0, 0, 3, 0.05, "C"),
                "ENDROOT",
                "TORSDOF 0",
                "ENDMDL"
        )) + "\n");
        PoseAnalysisService service = service(new StubRepository(
                Optional.of(pose(7, pose)),
                Optional.of(run(5, "receptor-1"))
        ));

        ContactProfileView profile = service.poseContactProfile(7);

        assertTrue(profile.available());
        assertEquals(5, profile.runId());
        assertEquals(7, profile.poseId());
        assertEquals(1, profile.contacts().size());
        assertEquals("A", profile.contacts().getFirst().chain());
        assertEquals(10, profile.contacts().getFirst().residueNumber());
        assertEquals("ALA", profile.contacts().getFirst().residueName());
        assertEquals(3.0,
                profile.contacts().getFirst().minimumDistance(), 1.0e-9);
    }

    @Test
    void reportsUnavailableWhenTheReceptorFileIsMissing() {
        PoseAnalysisService service = service(new StubRepository(
                Optional.of(pose(7, directory.resolve("pose.pdbqt"))),
                Optional.of(run(5, "no-such-artifact"))
        ));

        ContactProfileView profile = service.poseContactProfile(7);

        assertFalse(profile.available());
        assertTrue(profile.unavailableReason() != null
                && !profile.unavailableReason().isBlank());
        assertEquals(List.of(), profile.contacts());
    }

    @Test
    void rejectsUnknownPoses() {
        PoseAnalysisService service = service(new StubRepository(
                Optional.empty(), Optional.empty()));

        assertThrows(
                ResponseStatusException.class,
                () -> service.poseContactProfile(99)
        );
    }

    private PoseAnalysisService service(StubRepository repository) {
        return new PoseAnalysisService(
                repository,
                directory.toString()
        );
    }

    private static String atom(
            int serial,
            String name,
            String residueName,
            String chain,
            int residueNumber,
            double x,
            double y,
            double z,
            double charge,
            String type
    ) {
        return String.format(
                Locale.ROOT,
                "ATOM  %5d %-4s %-3s %1s%4d    %8.3f%8.3f%8.3f"
                        + "  1.00  0.00    %+6.3f %-2s",
                serial, name, residueName, chain, residueNumber,
                x, y, z, charge, type
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
