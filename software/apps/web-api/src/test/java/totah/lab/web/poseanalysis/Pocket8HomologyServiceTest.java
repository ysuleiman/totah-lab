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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pocket8HomologyServiceTest {

    private static final int RESIDUE_COUNT = 40;

    // The 7B pocket-8 cloud: the long zigzag line proven in athena's
    // own comparator fixtures — nothing like the compact 7A clusters
    // after any rigid alignment.
    private static final double[][] POCKET8_SPHERES = {
            {0, 0, 0},
            {20, 5, 0},
            {40, 0, 5},
            {60, 10, 0},
            {80, 0, 0},
            {100, 5, 10},
            {120, 0, 0},
            {140, 15, 5}
    };
    // Pocket 8's member residues, spread along the same line.
    private static final int[] LINE_RESIDUES = {41, 42, 43, 44, 45, 46};
    // 7B pocket 3 and 7A pocket 1: the same compact cluster (the
    // positive-control homolog pair).
    private static final double[][] CONTROL_SPHERES = {
            {50, 20, -10},
            {48, 19, -11},
            {52, 21, -9},
            {50, 22, -10},
            {50, 18, -10},
            {49, 21, -9},
            {51, 19, -11},
            {50, 20, -12}
    };
    // 7A pocket 2: compact cluster elsewhere.
    private static final double[][] POCKET2_SPHERES = {
            {-30, -20, 30},
            {-32, -19, 31},
            {-28, -21, 29},
            {-30, -18, 30},
            {-30, -22, 30},
            {-31, -21, 31},
            {-29, -19, 29},
            {-30, -20, 28}
    };

    @TempDir
    Path directory;

    private StubPoseAnalysisRepository repository;
    private Path diffdockDir;
    private Path samComplex;

    @BeforeEach
    void setUp() throws IOException {
        repository = new StubPoseAnalysisRepository();
        double[][] positions = residuePositions();
        double[][] withLine = withLineResidues(positions);

        // 7B side: the canonical receptor via the directory (40 blob
        // residues + the pocket-8 line residues).
        diffdockDir = Files.createDirectories(
                directory.resolve("diffdock-v6"));
        new PdbWriter().write(
                pdbStructure(withLine, null, 0),
                diffdockDir.resolve("target_protein.pdb"),
                PdbWriteOptions.defaults()
        );
        Path structureBCopy = directory.resolve("structure-2.pdb");
        Files.copy(diffdockDir.resolve("target_protein.pdb"),
                structureBCopy);
        repository.addStructureArtifact(
                2, 6, "AF-TESTB-F1-model_v6", structureBCopy.toString());
        repository.pockets.put(2L, List.of(
                StubPoseAnalysisRepository.pocket(10, 8, "FPOCKET"),
                StubPoseAnalysisRepository.pocket(5, 3, "FPOCKET")
        ));
        repository.spheres.put(2L, spheresOf(10, POCKET8_SPHERES,
                spheresOf(5, CONTROL_SPHERES, new ArrayList<>())));
        repository.residues.put(2L, residuesForPocket8And3());

        // 7A side: run + receptor artifact through the poseanalysis
        // seams (structure artifact = the same file).
        Files.writeString(
                directory.resolve("receptor-7a.pdbqt"),
                PoseAnalysisTestData.receptorPdbqt(
                        names(), numbers(), positions)
        );
        repository.addRun(2829, 2, 3, "receptor-7a", "TESTA");
        repository.addStructureArtifact(
                3, 24, "AF-TESTA-F1-model_v6",
                directory.resolve("receptor-7a.pdbqt").toString());
        repository.pockets.put(3L, List.of(
                StubPoseAnalysisRepository.pocket(19, 1, "FPOCKET"),
                StubPoseAnalysisRepository.pocket(20, 2, "FPOCKET")
        ));
        repository.spheres.put(3L, spheresOf(19, CONTROL_SPHERES,
                spheresOf(20, POCKET2_SPHERES, new ArrayList<>())));
        List<PosePocketResidueProjection> residuesA = new ArrayList<>();
        for (long pocketId : new long[]{19L, 20L}) {
            for (int number = 1; number <= RESIDUE_COUNT; number++) {
                residuesA.add(StubPoseAnalysisRepository
                        .pocketResidue(pocketId, "A", number, "ALA"));
            }
        }
        repository.residues.put(3L, residuesA);

        // SAM complex: canonical frame, SAM next to the first line
        // residue (41 at the origin).
        samComplex = directory.resolve("sam-complex.pdb");
        new PdbWriter().write(
                pdbStructure(withLine, new double[][]{
                        {0.3, 0.0, 0.0},
                        {1.0, 0.5, 0.5}
                }, 500),
                samComplex,
                PdbWriteOptions.defaults()
        );
    }

    @Test
    void ranks7APocketsAndAnswersTheVerdicts() {
        String report = service().report(
                diffdockDir, 2829, 10, 5, 19,
                samComplex,
                directory.resolve("no-such-sam.pdb")
        );

        // Homology table: both 7A pockets ranked, homologous flags.
        assertTrue(report.contains(
                "vs every 7A FPOCKET pocket (homologous ="
                        + " overall similarity >= 0.30"), report);
        assertTrue(report.contains("FPOCKET pocket 1 (id 19)"),
                report);
        assertTrue(report.contains("FPOCKET pocket 2 (id 20)"),
                report);

        // Pocket 8's zigzag cloud has no compact 7A homolog.
        assertTrue(report.contains("has NO clear 7A homolog"), report);

        // Positive control: the identical clusters are homologous.
        assertTrue(report.contains(
                "Positive control: 7B FPOCKET pocket 3 (id 5) vs 7A"
                        + " FPOCKET pocket 1 (id 19)"), report);
        assertTrue(report.contains("homologous yes (as expected)"),
                report);

        // SAM: 7B overlaps, 7A unavailable (no file).
        assertTrue(report.contains("7B: complex sam-complex.pdb"),
                report);
        assertTrue(report.contains("overlaps SAM region: yes"), report);
        assertTrue(report.contains("pocket residues contacting SAM:"
                + " [41]"), report);
        assertTrue(report.contains("7A: SAM overlap NOT_AVAILABLE"),
                report);
    }

    @Test
    void offFrameSamComplexIsNotAvailable() throws IOException {
        Path offFrame = directory.resolve("sam-offframe.pdb");
        new PdbWriter().write(
                pdbStructure(scaled(residuePositions(), 1.5),
                        new double[][]{{5.3, 3.0, 9.0}}, 500),
                offFrame,
                PdbWriteOptions.defaults()
        );

        String report = service().report(
                diffdockDir, 2829, 10, 5, 19,
                offFrame,
                directory.resolve("no-such-sam.pdb")
        );

        assertTrue(report.contains("7B: SAM overlap NOT_AVAILABLE"
                + " (the complex is in a different frame"), report);
    }

    @Test
    void missingDirectoryFailsWithAClearMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().report(
                        directory.resolve("no-such-dir"),
                        2829, 10, 5, 19,
                        samComplex,
                        directory.resolve("no-such-sam.pdb"))
        );
        assertTrue(exception.getMessage().contains(
                "DiffDock directory does not exist"),
                exception.getMessage());
    }

    private Pocket8HomologyService service() {
        return new Pocket8HomologyService(
                new PoseAnalysisService(repository, directory.toString()),
                repository
        );
    }

    private static List<PocketAlphaSphereProjection> spheresOf(
            long pocketId,
            double[][] spheres,
            List<PocketAlphaSphereProjection> into
    ) {
        int index = into.size();
        for (double[] sphere : spheres) {
            into.add(StubPoseAnalysisRepository.sphere(
                    pocketId, index++,
                    sphere[0], sphere[1], sphere[2], 2.5));
        }
        return into;
    }

    private static List<PosePocketResidueProjection>
            residuesForPocket8And3() {
        List<PosePocketResidueProjection> residues = new ArrayList<>();
        for (int number : LINE_RESIDUES) {
            residues.add(StubPoseAnalysisRepository
                    .pocketResidue(10, "A", number, "ALA"));
        }
        for (int number = 1; number <= RESIDUE_COUNT; number++) {
            residues.add(StubPoseAnalysisRepository
                    .pocketResidue(5, "A", number, "ALA"));
        }
        return residues;
    }

    /** The 40-residue blob plus the pocket-8 line residues (41-46). */
    private static double[][] withLineResidues(double[][] blob) {
        double[][] all = new double[blob.length
                + LINE_RESIDUES.length][];
        System.arraycopy(blob, 0, all, 0, blob.length);
        for (int index = 0; index < LINE_RESIDUES.length; index++) {
            all[blob.length + index] = new double[]{
                    20.0 * index, 0, 0};
        }
        return all;
    }

    private static final String[] NAME_CYCLE = {
            "ALA", "LEU", "SER", "LYS", "VAL", "GLY", "PHE", "ARG"
    };

    private static Structure pdbStructure(
            double[][] positions,
            double[][] samAtoms,
            int samNumber
    ) {
        List<Residue> residues = new ArrayList<>();
        int serial = 1;
        for (int index = 0; index < positions.length; index++) {
            double[] position = positions[index];
            residues.add(new Residue(
                    NAME_CYCLE[index % NAME_CYCLE.length], index + 1,
                    List.of(atom(serial++, "CA", position))));
        }
        if (samAtoms != null) {
            List<Atom> atoms = new ArrayList<>();
            for (double[] position : samAtoms) {
                atoms.add(atom(serial++, "C" + atoms.size(), position));
            }
            residues.add(new Residue("SAM", samNumber, atoms));
        }
        return new Structure(List.of(new Chain("A", residues)));
    }

    private static Atom atom(int serial, String name, double[] position) {
        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .position(new Point3D(
                        position[0], position[1], position[2]))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.C)
                .build();
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
            names[index] = NAME_CYCLE[index % NAME_CYCLE.length];
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
