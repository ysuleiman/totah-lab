package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Explicitly gated qualification campaign; excluded from ordinary test runs. */
final class LockedFermiNetH2oCampaignTest {
    @Test
    void runLockedPretrainingCampaign() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("ferminet.lockedCampaign"));
        int iterations = Integer.getInteger("ferminet.iterations", 1000);
        int walkers = Integer.getInteger("ferminet.walkers", 64);
        Path output = Path.of(System.getProperty("ferminet.output"));
        Files.createDirectories(output);

        var molecule = GaussianHartreeFockOrbitalTargetTest.water();
        var architecture = FermiNetV1Configuration.locked();
        var initial = new FermiNetV1State(molecule, architecture,
                FermiNetParameters.initialize(new FermiNetParameterLayout(architecture, molecule),
                        20260815L));
        var target = GaussianHartreeFockOrbitalTarget.read(
                GaussianHartreeFockOrbitalTargetTest.artifact(), molecule);
        long started = System.nanoTime();
        var result = new ReferenceFermiNetPretrainer().train(initial, target,
                new ReferenceFermiNetPretrainer.Configuration(
                        iterations, walkers, 3e-4, .02, 1.0, 1.0, 44017L),
                (iteration, loss) -> {
                    if (iteration == 1 || iteration % 25 == 0 || iteration == iterations) {
                        System.out.printf("LOCKED_PRETRAIN_PROGRESS iteration=%d loss=%s%n", iteration, loss);
                    }
                });
        long elapsed = System.nanoTime() - started;
        writeLoss(output.resolve("pretraining-loss.csv"), result.lossHistory());
        writeParameters(output.resolve("pretrained-parameters.hex"), result.state().parameterArray());
        Files.writeString(output.resolve("pretraining-summary.txt"),
                "reference_commit=" + ReferenceFermiNetPretrainer.REFERENCE_COMMIT + "\n"
                        + "hf_dependency=REFERENCE_QUALIFICATION_ONLY_PYSCF\n"
                        + "architecture=" + architecture + "\niterations=" + iterations
                        + "\nwalkers=" + walkers + "\ninitial_loss=" + result.lossHistory().getFirst()
                        + "\nfinal_loss=" + result.lossHistory().getLast()
                        + "\nacceptance=" + result.acceptance() + "\nelapsed_nanos=" + elapsed + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.printf("LOCKED_PRETRAIN iterations=%d walkers=%d first=%s last=%s acceptance=%s seconds=%s output=%s%n",
                iterations, walkers, result.lossHistory().getFirst(), result.lossHistory().getLast(),
                result.acceptance(), elapsed / 1e9, output);
        assertTrue(Double.isFinite(result.lossHistory().getLast()));
    }

    private static void writeLoss(Path path, List<Double> losses) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("iteration,orbital_mse\n");
            for (int i = 0; i < losses.size(); i++) writer.write((i + 1) + "," + losses.get(i) + "\n");
        }
    }

    private static void writeParameters(Path path, double[] parameters) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (double parameter : parameters) {
                writer.write(Long.toHexString(Double.doubleToRawLongBits(parameter)));
                writer.newLine();
            }
        }
    }
}
