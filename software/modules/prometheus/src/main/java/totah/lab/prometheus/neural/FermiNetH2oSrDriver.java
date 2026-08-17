package totah.lab.prometheus.neural;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Canonical one-step H2O FermiNet sample-space SR driver.
 *
 * <p>Loads the frozen HF-pretrained state, measures an independent baseline,
 * performs exactly one matrix-free SR update, measures the updated state, writes
 * diagnostics, and stops. It never overwrites the pretraining artifacts.
 */
public final class FermiNetH2oSrDriver {

    private static final int VMC_PARALLELISM = Math.max(1,
            Math.min(12, Runtime.getRuntime().availableProcessors()));
    private static final int SR_MAX_SOLVER_ITERATIONS = 50;
    private static final double SR_RELATIVE_TOLERANCE = 1.0e-6;
    private static final double SR_ABSOLUTE_TOLERANCE = 1.0e-8;

    private static final double MIN_ACCEPTANCE = 0.20;
    private static final double MAX_ACCEPTANCE = 0.90;

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private FermiNetH2oSrDriver() {}

    public static void main(String[] args) throws Exception {
        long driverStartedNanos = System.nanoTime();
        Arguments arguments = Arguments.parse(args);
        Files.createDirectories(arguments.outputDirectory());

        Molecule molecule = water();
        FermiNetV1Configuration configuration = FermiNetV1Configuration.locked();
        FermiNetParameterLayout layout = new FermiNetParameterLayout(configuration, molecule);

        double[] parameterValues = readParameters(arguments.parameterFile(), layout.parameterCount());

        FermiNetV1State initialState = new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.fromArray(layout, parameterValues));

        List<QuantumCoordinates> availableWalkers = readWalkers(arguments.walkerFile(), molecule);
        int requiredWalkers = arguments.sampleCount() / arguments.retainedPerWalker();
        if (availableWalkers.size() < requiredWalkers) {
            throw new IllegalArgumentException("walker artifact has " + availableWalkers.size()
                    + " walkers; configuration requires " + requiredWalkers);
        }
        List<QuantumCoordinates> pretrainedWalkers = List.copyOf(
                availableWalkers.subList(0, requiredWalkers));

        verifySamplingParity(initialState, pretrainedWalkers);

        System.out.printf(Locale.ROOT, """
                Prometheus canonical FermiNet H2O one-step SR
                -----------------------------------------
                preset                 : %s
                parameter input        : %s
                walker input           : %s
                output                 : %s
                parameters             : %d
                walkers                : %d
                SR samples             : %d
                VMC implementation     : deterministic parallel
                VMC parallelism        : %d

                baseline VMC:
                  warmup sweeps        : %d
                  retained/walker      : %d
                  sweeps/retained      : %d
                  step size (bohr)     : %.8g
                  seed                 : %d

                SR:
                  learning rate        : %.8g
                  damping              : %.8g
                  max update norm      : %.8g
                  solver               : structured Jacobian-free sample-space Cholesky SR
                  observation parallel.: %d

                """,
                arguments.preset(),
                arguments.parameterFile(),
                arguments.walkerFile(),
                arguments.outputDirectory(),
                initialState.parameterCount(),
                pretrainedWalkers.size(),
                arguments.sampleCount(),
                VMC_PARALLELISM,
                arguments.warmupSweeps(),
                arguments.retainedPerWalker(),
                arguments.sweepsBetweenRetained(),
                arguments.stepSizeBohr(),
                arguments.baselineSeed(),
                arguments.learningRate(),
                arguments.damping(),
                arguments.maxUpdateNorm(),
                arguments.observationParallelism());

        Instant started = Instant.now();

        /* 1. Independent baseline VMC. */
        FermiNetVmc.Configuration baselineConfiguration = new FermiNetVmc.Configuration(
                pretrainedWalkers.size(),
                arguments.warmupSweeps(),
                arguments.retainedPerWalker(),
                arguments.sweepsBetweenRetained(),
                arguments.stepSizeBohr(),
                arguments.baselineSeed());

        long baselineStartedNanos = System.nanoTime();
        FermiNetVmc.Result baseline = sampleCanonicalVmc(
                initialState,
                baselineConfiguration,
                pretrainedWalkers,
                VMC_PARALLELISM);
        long baselineVmcNanos = System.nanoTime() - baselineStartedNanos;
        if (baseline.samples().size() != arguments.sampleCount()) {
            throw new IllegalStateException("expected " + arguments.sampleCount()
                    + " SR samples but obtained " + baseline.samples().size());
        }

        EnergyStatistics baselineEnergy = energyStatistics(baseline.localEnergies());
        requireOperationalAcceptance(baseline.acceptance(), "baseline");

        System.out.printf(Locale.ROOT, """
                Baseline complete
                -----------------
                acceptance : %.6f
                energy     : %+.10f +/- %.10f Ha
                stddev     : %.10f Ha
                samples    : %d

                """,
                baseline.acceptance(),
                baselineEnergy.mean(),
                baselineEnergy.standardError(),
                baselineEnergy.standardDeviation(),
                baselineEnergy.count());

        /* 2. Direct |Psi|^2 VMC samples have equal SR weights. */
        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> srSamples =
                new ArrayList<>(baseline.samples().size());

        for (QuantumCoordinates coordinates : baseline.samples()) {
            srSamples.add(new FermiNetMatrixFreeSrOptimizer.WeightedSample(1.0, coordinates));
        }

        /* 3. Exactly one matrix-free SR update. */
        FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration =
                new FermiNetMatrixFreeSrOptimizer.Configuration(
                        arguments.learningRate(),
                        arguments.damping(),
                        arguments.maxUpdateNorm(),
                        arguments.observationParallelism(),
                        arguments.parameterBlockSize(),
                        SR_MAX_SOLVER_ITERATIONS,
                        SR_RELATIVE_TOLERANCE,
                        SR_ABSOLUTE_TOLERANCE);

        System.out.println("Starting exactly ONE SR update...");

        long srStartedNanos = System.nanoTime();
        FermiNetMatrixFreeSrOptimizer.Result sr =
                new FermiNetMatrixFreeSrOptimizer().oneIteration(
                        initialState,
                        srSamples,
                        FermiNetKnownLocalEnergies.from(initialState, baseline),
                        srConfiguration);
        long srIterationNanos = System.nanoTime() - srStartedNanos;

        FermiNetV1State updatedState = sr.state();
        verifyFiniteParameters(updatedState);
        verifySamplingParity(updatedState, baseline.samples());

        System.out.printf(Locale.ROOT, """
                SR solve complete
                -----------------
                SR initial energy      : %+.10f Ha
                gradient norm          : %.10e
                Cholesky solves        : %d
                relative true residual : %.10e
                sample evaluations     : %d
                raw update norm        : %.10e
                applied update norm    : %.10e
                update rescaled        : %s

                """,
                sr.initialEnergyHartree(),
                sr.gradientNorm(),
                sr.solverIterations(),
                sr.relativeTrueResidual(),
                sr.sampleEvaluations(),
                sr.rawUpdateNorm(),
                sr.appliedUpdateNorm(),
                sr.updateRescaled());

        /* 4. Independent post-SR VMC, from the latest retained walkers. */
        FermiNetVmc.Configuration postConfiguration = new FermiNetVmc.Configuration(
                baseline.samples().size(),
                arguments.warmupSweeps(),
                1,
                arguments.sweepsBetweenRetained(),
                arguments.stepSizeBohr(),
                arguments.postSrSeed());

        long postStartedNanos = System.nanoTime();
        FermiNetVmc.Result post = sampleCanonicalVmc(
                updatedState,
                postConfiguration,
                baseline.samples(),
                VMC_PARALLELISM);
        long postSrVmcNanos = System.nanoTime() - postStartedNanos;

        EnergyStatistics postEnergy = energyStatistics(post.localEnergies());
        requireOperationalAcceptance(post.acceptance(), "post-SR");

        double deltaEnergy = postEnergy.mean() - baselineEnergy.mean();
        String direction = deltaEnergy < 0.0 ? "DOWN" : deltaEnergy > 0.0 ? "UP" : "UNCHANGED";
        Instant finished = Instant.now();

        /* 5. Persist pilot evidence; never touch the frozen pretraining artifacts. */
        long persistenceStartedNanos = System.nanoTime();
        writeParameters(arguments.outputDirectory().resolve("post-sr-parameters.hex"),
                updatedState.parameterArray());
        writeWalkers(arguments.outputDirectory().resolve("baseline-retained-walkers.csv"),
                baseline.samples());
        writeWalkers(arguments.outputDirectory().resolve("post-sr-retained-walkers.csv"),
                post.samples());
        writeEnergySamples(arguments.outputDirectory().resolve("baseline-local-energy-samples.csv"),
                baseline.localEnergies());
        writeEnergySamples(arguments.outputDirectory().resolve("post-sr-local-energy-samples.csv"),
                post.localEnergies());
        writeSummary(arguments, baseline, baselineEnergy, sr, post, postEnergy,
                deltaEnergy, direction, started, finished);
        long persistenceNanos = System.nanoTime() - persistenceStartedNanos;
        long totalDriverNanos = System.nanoTime() - driverStartedNanos;

        System.out.printf(Locale.ROOT, """
                FERMINET_H2O_SR_DRIVER_TIMING
                  baseline_vmc_ms=%.3f
                  sr_iteration_ms=%.3f
                  post_sr_vmc_ms=%.3f
                  persistence_ms=%.3f
                  total_driver_ms=%.3f

                """,
                milliseconds(baselineVmcNanos),
                milliseconds(srIterationNanos),
                milliseconds(postSrVmcNanos),
                milliseconds(persistenceNanos),
                milliseconds(totalDriverNanos));

        System.out.printf(Locale.ROOT, """
                H2O canonical one-step SR run complete.
                --------------------------------
                baseline acceptance     : %.6f
                baseline energy         : %+.10f +/- %.10f Ha

                post-SR acceptance      : %.6f
                post-SR energy          : %+.10f +/- %.10f Ha

                delta E (after-before)  : %+.10f Ha
                observed direction      : %s

                SR steps executed       : 1
                additional SR steps     : 0
                scientific acceptance   : NOT YET DETERMINED
                forces evaluated        : false

                STOP: inspect this result before any second SR update.
                """,
                baseline.acceptance(),
                baselineEnergy.mean(),
                baselineEnergy.standardError(),
                post.acceptance(),
                postEnergy.mean(),
                postEnergy.standardError(),
                deltaEnergy,
                direction);
    }

    static FermiNetVmc.Result sampleCanonicalVmc(
            FermiNetV1State state,
            FermiNetVmc.Configuration configuration,
            List<QuantumCoordinates> initialWalkers,
            int parallelism) {
        try (FermiNetVmcParallel sampler = new FermiNetVmcParallel(parallelism)) {
            return sampler.sample(state, configuration, initialWalkers);
        }
    }

    static int canonicalVmcParallelism() {
        return VMC_PARALLELISM;
    }

    private static double milliseconds(long nanoseconds) {
        return nanoseconds / 1_000_000.0;
    }

    private static void requireOperationalAcceptance(double acceptance, String stage) {
        if (!Double.isFinite(acceptance)
                || acceptance < MIN_ACCEPTANCE
                || acceptance > MAX_ACCEPTANCE) {
            throw new IllegalStateException(stage
                    + " VMC acceptance outside pilot-operational range: " + acceptance);
        }
    }

    private static void verifyFiniteParameters(FermiNetV1State state) {
        double[] parameters = state.parameterArray();
        for (int i = 0; i < parameters.length; i++) {
            if (!Double.isFinite(parameters[i])) {
                throw new IllegalStateException("non-finite updated parameter at index " + i);
            }
        }
    }

    private static void verifySamplingParity(
            FermiNetV1State state,
            List<QuantumCoordinates> walkers) {

        int checks = Math.min(4, walkers.size());
        for (int i = 0; i < checks; i++) {
            QuantumCoordinates coordinates = walkers.get(i);
            var fast = state.samplingEvaluation(coordinates);
            var full = state.spatialEvaluation(coordinates);
            if (fast.sign() != full.sign()) {
                throw new IllegalStateException("sampling/spatial sign mismatch at walker " + i);
            }
            double delta = Math.abs(
                    fast.logAbsoluteWavefunction() - full.logAbsoluteWavefunction());
            if (delta > 1.0e-12) {
                throw new IllegalStateException(
                        "sampling/spatial log|Psi| mismatch at walker " + i + ": " + delta);
            }
        }
    }

    private static EnergyStatistics energyStatistics(List<LocalEnergyComponents> energies) {
        if (energies.size() < 2) {
            throw new IllegalArgumentException("at least two local-energy samples are required");
        }
        double sum = 0.0;
        for (LocalEnergyComponents energy : energies) {
            double value = energy.totalHartree();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("non-finite local energy");
            }
            sum += value;
        }
        double mean = sum / energies.size();
        double sumSquared = 0.0;
        for (LocalEnergyComponents energy : energies) {
            double delta = energy.totalHartree() - mean;
            sumSquared += delta * delta;
        }
        double variance = sumSquared / (energies.size() - 1);
        double standardDeviation = Math.sqrt(variance);
        double standardError = standardDeviation / Math.sqrt(energies.size());
        return new EnergyStatistics(energies.size(), mean, standardDeviation, standardError);
    }

    private static double[] readParameters(Path file, int expectedCount) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("missing pretrained parameters: " + file);
        }
        double[] values = new double[expectedCount];
        boolean[] seen = new boolean[expectedCount];
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] fields = trimmed.split("\\s+");
            if (fields.length != 2) {
                throw new IOException("invalid pretrained parameter line: " + line);
            }
            int index;
            double value;
            try {
                index = Integer.parseInt(fields[0]);
                value = Double.parseDouble(fields[1]);
            } catch (NumberFormatException exception) {
                throw new IOException("invalid pretrained parameter line: " + line, exception);
            }
            if (index < 0 || index >= expectedCount || seen[index]) {
                throw new IOException("invalid/duplicate parameter index: " + index);
            }
            if (!Double.isFinite(value)) {
                throw new IOException("non-finite pretrained parameter: " + index);
            }
            values[index] = value;
            seen[index] = true;
        }
        for (int i = 0; i < expectedCount; i++) {
            if (!seen[i]) throw new IOException("missing pretrained parameter index: " + i);
        }
        return values;
    }

    private static List<QuantumCoordinates> readWalkers(Path file, Molecule molecule)
            throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("missing pretrained walkers: " + file);
        }
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) throw new IOException("pretrained walker artifact is empty");

        Map<Integer, List<QuantumCoordinates.ParticleCoordinate>> grouped = new LinkedHashMap<>();
        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex).trim();
            if (line.isEmpty()) continue;
            String[] fields = line.split(",");
            if (fields.length != 6) throw new IOException("invalid walker CSV line: " + line);
            try {
                int walker = Integer.parseInt(fields[0]);
                int electron = Integer.parseInt(fields[1]);
                SpinProjection spin = SpinProjection.valueOf(fields[2]);
                double x = Double.parseDouble(fields[3]);
                double y = Double.parseDouble(fields[4]);
                double z = Double.parseDouble(fields[5]);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    throw new IOException("non-finite walker coordinate");
                }
                grouped.computeIfAbsent(walker, ignored -> new ArrayList<>())
                        .add(new QuantumCoordinates.ParticleCoordinate(electron, x, y, z, spin));
            } catch (IllegalArgumentException exception) {
                throw new IOException("invalid walker CSV line: " + line, exception);
            }
        }
        if (grouped.isEmpty()) throw new IOException("pretrained walker artifact is empty");

        List<QuantumCoordinates> result = new ArrayList<>();
        int expectedWalker = 0;
        for (var entry : grouped.entrySet()) {
            if (entry.getKey() != expectedWalker++) {
                throw new IOException("walker indices must be contiguous from zero");
            }
            List<QuantumCoordinates.ParticleCoordinate> particles = entry.getValue();
            particles.sort(Comparator.comparingInt(
                    QuantumCoordinates.ParticleCoordinate::particleIndex));
            if (particles.size() != molecule.electrons().value()) {
                throw new IOException("walker electron count mismatch");
            }
            for (int i = 0; i < particles.size(); i++) {
                var particle = particles.get(i);
                if (particle.particleIndex() != i) {
                    throw new IOException("walker particle ordering mismatch");
                }
                SpinProjection expected = i < molecule.spin().alphaElectrons()
                        ? SpinProjection.ALPHA : SpinProjection.BETA;
                if (particle.spin() != expected) {
                    throw new IOException("walker spin ordering mismatch");
                }
            }
            result.add(new QuantumCoordinates(particles));
        }
        return result;
    }

    private static void writeParameters(Path file, double[] parameters) throws IOException {
        StringBuilder text = new StringBuilder("# index value_hex\n");
        for (int i = 0; i < parameters.length; i++) {
            text.append(i).append(' ').append(Double.toHexString(parameters[i])).append('\n');
        }
        Files.writeString(file, text.toString(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void writeWalkers(Path file, List<QuantumCoordinates> walkers)
            throws IOException {
        StringBuilder csv = new StringBuilder(
                "sample,electron,spin,x_bohr_hex,y_bohr_hex,z_bohr_hex\n");
        for (int sample = 0; sample < walkers.size(); sample++) {
            for (var electron : walkers.get(sample).particles()) {
                csv.append(sample).append(',')
                        .append(electron.particleIndex()).append(',')
                        .append(electron.spin()).append(',')
                        .append(Double.toHexString(electron.xBohr())).append(',')
                        .append(Double.toHexString(electron.yBohr())).append(',')
                        .append(Double.toHexString(electron.zBohr())).append('\n');
            }
        }
        Files.writeString(file, csv.toString(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void writeEnergySamples(Path file, List<LocalEnergyComponents> energies)
            throws IOException {
        StringBuilder csv = new StringBuilder(
                "sample,kinetic_hartree,electron_nuclear_hartree,"
                        + "electron_electron_hartree,nuclear_nuclear_hartree,total_hartree\n");
        for (int i = 0; i < energies.size(); i++) {
            LocalEnergyComponents energy = energies.get(i);
            csv.append(i).append(',')
                    .append(Double.toHexString(energy.kineticHartree())).append(',')
                    .append(Double.toHexString(energy.electronNuclearHartree())).append(',')
                    .append(Double.toHexString(energy.electronElectronHartree())).append(',')
                    .append(Double.toHexString(energy.nuclearNuclearHartree())).append(',')
                    .append(Double.toHexString(energy.totalHartree())).append('\n');
        }
        Files.writeString(file, csv.toString(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void writeSummary(
            Arguments arguments,
            FermiNetVmc.Result baseline,
            EnergyStatistics baselineEnergy,
            FermiNetMatrixFreeSrOptimizer.Result sr,
            FermiNetVmc.Result post,
            EnergyStatistics postEnergy,
            double deltaEnergy,
            String direction,
            Instant started,
            Instant finished) throws IOException {

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schema", "prometheus-ferminet-h2o-sr-v2");
        summary.put("started_utc", started.toString());
        summary.put("finished_utc", finished.toString());
        summary.put("preset", arguments.preset());
        summary.put("parameter_input", arguments.parameterFile().toString());
        summary.put("walker_input", arguments.walkerFile().toString());
        summary.put("parameter_count", sr.state().parameterCount());
        summary.put("sr_steps_executed", 1);
        summary.put("additional_sr_steps_executed", 0);

        Map<String, Object> baselineMap = new LinkedHashMap<>();
        baselineMap.put("acceptance", baseline.acceptance());
        baselineMap.put("samples", baselineEnergy.count());
        baselineMap.put("mean_energy_hartree", baselineEnergy.mean());
        baselineMap.put("sample_standard_deviation_hartree", baselineEnergy.standardDeviation());
        baselineMap.put("naive_standard_error_hartree", baselineEnergy.standardError());
        summary.put("baseline", baselineMap);

        Map<String, Object> srMap = new LinkedHashMap<>();
        srMap.put("learning_rate", arguments.learningRate());
        srMap.put("damping", arguments.damping());
        srMap.put("max_update_norm", arguments.maxUpdateNorm());
        srMap.put("solver", "STRUCTURED_JACOBIAN_FREE_SAMPLE_SPACE_CHOLESKY_SR");
        srMap.put("observation_parallelism", arguments.observationParallelism());
        srMap.put("gradient_norm", sr.gradientNorm());
        srMap.put("raw_update_norm", sr.rawUpdateNorm());
        srMap.put("applied_update_norm", sr.appliedUpdateNorm());
        srMap.put("update_rescaled", sr.updateRescaled());
        srMap.put("solver_iterations", sr.solverIterations());
        srMap.put("relative_true_residual", sr.relativeTrueResidual());
        srMap.put("sample_evaluations", sr.sampleEvaluations());
        srMap.put("sr_sample_mean_energy_hartree", sr.initialEnergyHartree());
        summary.put("sr", srMap);

        Map<String, Object> postMap = new LinkedHashMap<>();
        postMap.put("acceptance", post.acceptance());
        postMap.put("samples", postEnergy.count());
        postMap.put("mean_energy_hartree", postEnergy.mean());
        postMap.put("sample_standard_deviation_hartree", postEnergy.standardDeviation());
        postMap.put("naive_standard_error_hartree", postEnergy.standardError());
        summary.put("post_sr", postMap);

        summary.put("delta_energy_hartree", deltaEnergy);
        summary.put("observed_energy_direction", direction);
        summary.put("scientifically_accepted", false);
        summary.put("forces_evaluated", false);
        summary.put("continue_sr_automatically", false);

        JSON.writeValue(arguments.outputDirectory().resolve("sr-summary.json").toFile(), summary);
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-v1-water",
                List.of(
                        new NuclearCenter(0, "O", new NuclearCharge(8),
                                new CartesianPosition(0.0, 0.0, 0.0, LengthUnit.BOHR)),
                        new NuclearCenter(1, "H", new NuclearCharge(1),
                                new CartesianPosition(1.7952398191849366, 0.0, 0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(2, "H", new NuclearCharge(1),
                                new CartesianPosition(-0.46464225035067114,
                                        1.7340684963325879, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(5, 5, 1));
    }

    private record EnergyStatistics(
            int count,
            double mean,
            double standardDeviation,
            double standardError) {}

    private record Arguments(
            String preset,
            Path parameterFile,
            Path walkerFile,
            Path outputDirectory,
            int sampleCount,
            int retainedPerWalker,
            int warmupSweeps,
            int sweepsBetweenRetained,
            double stepSizeBohr,
            long baselineSeed,
            long postSrSeed,
            double learningRate,
            double damping,
            double maxUpdateNorm,
            int parameterBlockSize,
            int observationParallelism) {

        private static Arguments parse(String[] args) {
            String preset = "historical-n64";
            Path repositoryRoot = Path.of("/Users/yazan/totah-lab");
            Path parameters = repositoryRoot.resolve(
                    "artifacts/prometheus/h2o/ferminet/pretrained/parameters.hex");
            Path walkers = repositoryRoot.resolve(
                    "artifacts/prometheus/h2o/ferminet/pretrained/walkers.csv");
            Path output = repositoryRoot.resolve(
                    "artifacts/prometheus/h2o/ferminet/sr/latest");
            int sampleCount = 64;
            int retainedPerWalker = 1;
            int warmupSweeps = 100;
            int sweepsBetweenRetained = 10;
            double stepSizeBohr = 0.02;
            long baselineSeed = 20260818L;
            long postSrSeed = 20260819L;
            double learningRate = 0.01;
            double damping = 1.0;
            double maxUpdateNorm = 0.05;
            int parameterBlockSize = 8192;
            int observationParallelism = 12;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--preset" -> preset = value(args, ++i, "--preset");
                    case "--parameters" -> parameters = Path.of(value(args, ++i, "--parameters"));
                    case "--walkers" -> walkers = Path.of(value(args, ++i, "--walkers"));
                    case "--output" -> output = Path.of(value(args, ++i, "--output"));
                    case "--sample-count" -> sampleCount = integer(args, ++i, "--sample-count");
                    case "--retained-per-walker" -> retainedPerWalker = integer(args, ++i, "--retained-per-walker");
                    case "--warmup-sweeps" -> warmupSweeps = integer(args, ++i, "--warmup-sweeps");
                    case "--sweeps-between-retained" -> sweepsBetweenRetained = integer(args, ++i, "--sweeps-between-retained");
                    case "--step-size-bohr" -> stepSizeBohr = decimal(args, ++i, "--step-size-bohr");
                    case "--baseline-seed" -> baselineSeed = number(args, ++i, "--baseline-seed");
                    case "--post-sr-seed" -> postSrSeed = number(args, ++i, "--post-sr-seed");
                    case "--learning-rate" -> learningRate = decimal(args, ++i, "--learning-rate");
                    case "--damping" -> damping = decimal(args, ++i, "--damping");
                    case "--max-update-norm" -> maxUpdateNorm = decimal(args, ++i, "--max-update-norm");
                    case "--block-size" -> parameterBlockSize = integer(args, ++i, "--block-size");
                    case "--observation-parallelism" -> observationParallelism = integer(args, ++i, "--observation-parallelism");
                    default -> throw usage("unknown argument: " + args[i]);
                }
            }
            if (!"historical-n64".equals(preset)) throw usage("unknown preset: " + preset);
            if (sampleCount < 2 || retainedPerWalker < 1
                    || sampleCount % retainedPerWalker != 0
                    || warmupSweeps < 0 || sweepsBetweenRetained < 1
                    || !(stepSizeBohr > 0.0) || !Double.isFinite(stepSizeBohr)
                    || !(learningRate > 0.0) || !Double.isFinite(learningRate)
                    || !(damping > 0.0) || !Double.isFinite(damping)
                    || !(maxUpdateNorm > 0.0) || !Double.isFinite(maxUpdateNorm)
                    || parameterBlockSize < 1 || observationParallelism < 1) {
                throw usage("invalid numeric configuration");
            }
            return new Arguments(preset,
                    parameters.toAbsolutePath().normalize(),
                    walkers.toAbsolutePath().normalize(),
                    output.toAbsolutePath().normalize(),
                    sampleCount, retainedPerWalker, warmupSweeps, sweepsBetweenRetained,
                    stepSizeBohr, baselineSeed, postSrSeed, learningRate, damping,
                    maxUpdateNorm, parameterBlockSize, observationParallelism);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) throw usage(option + " requires a value");
            return args[index];
        }

        private static int integer(String[] args, int index, String option) {
            try {
                return Integer.parseInt(value(args, index, option));
            } catch (NumberFormatException exception) {
                throw usage(option + " requires an integer");
            }
        }

        private static long number(String[] args, int index, String option) {
            try {
                return Long.parseLong(value(args, index, option));
            } catch (NumberFormatException exception) {
                throw usage(option + " requires an integer");
            }
        }

        private static double decimal(String[] args, int index, String option) {
            try {
                return Double.parseDouble(value(args, index, option));
            } catch (NumberFormatException exception) {
                throw usage(option + " requires a number");
            }
        }

        private static IllegalArgumentException usage(String problem) {
            return new IllegalArgumentException(problem + System.lineSeparator() + """
                    --preset historical-n64
                    --parameters PATH --walkers PATH --output PATH
                    [--sample-count N] [--retained-per-walker N]
                    [--warmup-sweeps N] [--sweeps-between-retained N]
                    [--step-size-bohr X] [--baseline-seed N] [--post-sr-seed N]
                    [--learning-rate X] [--damping X] [--max-update-norm X]
                    [--block-size N] [--observation-parallelism N]
                    """);
        }
    }
}
