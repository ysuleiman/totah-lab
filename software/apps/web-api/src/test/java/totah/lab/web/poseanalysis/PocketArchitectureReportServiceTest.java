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
import totah.lab.web.service.PocketAlphaSphereProjection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketArchitectureReportServiceTest {

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
    private static final double[][] FAR_POSE = {
            {200, 200, 200},
            {202, 201, 200}
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

        Path poseA = directory.resolve("pose-a.pdbqt");
        Files.writeString(poseA, PoseAnalysisTestData.posePdbqt(POSE));
        Path poseB = directory.resolve("pose-b.pdbqt");
        Files.writeString(poseB, PoseAnalysisTestData.posePdbqt(POSE));
        Path farPose = directory.resolve("pose-far.pdbqt");
        Files.writeString(
                farPose,
                PoseAnalysisTestData.posePdbqt(FAR_POSE)
        );

        repository.addRun(5, 1, 1, "receptor-1", "TESTA");
        repository.addRun(6, 2, 2, "receptor-2", "TESTB");
        repository.addStructureArtifact(
                1,
                101,
                "AF-TESTA-F1-model_v6",
                directory.resolve("receptor-1.pdbqt").toString()
        );
        repository.addStructureArtifact(
                2,
                102,
                "AF-TESTB-F1-model_v6",
                directory.resolve("receptor-2.pdbqt").toString()
        );
        repository.addPose(5, 7, "DCMB", "DCMB-R diffdock 7A rank1",
                -7.0, poseA.toString());
        repository.addPose(6, 9, "DCMB", "DCMB-R diffdock 7B rank1",
                -8.0, poseB.toString());
        repository.addPose(5, 8, "DCMB", "DCMB-R diffdock 7A rank2",
                -6.0, farPose.toString());

        for (long structureId : new long[]{1L, 2L}) {
            repository.pockets.put(structureId, List.of(
                    StubPoseAnalysisRepository.pocket(
                            structureId, 1, "FPOCKET")
            ));
            List<PocketAlphaSphereProjection> spheres = new ArrayList<>();
            for (int index = 0; index < POCKET_SPHERES.length; index++) {
                double[] sphere = POCKET_SPHERES[index];
                spheres.add(StubPoseAnalysisRepository.sphere(
                        structureId, index,
                        sphere[0], sphere[1], sphere[2], 2.5
                ));
            }
            repository.spheres.put(structureId, spheres);
            List<PosePocketResidueProjection> residues = new ArrayList<>();
            for (int index = 0;
                    index < RESIDUE_NUMBERS.length; index++) {
                residues.add(StubPoseAnalysisRepository.pocketResidue(
                        structureId, "A", RESIDUE_NUMBERS[index],
                        RESIDUE_NAMES[index]
                ));
            }
            repository.residues.put(structureId, residues);
        }
    }

    @Test
    void rendersTheArchitectureComparisonBehindAHeader() {
        String report = service().report(7, 9);

        assertTrue(report.contains(
                "computational geometry evidence"), report);
        assertTrue(report.contains(
                "Pose A: 7 \"DCMB-R diffdock 7A rank1\" (run 5, TESTA,"
                        + " receptor 1), assigned FPOCKET pocket 1"),
                report);
        assertTrue(report.contains(
                "Pose B: 9 \"DCMB-R diffdock 7B rank1\" (run 6, TESTB,"
                        + " receptor 2), assigned FPOCKET pocket 1"),
                report);
        // The athena rendering follows the header.
        assertTrue(report.contains("Pocket architecture comparison"),
                report);
        assertTrue(report.contains("Alpha spheres"), report);
        assertTrue(report.indexOf("Pocket architecture comparison")
                > report.indexOf("Pose B:"), report);
    }

    @Test
    void directorySideLoadsThroughHashProvenance() throws IOException {
        Path diffdockDir = writeDiffdockDir(
                "diffdock-7b", POSE);
        // The DB structure artifact with identical content (a copy —
        // the match is by content hash, not by path).
        Path structureCopy = directory.resolve("structure-2.pdb");
        Files.copy(
                diffdockDir.resolve("target_protein.pdb"),
                structureCopy
        );
        repository.addStructureArtifact(
                2, 102, "AF-TESTB-F1-model_v6", structureCopy.toString());

        String report = service().report(
                7L, null, 1, null, diffdockDir, 1);

        assertTrue(report.contains("Pose B: directory "
                + diffdockDir
                + " (rank 1, pose file rank1_confidence-0.55.sdf),"
                + " matched DB structure 2 (artifact 102,"
                + " AF-TESTB-F1-model_v6), assigned FPOCKET pocket 1"
                + " (id 2)"), report);
        assertTrue(report.contains(
                "Provenance B: docked receptor artifact"
                        + " diffdock-7b/target_protein.pdb"), report);
        assertTrue(report.contains("compatibility IDENTICAL_ARTIFACT"),
                report);
        assertTrue(report.contains("sphere metrics AVAILABLE"), report);
        assertTrue(report.contains("Pocket architecture comparison"),
                report);
        assertTrue(report.contains("Alpha spheres"), report);
    }

    @Test
    void directorySideWithoutHashMatchFailsLoudly() throws IOException {
        Path diffdockDir = writeDiffdockDir(
                "diffdock-7b", POSE);
        // Structure 2's artifact (receptor-2.pdbqt) has different
        // content than the directory's target_protein.pdb.
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report(7L, null, 1, null, diffdockDir, 1)
        );
        assertTrue(exception.getMessage().contains(
                "no pocket-bearing DB structure artifact"),
                exception.getMessage());
        assertTrue(exception.getMessage().contains("no accession-based"),
                exception.getMessage());
    }

    @Test
    void directorySideUnassignedPoseFailsLoudly() throws IOException {
        Path diffdockDir = writeDiffdockDir(
                "diffdock-7b", FAR_POSE);
        Path structureCopy = directory.resolve("structure-2.pdb");
        Files.copy(
                diffdockDir.resolve("target_protein.pdb"),
                structureCopy
        );
        repository.addStructureArtifact(
                2, 102, "AF-TESTB-F1-model_v6", structureCopy.toString());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report(7L, null, 1, null, diffdockDir, 1)
        );
        assertTrue(exception.getMessage().contains("ASSIGNED"),
                exception.getMessage());
    }

    @Test
    void unassignedPoseFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report(8, 9)
        );
        assertTrue(exception.getMessage().contains(
                "Pose 8 is not assigned to any pocket"),
                exception.getMessage());
    }

    @Test
    void unknownPoseFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report(99, 9)
        );
        assertTrue(exception.getMessage().contains("No docking pose 99"),
                exception.getMessage());
    }

    private PocketArchitectureReportService service() {
        return new PocketArchitectureReportService(
                new PoseAnalysisService(repository, directory.toString()),
                repository
        );
    }

    /**
     * A DiffDock-style output directory: target_protein.pdb (the CA
     * geometry, as a standard PDB) and one rank-1 pose SDF.
     */
    private Path writeDiffdockDir(
            String name,
            double[][] pose
    ) throws IOException {
        Path dir = Files.createDirectories(directory.resolve(name));
        List<Residue> residues = new ArrayList<>();
        for (int index = 0; index < RESIDUE_NUMBERS.length; index++) {
            double[] position = RESIDUE_POSITIONS[index];
            residues.add(new Residue(
                    RESIDUE_NAMES[index],
                    RESIDUE_NUMBERS[index],
                    List.of(Atom.builder()
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
                            .build())
            ));
        }
        new PdbWriter().write(
                new Structure(List.of(new Chain("A", residues))),
                dir.resolve("target_protein.pdb"),
                PdbWriteOptions.defaults()
        );
        Files.writeString(
                dir.resolve("rank1_confidence-0.55.sdf"),
                sdf(pose)
        );
        return dir;
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
