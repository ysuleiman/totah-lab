package totah.lab.daedalus.cli;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full dock-prep acceptance with a real AutoDock Vina binary: receptor
 * and ligand preparation through the pipeline, then docking into the
 * fpocket pocket-2 box (the box of LigandDockingAcceptanceTest).
 * Skipped when no vina binary is available.
 */
class DaedalusCliVinaAcceptanceTest {

    private static final Path DEFAULT_VINA =
            Path.of("/Users/yazan/bin/vina");

    @TempDir
    Path temporaryDirectory;

    @Test
    void dockPrepWithVinaDocksSamIntoPocket2() throws Exception {
        Path vina = vinaExecutable();
        Assumptions.assumeTrue(Files.exists(vina),
                "AutoDock Vina not available at " + vina);

        double[] centroid = pocketCentroid(
                resource("Q6UX53/fpocket/pockets/pocket2_vert.pqr"));

        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        int exitCode = new DaedalusCli().run(new String[]{
                "dock-prep",
                "--target", resource("Q6UX53/Q6UX53_TMT1B_HUMAN.pdb"),
                "--ligand", resource("ligand/SAM.sdf"),
                "--out", temporaryDirectory.resolve("runs").toString(),
                "--box", centroid[0] + "," + centroid[1] + ","
                        + centroid[2] + ",24,26,24",
                "--vina", vina.toString()
        }, new PrintWriter(output), new PrintWriter(error));

        assertEquals(CliExitCode.SUCCESS, exitCode, error.toString());
        assertTrue(output.toString().contains("Poses: "),
                output.toString());
        assertTrue(output.toString().contains("Best affinity: "),
                output.toString());
    }

    private static Path vinaExecutable() {
        String property = System.getProperty("vina.executable");
        if (property != null && !property.isBlank()) {
            return Path.of(property);
        }
        String environment = System.getenv("VINA_EXECUTABLE");
        if (environment != null && !environment.isBlank()) {
            return Path.of(environment);
        }
        return DEFAULT_VINA;
    }

    private static double[] pocketCentroid(String vertPqr)
            throws Exception {

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(
                Path.of(vertPqr))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ATOM") || line.startsWith("HETATM")) {
                    String[] tokens = line.trim().split("\\s+");
                    x += Double.parseDouble(tokens[5]);
                    y += Double.parseDouble(tokens[6]);
                    z += Double.parseDouble(tokens[7]);
                    count++;
                }
            }
        }
        return new double[]{x / count, y / count, z / count};
    }

    private static String resource(String name) {
        try {
            return Path.of(DaedalusCliVinaAcceptanceTest.class
                    .getClassLoader()
                    .getResource(name).toURI()).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
