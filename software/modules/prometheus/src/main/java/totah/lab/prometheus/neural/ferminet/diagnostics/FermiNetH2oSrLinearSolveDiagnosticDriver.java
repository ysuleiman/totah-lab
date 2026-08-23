package totah.lab.prometheus.neural.ferminet.diagnostics;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Read-only diagnostic reproduction of the frozen H2O SR step-1 linear solve.
 *
 * <p>This driver:
 *
 * <ul>
 *   <li>loads the original frozen pretrained H2O parameters;</li>
 *   <li>loads the already-frozen 64 baseline retained configurations from the
 *       completed SR pilot;</li>
 *   <li>runs the same diagonal-preconditioned matrix-free SR solve;</li>
 *   <li>records the complete true-residual history;</li>
 *   <li>does not perform baseline VMC sampling;</li>
 *   <li>does not perform post-SR VMC;</li>
 *   <li>does not persist or accept updated parameters;</li>
 *   <li>does not evaluate forces.</li>
 * </ul>
 *
 * <p>The returned FermiNet state from the optimizer is intentionally discarded.
 */
public final class FermiNetH2oSrLinearSolveDiagnosticDriver {

    private static final double SR_LEARNING_RATE = 0.01;
    private static final double SR_DAMPING = 1.0;
    private static final double SR_MAX_UPDATE_NORM = 0.05;
    private static final int SR_BLOCK_SIZE = 128;
    private static final int SR_MAX_SOLVER_ITERATIONS = 50;
    private static final double SR_RELATIVE_TOLERANCE = 1.0e-6;
    private static final double SR_ABSOLUTE_TOLERANCE = 1.0e-8;

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private FermiNetH2oSrLinearSolveDiagnosticDriver() {
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);

        Files.createDirectories(arguments.outputDirectory());

        Molecule molecule = water();

        FermiNetV1Configuration networkConfiguration =
                FermiNetV1Configuration.locked();

        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(
                        networkConfiguration,
                        molecule);

        double[] parameterValues =
                readParameters(
                        arguments.pretrainingDirectory()
                                .resolve("pretrained-parameters.hex"),
                        layout.parameterCount());

        FermiNetV1State state =
                new FermiNetV1State(
                        molecule,
                        networkConfiguration,
                        FermiNetParameters.fromArray(
                                layout,
                                parameterValues));

        List<QuantumCoordinates> frozenSamples =
                readWalkers(
                        arguments.pilotDirectory()
                                .resolve("baseline-retained-walkers.csv"),
                        molecule);

        if (frozenSamples.size() != 64) {
            throw new IllegalStateException(
                    "expected exactly 64 frozen SR baseline samples, got "
                            + frozenSamples.size());
        }

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> srSamples =
                new ArrayList<>(frozenSamples.size());

        for (QuantumCoordinates coordinates : frozenSamples) {
            srSamples.add(
                    new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                            1.0,
                            coordinates));
        }

        FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration =
                new FermiNetMatrixFreeSrOptimizer.Configuration(
                        SR_LEARNING_RATE,
                        SR_DAMPING,
                        SR_MAX_UPDATE_NORM,
                        12,
                        SR_BLOCK_SIZE,
                        SR_MAX_SOLVER_ITERATIONS,
                        SR_RELATIVE_TOLERANCE,
                        SR_ABSOLUTE_TOLERANCE);

        System.out.printf(
                Locale.ROOT,
                """
                Prometheus FermiNet H2O SR linear-solve diagnostic
                --------------------------------------------------
                pretrained parameters : %s
                frozen SR samples     : %s
                output                : %s
                parameters            : %d
                samples               : %d

                SR configuration:
                  learning rate       : %.8g
                  damping             : %.8g
                  max update norm     : %.8g
                  preconditioner      : STREAMED_COVARIANCE_DIAGONAL
                  max PCG iterations  : %d
                  relative tolerance  : %.3e
                  absolute tolerance  : %.3e

                No baseline VMC will be run.
                No post-SR VMC will be run.
                Updated parameters will NOT be persisted or accepted.

                """,
                arguments.pretrainingDirectory()
                        .resolve("pretrained-parameters.hex"),
                arguments.pilotDirectory()
                        .resolve("baseline-retained-walkers.csv"),
                arguments.outputDirectory(),
                state.parameterCount(),
                frozenSamples.size(),
                SR_LEARNING_RATE,
                SR_DAMPING,
                SR_MAX_UPDATE_NORM,
                SR_MAX_SOLVER_ITERATIONS,
                SR_RELATIVE_TOLERANCE,
                SR_ABSOLUTE_TOLERANCE);

        Instant started = Instant.now();

        /*
         * This computes the same one-step SR solve as the frozen pilot.
         * The updated state returned inside Result is diagnostic only and is discarded.
         */
        FermiNetMatrixFreeSrOptimizer.Result result =
                new FermiNetMatrixFreeSrOptimizer()
                        .oneIteration(
                                state,
                                srSamples,
                                srConfiguration);

        Instant finished = Instant.now();

        List<Double> history =
                result.trueResidualHistory();

        if (history.isEmpty()) {
            throw new IllegalStateException(
                    "empty true-residual history");
        }

        if (history.size() != result.solverIterations()) {
            throw new IllegalStateException(
                    "unexpected residual-history length: "
                            + history.size()
                            + " for "
                            + result.solverIterations()
                            + " iterations");
        }

        System.out.printf(
                Locale.ROOT,
                """
                Java SR linear solve complete
                -----------------------------
                SR sample mean energy  : %+.15f Ha
                gradient norm          : %.17g
                PCG iterations         : %d
                absolute true residual : %.17g
                relative true residual : %.17g
                streamed passes        : %d
                sample evaluations     : %d
                raw update norm        : %.17g
                applied update norm    : %.17g
                update rescaled        : %s

                True residual history:
                """,
                result.initialEnergyHartree(),
                result.gradientNorm(),
                result.solverIterations(),
                result.absoluteTrueResidual(),
                result.relativeTrueResidual(),
                result.streamedOperatorPasses(),
                result.sampleEvaluations(),
                result.rawUpdateNorm(),
                result.appliedUpdateNorm(),
                result.updateRescaled());

        for (int i = 0; i < history.size(); i++) {
            double absolute = history.get(i);
            double relative =
                    history.get(0) == 0.0
                            ? 0.0
                            : absolute / history.get(0);

            System.out.printf(
                    Locale.ROOT,
                    "  iteration %2d  absolute=%.17g  relative=%.17g%n",
                    i,
                    absolute,
                    relative);
        }

        writeResidualCsv(
                arguments.outputDirectory()
                        .resolve("java-true-residual-history.csv"),
                history);

        writeSummary(
                arguments,
                state,
                result,
                started,
                finished);

        System.out.printf(
                Locale.ROOT,
                """

                Diagnostic complete.
                --------------------
                residual CSV : %s
                summary JSON : %s

                updated state persisted : false
                post-SR VMC run         : false
                second SR step run      : false
                forces evaluated        : false

                STOP: compare this residual history against the JAX/Python histories.
                """,
                arguments.outputDirectory()
                        .resolve("java-true-residual-history.csv"),
                arguments.outputDirectory()
                        .resolve("java-sr-linear-solve-diagnostic.json"));
    }

    private static void writeResidualCsv(
            Path output,
            List<Double> history)
            throws IOException {

        double initial =
                history.get(0);

        StringBuilder csv =
                new StringBuilder();

        csv.append(
                "iteration,absolute_true_residual,relative_true_residual\n");

        for (int i = 0; i < history.size(); i++) {
            double absolute =
                    history.get(i);

            double relative =
                    initial == 0.0
                            ? 0.0
                            : absolute / initial;

            csv.append(i)
                    .append(',')
                    .append(Double.toHexString(absolute))
                    .append(',')
                    .append(Double.toHexString(relative))
                    .append('\n');
        }

        Files.writeString(
                output,
                csv.toString());
    }

    private static void writeSummary(
            Arguments arguments,
            FermiNetV1State initialState,
            FermiNetMatrixFreeSrOptimizer.Result result,
            Instant started,
            Instant finished)
            throws IOException {

        Map<String, Object> summary =
                new LinkedHashMap<>();

        summary.put(
                "schema",
                "prometheus-ferminet-h2o-sr-linear-solve-diagnostic-v1");

        summary.put(
                "started_utc",
                started.toString());

        summary.put(
                "finished_utc",
                finished.toString());

        summary.put(
                "pretraining_directory",
                arguments.pretrainingDirectory().toString());

        summary.put(
                "pilot_directory",
                arguments.pilotDirectory().toString());

        summary.put(
                "parameter_count",
                initialState.parameterCount());

        summary.put(
                "sample_count",
                64);

        summary.put(
                "preconditioner",
                "STREAMED_COVARIANCE_DIAGONAL");

        summary.put(
                "learning_rate",
                SR_LEARNING_RATE);

        summary.put(
                "damping",
                SR_DAMPING);

        summary.put(
                "max_update_norm",
                SR_MAX_UPDATE_NORM);

        summary.put(
                "max_solver_iterations",
                SR_MAX_SOLVER_ITERATIONS);

        summary.put(
                "relative_tolerance",
                SR_RELATIVE_TOLERANCE);

        summary.put(
                "absolute_tolerance",
                SR_ABSOLUTE_TOLERANCE);

        summary.put(
                "sr_sample_mean_energy_hartree",
                result.initialEnergyHartree());

        summary.put(
                "gradient_norm",
                result.gradientNorm());

        summary.put(
                "solver_iterations",
                result.solverIterations());

        summary.put(
                "absolute_true_residual",
                result.absoluteTrueResidual());

        summary.put(
                "relative_true_residual",
                result.relativeTrueResidual());

        summary.put(
                "true_residual_history",
                result.trueResidualHistory());

        summary.put(
                "streamed_operator_passes",
                result.streamedOperatorPasses());

        summary.put(
                "sample_evaluations",
                result.sampleEvaluations());

        summary.put(
                "raw_update_norm",
                result.rawUpdateNorm());

        summary.put(
                "applied_update_norm",
                result.appliedUpdateNorm());

        summary.put(
                "update_rescaled",
                result.updateRescaled());

        summary.put(
                "updated_state_persisted",
                false);

        summary.put(
                "post_sr_vmc_run",
                false);

        summary.put(
                "second_sr_step_run",
                false);

        summary.put(
                "forces_evaluated",
                false);

        JSON.writeValue(
                arguments.outputDirectory()
                        .resolve("java-sr-linear-solve-diagnostic.json")
                        .toFile(),
                summary);
    }

    private static double[] readParameters(
            Path file,
            int expectedCount)
            throws IOException {

        if (!Files.isRegularFile(file)) {
            throw new IOException(
                    "missing pretrained parameters: "
                            + file);
        }

        double[] values =
                new double[expectedCount];

        boolean[] seen =
                new boolean[expectedCount];

        for (String line : Files.readAllLines(file)) {
            String trimmed =
                    line.trim();

            if (trimmed.isEmpty()
                    || trimmed.startsWith("#")) {
                continue;
            }

            String[] fields =
                    trimmed.split("\\s+");

            if (fields.length != 2) {
                throw new IOException(
                        "invalid pretrained parameter line: "
                                + line);
            }

            int index;
            double value;

            try {
                index =
                        Integer.parseInt(
                                fields[0]);

                value =
                        Double.parseDouble(
                                fields[1]);
            } catch (NumberFormatException exception) {
                throw new IOException(
                        "invalid pretrained parameter line: "
                                + line,
                        exception);
            }

            if (index < 0
                    || index >= expectedCount
                    || seen[index]) {
                throw new IOException(
                        "invalid/duplicate parameter index: "
                                + index);
            }

            if (!Double.isFinite(value)) {
                throw new IOException(
                        "non-finite pretrained parameter: "
                                + index);
            }

            values[index] =
                    value;

            seen[index] =
                    true;
        }

        for (int i = 0; i < expectedCount; i++) {
            if (!seen[i]) {
                throw new IOException(
                        "missing pretrained parameter index: "
                                + i);
            }
        }

        return values;
    }

    private static List<QuantumCoordinates> readWalkers(
            Path file,
            Molecule molecule)
            throws IOException {

        if (!Files.isRegularFile(file)) {
            throw new IOException(
                    "missing frozen baseline walkers: "
                            + file);
        }

        List<String> lines =
                Files.readAllLines(file);

        if (lines.isEmpty()) {
            throw new IOException(
                    "frozen baseline walker artifact is empty");
        }

        Map<Integer, List<QuantumCoordinates.ParticleCoordinate>> grouped =
                new LinkedHashMap<>();

        for (int lineIndex = 1;
             lineIndex < lines.size();
             lineIndex++) {

            String line =
                    lines.get(lineIndex).trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] fields =
                    line.split(",");

            if (fields.length != 6) {
                throw new IOException(
                        "invalid frozen walker CSV line: "
                                + line);
            }

            try {
                int walker =
                        Integer.parseInt(
                                fields[0]);

                int electron =
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

                if (!Double.isFinite(x)
                        || !Double.isFinite(y)
                        || !Double.isFinite(z)) {
                    throw new IOException(
                            "non-finite frozen walker coordinate");
                }

                grouped.computeIfAbsent(
                                walker,
                                ignored -> new ArrayList<>())
                        .add(
                                new QuantumCoordinates.ParticleCoordinate(
                                        electron,
                                        x,
                                        y,
                                        z,
                                        spin));

            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "invalid frozen walker CSV line: "
                                + line,
                        exception);
            }
        }

        if (grouped.isEmpty()) {
            throw new IOException(
                    "frozen baseline walker artifact is empty");
        }

        List<QuantumCoordinates> result =
                new ArrayList<>();

        int expectedWalker =
                0;

        for (var entry : grouped.entrySet()) {
            if (entry.getKey() != expectedWalker++) {
                throw new IOException(
                        "walker indices must be contiguous from zero");
            }

            List<QuantumCoordinates.ParticleCoordinate> particles =
                    entry.getValue();

            particles.sort(
                    Comparator.comparingInt(
                            QuantumCoordinates.ParticleCoordinate::particleIndex));

            if (particles.size()
                    != molecule.electrons().value()) {
                throw new IOException(
                        "walker electron count mismatch");
            }

            for (int i = 0; i < particles.size(); i++) {
                var particle =
                        particles.get(i);

                if (particle.particleIndex() != i) {
                    throw new IOException(
                            "walker particle ordering mismatch");
                }

                SpinProjection expected =
                        i < molecule.spin().alphaElectrons()
                                ? SpinProjection.ALPHA
                                : SpinProjection.BETA;

                if (particle.spin() != expected) {
                    throw new IOException(
                            "walker spin ordering mismatch");
                }
            }

            result.add(
                    new QuantumCoordinates(
                            particles));
        }

        return result;
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-v1-water",
                List.of(
                        new NuclearCenter(
                                0,
                                "O",
                                new NuclearCharge(8),
                                new CartesianPosition(
                                        0.0,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        1.7952398191849366,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        -0.46464225035067114,
                                        1.7340684963325879,
                                        0.0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(5, 5, 1));
    }

    private record Arguments(
            Path pretrainingDirectory,
            Path pilotDirectory,
            Path outputDirectory) {

        private static Arguments parse(String[] args) {
            Path pretraining =
                    Path.of(
                            "analysis",
                            "prometheus-ferminet-h2o-pretraining");

            Path pilot =
                    Path.of(
                            "analysis",
                            "prometheus-ferminet-h2o-sr-pilot");

            Path output =
                    Path.of(
                            "analysis",
                            "prometheus-ferminet-h2o-sr-linear-solve-diagnostic");

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--pretraining" -> {
                        if (++i >= args.length) {
                            throw usage(
                                    "--pretraining requires a path");
                        }
                        pretraining =
                                Path.of(
                                        args[i]);
                    }

                    case "--pilot" -> {
                        if (++i >= args.length) {
                            throw usage(
                                    "--pilot requires a path");
                        }
                        pilot =
                                Path.of(
                                        args[i]);
                    }

                    case "--output" -> {
                        if (++i >= args.length) {
                            throw usage(
                                    "--output requires a path");
                        }
                        output =
                                Path.of(
                                        args[i]);
                    }

                    default ->
                            throw usage(
                                    "unknown argument: "
                                            + args[i]);
                }
            }

            return new Arguments(
                    pretraining.toAbsolutePath().normalize(),
                    pilot.toAbsolutePath().normalize(),
                    output.toAbsolutePath().normalize());
        }

        private static IllegalArgumentException usage(
                String problem) {

            return new IllegalArgumentException(
                    problem
                            + System.lineSeparator()
                            + """
                              --pretraining PATH
                              --pilot PATH
                              --output PATH
                              """);
        }
    }
}
