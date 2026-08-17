package totah.lab.prometheus.neural;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * One-shot real-size H2O FermiNet SR iteration driver.
 *
 * <p>This is a performance/scientific diagnostic driver, not an acceptance test.
 *
 * <p>It loads:
 * <ul>
 *     <li>the locked H2O FermiNet configuration;</li>
 *     <li>the existing pretrained parameter vector;</li>
 *     <li>64 retained H2O walkers from the SR pilot;</li>
 * </ul>
 *
 * <p>It then performs exactly one production
 * {@link FermiNetMatrixFreeSrOptimizer#oneIteration} call.
 */
public final class FermiNetH2oSingleSrIterationDriver {

    private static final int SAMPLE_COUNT = 64;

    /*
     * Measured best observation-stage parallelism on the current machine.
     * This is passed through optimizer configuration; it is not hard-coded
     * inside the optimizer.
     */
    private static final int OBSERVATION_PARALLELISM = 12;

    /*
     * SR numerical configuration.
     *
     * These currently match the established optimizer-test numerical settings.
     * If the locked H2O production protocol uses different values, replace
     * these constants with those locked values before using the resulting
     * parameter update scientifically.
     */
    private static final double LEARNING_RATE = 0.03;
    private static final double DAMPING = 0.2;
    private static final double MAX_UPDATE_NORM = 100.0;
    private static final int BLOCK_SIZE = 8192;
    private static final int MAX_SOLVER_ITERATIONS = 300;
    private static final double RELATIVE_TOLERANCE = 1.0e-11;
    private static final double ABSOLUTE_TOLERANCE = 1.0e-12;

    private FermiNetH2oSingleSrIterationDriver() {
    }

    public static void main(String[] args)
            throws Exception {

        Path root;

        if (args.length == 0) {
            root =
                    Path.of(
                                    "/Users/yazan/totah-lab")
                            .toAbsolutePath()
                            .normalize();

        } else if (args.length == 1) {
            root =
                    Path.of(args[0])
                            .toAbsolutePath()
                            .normalize();

        } else {
            throw new IllegalArgumentException(
                    "expected zero arguments or repository root path");
        }

        Path pretraining =
                root.resolve(
                        "software/modules/analysis/"
                                + "prometheus-ferminet-h2o-pretraining");

        Path pilot =
                root.resolve(
                        "software/modules/analysis/"
                                + "prometheus-ferminet-h2o-sr-pilot");

        Path parameterFile =
                pretraining.resolve(
                        "pretrained-parameters.hex");

        Path walkerFile =
                pilot.resolve(
                        "baseline-retained-walkers.csv");

        requireFile(
                parameterFile,
                "pretrained parameter");

        requireFile(
                walkerFile,
                "retained walker");

        Molecule molecule =
                GaussianHartreeFockOrbitalTargetTest.water();

        FermiNetV1Configuration networkConfiguration =
                FermiNetV1Configuration.locked();

        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(
                        networkConfiguration,
                        molecule);

        int parameterCount =
                layout.parameterCount();

        double[] parameterArray =
                readParameters(
                        parameterFile,
                        parameterCount);

        FermiNetParameters parameters =
                FermiNetParameters.fromArray(
                        layout,
                        parameterArray);

        FermiNetV1State state =
                new FermiNetV1State(
                        molecule,
                        networkConfiguration,
                        parameters);

        List<QuantumCoordinates> allWalkers =
                readWalkers(
                        walkerFile,
                        molecule);

        if (allWalkers.size() < SAMPLE_COUNT) {
            throw new IllegalStateException(
                    "need at least "
                            + SAMPLE_COUNT
                            + " retained walkers but found "
                            + allWalkers.size());
        }

        List<QuantumCoordinates> selectedWalkers =
                List.copyOf(
                        allWalkers.subList(
                                0,
                                SAMPLE_COUNT));

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples =
                selectedWalkers.stream()
                        .map(
                                coordinates ->
                                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                                1.0,
                                                coordinates))
                        .toList();

        FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration =
                new FermiNetMatrixFreeSrOptimizer.Configuration(
                        LEARNING_RATE,
                        DAMPING,
                        MAX_UPDATE_NORM,
                        OBSERVATION_PARALLELISM,
                        BLOCK_SIZE,
                        MAX_SOLVER_ITERATIONS,
                        RELATIVE_TOLERANCE,
                        ABSOLUTE_TOLERANCE);

        System.out.printf("""
                FERMINET_H2O_SINGLE_SR_ITERATION_DRIVER
                  repository_root=%s
                  parameter_file=%s
                  walker_file=%s

                  parameters=%d
                  samples=%d
                  available_processors=%d

                  learning_rate=%.17g
                  damping=%.17g
                  max_update_norm=%.17g
                  observation_parallelism=%d
                  block_size=%d
                  max_solver_iterations=%d
                  relative_tolerance=%.17g
                  absolute_tolerance=%.17g

                  action=one production SR iteration
                %n""",
                root,
                parameterFile,
                walkerFile,
                parameterCount,
                samples.size(),
                Runtime.getRuntime().availableProcessors(),
                srConfiguration.learningRate(),
                srConfiguration.damping(),
                srConfiguration.maxUpdateNorm(),
                srConfiguration.observationParallelism(),
                srConfiguration.blockSize(),
                srConfiguration.maxSolverIterations(),
                srConfiguration.relativeTolerance(),
                srConfiguration.absoluteTolerance());

        long started =
                System.nanoTime();

        FermiNetMatrixFreeSrOptimizer.Result result =
                new FermiNetMatrixFreeSrOptimizer()
                        .oneIteration(
                                state,
                                samples,
                                srConfiguration);

        long externalWallNanos =
                System.nanoTime() - started;

        System.out.printf("""
                FERMINET_H2O_SINGLE_SR_ITERATION_RESULT
                  initial_energy_hartree=%.17g
                  gradient_norm=%.17g
                  raw_update_norm=%.17g
                  applied_update_norm=%.17g
                  update_rescaled=%s

                  absolute_sample_space_residual=%.17g
                  relative_sample_space_residual=%.17g

                  sample_evaluations=%d
                  solver_iterations=%d
                  streamed_operator_passes=%d

                  external_one_iteration_wall_ms=%.3f

                  note=no parameter files are written by this driver
                %n""",
                result.initialEnergyHartree(),
                result.gradientNorm(),
                result.rawUpdateNorm(),
                result.appliedUpdateNorm(),
                result.updateRescaled(),
                result.absoluteTrueResidual(),
                result.relativeTrueResidual(),
                result.sampleEvaluations(),
                result.solverIterations(),
                result.streamedOperatorPasses(),
                externalWallNanos / 1.0e6);
    }

    private static void requireFile(
            Path path,
            String label) {

        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                    label
                            + " file does not exist: "
                            + path);
        }
    }

    private static double[] readParameters(
            Path file,
            int count)
            throws IOException {

        double[] values =
                new double[count];

        boolean[] seen =
                new boolean[count];

        for (String line :
                Files.readAllLines(file)) {

            String trimmed =
                    line.trim();

            if (trimmed.isEmpty()
                    || trimmed.startsWith("#")) {
                continue;
            }

            String[] fields =
                    trimmed.split("\\s+");

            if (fields.length < 2) {
                throw new IOException(
                        "invalid parameter line: "
                                + line);
            }

            int index =
                    Integer.parseInt(
                            fields[0]);

            if (index < 0
                    || index >= count) {
                throw new IOException(
                        "parameter index out of range: "
                                + index);
            }

            if (seen[index]) {
                throw new IOException(
                        "duplicate parameter "
                                + index);
            }

            values[index] =
                    Double.parseDouble(
                            fields[1]);

            seen[index] =
                    true;
        }

        for (int index = 0;
             index < count;
             index++) {

            if (!seen[index]) {
                throw new IOException(
                        "missing parameter "
                                + index);
            }
        }

        return values;
    }

    private static List<QuantumCoordinates> readWalkers(
            Path file,
            Molecule molecule)
            throws IOException {

        Map<Integer, List<QuantumCoordinates.ParticleCoordinate>> grouped =
                new LinkedHashMap<>();

        List<String> lines =
                Files.readAllLines(
                        file);

        for (int lineIndex = 1;
             lineIndex < lines.size();
             lineIndex++) {

            String line =
                    lines.get(lineIndex)
                            .trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] fields =
                    line.split(",");

            if (fields.length < 6) {
                throw new IOException(
                        "invalid walker line "
                                + (lineIndex + 1)
                                + ": "
                                + line);
            }

            int walker =
                    Integer.parseInt(
                            fields[0]);

            int particleIndex =
                    Integer.parseInt(
                            fields[1]);

            SpinProjection spin =
                    SpinProjection.valueOf(
                            fields[2]);

            double x =
                    Double.parseDouble(
                            fields[3]);

            double y =
                    Double.parseDouble(
                            fields[4]);

            double z =
                    Double.parseDouble(
                            fields[5]);

            grouped.computeIfAbsent(
                            walker,
                            ignored ->
                                    new ArrayList<>())
                    .add(
                            new QuantumCoordinates.ParticleCoordinate(
                                    particleIndex,
                                    x,
                                    y,
                                    z,
                                    spin));
        }

        List<QuantumCoordinates> result =
                new ArrayList<>(
                        grouped.size());

        for (var entry :
                grouped.entrySet()) {

            List<QuantumCoordinates.ParticleCoordinate> particles =
                    entry.getValue();

            particles.sort(
                    Comparator.comparingInt(
                            QuantumCoordinates.ParticleCoordinate::particleIndex));

            if (particles.size()
                    != molecule.electrons().value()) {

                throw new IOException(
                        "walker "
                                + entry.getKey()
                                + " electron count mismatch: expected "
                                + molecule.electrons().value()
                                + " but found "
                                + particles.size());
            }

            result.add(
                    new QuantumCoordinates(
                            particles));
        }

        return result;
    }
}