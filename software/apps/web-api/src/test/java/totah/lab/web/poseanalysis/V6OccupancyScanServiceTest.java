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

class V6OccupancyScanServiceTest {

    private static final String[] NAMES = {
            "ALA", "LEU", "SER", "LYS", "VAL", "GLY"
    };
    private static final int[] NUMBERS = {1, 2, 3, 4, 5, 6};
    private static final double[][] CA_POSITIONS = {
            {0, 0, 0},
            {9, 1, 2},
            {2, 8, 1},
            {5, 3, 9},
            {11, 7, 4},
            {3, 12, 6}
    };
    private static final double[][] POCKET8_SPHERES = {
            {5, 3, 9},
            {4, 2, 8},
            {6, 4, 10},
            {5, 5, 9},
            {5, 1, 9},
            {4, 4, 10}
    };
    private static final double[][] POCKET3_SPHERES = {
            {30, 30, 30},
            {29, 29, 29},
            {31, 31, 31},
            {30, 32, 30},
            {30, 28, 30},
            {29, 31, 31}
    };
    private static final double[][] NEAR_POSE = {
            {5, 3, 7},
            {5, 3, 11},
            {3, 3, 9},
            {7, 3, 9}
    };
    private static final double[][] HOMOLOGOUS_POSE = {
            {30, 30, 28},
            {30, 30, 32},
            {28, 30, 30},
            {32, 30, 30}
    };

    @TempDir
    Path directory;

    private StubPoseAnalysisRepository repository;
    private Path diffdockDir;

    @BeforeEach
    void setUp() throws IOException {
        repository = new StubPoseAnalysisRepository();

        diffdockDir = Files.createDirectories(
                directory.resolve("diffdock-v6"));
        new PdbWriter().write(
                receptorStructure(),
                diffdockDir.resolve("target_protein.pdb"),
                PdbWriteOptions.defaults()
        );
        Files.writeString(
                diffdockDir.resolve("rank1_confidence-0.55.sdf"),
                sdf(NEAR_POSE)
        );
        Files.writeString(
                diffdockDir.resolve("rank2_confidence-0.40.sdf"),
                sdf(HOMOLOGOUS_POSE)
        );

        // The pocket-bearing DB structure with identical content.
        Path structureCopy = directory.resolve("structure-2.pdb");
        Files.copy(
                diffdockDir.resolve("target_protein.pdb"),
                structureCopy
        );
        repository.addStructureArtifact(
                2, 6, "AF-TESTB-F1-model_v6", structureCopy.toString());

        repository.pockets.put(2L, List.of(
                StubPoseAnalysisRepository.pocket(10, 8, "FPOCKET"),
                StubPoseAnalysisRepository.pocket(5, 3, "FPOCKET")
        ));
        List<PocketAlphaSphereProjection> spheres = new ArrayList<>();
        for (int index = 0; index < POCKET8_SPHERES.length; index++) {
            double[] sphere = POCKET8_SPHERES[index];
            spheres.add(StubPoseAnalysisRepository.sphere(
                    10, index, sphere[0], sphere[1], sphere[2], 2.5));
        }
        for (int index = 0; index < POCKET3_SPHERES.length; index++) {
            double[] sphere = POCKET3_SPHERES[index];
            spheres.add(StubPoseAnalysisRepository.sphere(
                    5, index, sphere[0], sphere[1], sphere[2], 2.5));
        }
        repository.spheres.put(2L, spheres);
        List<PosePocketResidueProjection> residues = new ArrayList<>();
        for (long pocketId : new long[]{10L, 5L}) {
            for (int index = 0; index < NUMBERS.length; index++) {
                residues.add(StubPoseAnalysisRepository.pocketResidue(
                        pocketId, "A", NUMBERS[index], NAMES[index]));
            }
        }
        repository.residues.put(2L, residues);
    }

    @Test
    void scansRanksGroupsByPocketAndFlagsTheHomologousSite() {
        String report = service().report(diffdockDir, 2, 5);

        // Header + provenance.
        assertTrue(report.contains("matched DB structure 2"), report);
        assertTrue(report.contains("compatibility IDENTICAL_ARTIFACT"),
                report);
        assertTrue(report.contains("Homologous-site pocket:"
                + " FPOCKET pocket 3 (id 5"), report);

        // Rank rows.
        assertTrue(report.contains("rank | confidence"), report);
        assertTrue(report.contains(
                "1 | -0.5500 | FPOCKET pocket 8 (id 10)"), report);
        assertTrue(report.contains(
                "2 | -0.4000 | FPOCKET pocket 3 (id 5)"), report);
        assertTrue(report.contains("yes (assigned+geometry)"), report);

        // Grouping.
        assertTrue(report.contains(
                "FPOCKET pocket 8 (id 10) | 1 | 0.500"), report);
        assertTrue(report.contains(
                "FPOCKET pocket 3 (id 5) | 1 | 0.500"), report);

        // Verdicts.
        assertTrue(report.contains(
                "ranks assigned to the homologous pocket 5: [2]"),
                report);
        assertTrue(report.contains("dominant pocket:"), report);
    }

    @Test
    void missingDirectoryFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report(
                        directory.resolve("no-such-dir"), 2, 5)
        );
        assertTrue(exception.getMessage().contains(
                "Occupancy scan directory does not exist"),
                exception.getMessage());
    }

    @Test
    void hashMismatchFailsLoudly() {
        // Remove the matching structure artifact: nothing may fall
        // back to accession matching.
        repository.structureArtifacts.clear();
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report(diffdockDir, 2, 5)
        );
        assertTrue(exception.getMessage().contains(
                "no accession-based fallback"),
                exception.getMessage());
    }

    private V6OccupancyScanService service() {
        return new V6OccupancyScanService(
                new PoseAnalysisService(repository, directory.toString()),
                repository
        );
    }

    private static Structure receptorStructure() {
        List<Residue> residues = new ArrayList<>();
        for (int index = 0; index < NUMBERS.length; index++) {
            double[] position = CA_POSITIONS[index];
            residues.add(new Residue(NAMES[index], NUMBERS[index],
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
        return new Structure(List.of(new Chain("A", residues)));
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
