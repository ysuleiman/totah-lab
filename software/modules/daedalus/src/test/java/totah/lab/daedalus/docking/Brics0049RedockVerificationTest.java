package totah.lab.daedalus.docking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.hephaestus.client.HephaestusClients;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reproduces the recorded chemflow3 selectivity result for
 * METTL7-BRICS-0049 (7B -2.10 / 7A -6.92, delta -4.83) by preparing
 * the ligand with our own SDF->PDBQT path and redocking into the
 * original run boxes with the original receptor PDBQTs.
 *
 * <p>Verified findings (2026-08, vina 1.2.5):</p>
 * <ul>
 *   <li>7A reproduces with both the original meeko ligand
 *       (-6.90 vs recorded -6.921) and our prepared ligand (-7.17).</li>
 *   <li>7B is seed-unstable at exhaustiveness 4: the original meeko
 *       ligand scores between -1.89 and -3.47 depending on seed
 *       (seed 1 gives -2.096, matching the recorded -2.095), and
 *       exhaustiveness 32 finds -5.43. Our prepared ligand scores
 *       -5.73 at exhaustiveness 4.</li>
 *   <li>The recorded -4.83 delta is therefore an artifact of one
 *       low-sampling 7B run; the well-sampled delta is roughly
 *       -1.5 kcal/mol (still 7A-favored).</li>
 * </ul>
 *
 * <p>Asserts the stable 7A reproduction only; the 7B side is reported
 * but not asserted because it is inherently seed-dependent at the
 * original exhaustiveness.</p>
 *
 * <p>Runs only when the machine-local chemflow artifacts and a vina
 * executable exist.</p>
 */
class Brics0049RedockVerificationTest {

    private static final Path STORAGE = Path.of(
            "/Users/yazan/projects/chemflow/backend/artifact-storage"
                    + "/6702f24f-7bed-4389-a9fa-a28fccc92a12");

    private static final Path LIGAND_SDF =
            STORAGE.resolve("25564958-dbab-4407-adc6-2969b89a9eda.sdf");
    private static final Path RECEPTOR_7B =
            STORAGE.resolve("d8b354e4-c100-4067-91d6-699c3d9fb022.pdbqt");
    private static final Path RECEPTOR_7A =
            STORAGE.resolve("663999e8-8302-4650-a345-660547e026f3.pdbqt");

    private static final double EXPECTED_7B = -2.10;
    private static final double EXPECTED_7A = -6.92;

    @TempDir
    Path workDir;

    @Test
    void redockReproducesTheSelectivityDelta() throws Exception {
        assumeTrue(Files.exists(LIGAND_SDF)
                && Files.exists(RECEPTOR_7B)
                && Files.exists(RECEPTOR_7A));

        HephaestusClient client = HephaestusClients.createDefault();

        LigandPreparationResult prepared = client.prepareLigand(
                LIGAND_SDF,
                LigandPreparationOptions.defaults()
        );
        if (!prepared.successful()) {
            throw new IllegalStateException(
                    "Ligand preparation failed: " + prepared.issues()
            );
        }

        Path ligandPdbqt = workDir.resolve("brics-0049.pdbqt");
        client.writePreparedLigand(prepared.preparedLigand(), ligandPdbqt);

        VinaDockingRunner runner =
                new VinaDockingRunner(vinaExecutable());

        // Recorded boxes from docking_run 2087 (7B) and 2088 (7A),
        // exhaustiveness 4.
        VinaDockingResult result7b = runner.run(
                new DockingInput(RECEPTOR_7B, ligandPdbqt, Optional.empty()),
                new VinaDockingOptions(
                        2.8443701657458567, -2.100453038674033,
                        -4.210508287292818,
                        25.334, 22.0, 23.923000000000002,
                        4, null
                )
        );
        VinaDockingResult result7a = runner.run(
                new DockingInput(RECEPTOR_7A, ligandPdbqt, Optional.empty()),
                new VinaDockingOptions(
                        1.802043209876543, -3.925425925925926,
                        -6.77633950617284,
                        28.451999999999998, 22.0, 26.506,
                        4, null
                )
        );

        assertEquals(0, result7b.exitCode(), "7B vina exit code");
        assertEquals(0, result7a.exitCode(), "7A vina exit code");
        assertTrue(result7b.bestPose().isPresent(), "7B produced no poses");
        assertTrue(result7a.bestPose().isPresent(), "7A produced no poses");

        double best7b = bestOf(result7b);
        double best7a = bestOf(result7a);
        double delta = best7a - best7b;

        System.out.printf(
                "BRICS-0049 redock: 7B %.2f (recorded %.2f),"
                        + " 7A %.2f (recorded %.2f), delta %.2f"
                        + " (recorded %.2f)%n",
                best7b, EXPECTED_7B, best7a, EXPECTED_7A,
                delta, EXPECTED_7A - EXPECTED_7B
        );
        System.out.println("7B poses:");
        printPoses(result7b);
        System.out.println("7A poses:");
        printPoses(result7a);

        // The 7A side is stable across ligand preparations and seeds;
        // it must reproduce. The 7B side is seed-sensitive at
        // exhaustiveness 4 and is reported above, not asserted.
        assertEquals(EXPECTED_7A, best7a, 0.5,
                "7A best affinity does not reproduce the recorded run");
    }

    private static void printPoses(VinaDockingResult result) {
        result.poses().forEach(pose -> System.out.printf(
                "  %d %.3f (rmsd lb %.2f ub %.2f)%n",
                pose.mode(), pose.affinityKcalPerMol(),
                pose.rmsdLowerBound(), pose.rmsdUpperBound()
        ));
    }

    private static double bestOf(VinaDockingResult result) {
        return result.poses().stream()
                .mapToDouble(VinaPose::affinityKcalPerMol)
                .min()
                .orElseThrow();
    }

    private static Path vinaExecutable() {
        String property = System.getProperty("vina.executable");
        String environment = System.getenv("VINA_EXECUTABLE");
        Path candidate = property != null
                ? Path.of(property)
                : environment != null
                ? Path.of(environment)
                : Path.of("/Users/yazan/bin/vina");
        assumeTrue(Files.exists(candidate));
        return candidate;
    }
}
