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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

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
 * H2O FermiNet N=1024 one-step SR sample-size experiment.
 *
 * <p>The first 512 SR configurations are loaded verbatim from the frozen
 * N=512 pilot artifact. Exactly 512 additional configurations are generated
 * from those frozen configurations using the same pretrained parameter state.
 *
 * <p>The SR update is always applied to the original pretrained state, never
 * to the N=1024 post-SR state. Therefore this experiment compares:
 *
 * <pre>
 * delta_128(theta0)  versus  delta_256(theta0)
 * </pre>
 *
 * and is not a second SR optimization step.
 */
public final class FermiNetH2oSr1024Driver {

    private static final int EXPECTED_FROZEN_SAMPLES = 512;
    private static final int TARGET_SAMPLES = 1024;

    /*
     * Extension sampling starts from the exact frozen N=1024 configurations.
     * No additional warmup is used; this is a new preregistered extension
     * chain with a new fixed seed, not a claim of exact RNG-state continuation.
     */
    private static final int EXTENSION_WARMUP_SWEEPS = 0;
    private static final int EXTENSION_RETAINED_PER_WALKER = 1;
    private static final int EXTENSION_SWEEPS_BETWEEN_RETAINED = 10;
    private static final double VMC_STEP_SIZE_BOHR = 0.02;
    private static final long EXTENSION_VMC_SEED = 20260828L;

    /* Independent post-SR VMC. */
    private static final int POST_SR_WARMUP_SWEEPS = 100;
    private static final int POST_SR_RETAINED_PER_WALKER = 1;
    private static final int POST_SR_SWEEPS_BETWEEN_RETAINED = 10;
    private static final long POST_SR_VMC_SEED = 20260829L;

    /* Locked SR values from the N=512 experiment. */
    private static final double SR_LEARNING_RATE = 0.01;
    private static final double SR_DAMPING = 1.0;
    private static final double SR_MAX_UPDATE_NORM = 0.05;
    private static final int SR_PARAMETER_BLOCK_SIZE = 128;

    /*
     * Retained only because Configuration still carries the legacy fields.
     * The current production optimizer uses sample-space Cholesky, not PCG.
     */
    private static final int LEGACY_MAX_SOLVER_ITERATIONS = 50;
    private static final double LEGACY_RELATIVE_TOLERANCE = 1.0e-6;
    private static final double LEGACY_ABSOLUTE_TOLERANCE = 1.0e-8;

    private static final double MIN_ACCEPTANCE = 0.20;
    private static final double MAX_ACCEPTANCE = 0.90;

    private static final int VMC_PARALLELISM =
            Math.max(
                    1,
                    Runtime.getRuntime()
                            .availableProcessors());

    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .enable(
                            SerializationFeature.INDENT_OUTPUT);

    private FermiNetH2oSr1024Driver() {
    }

    public static void main(String[] args)
            throws Exception {

        Arguments arguments =
                Arguments.parse(args);

        Files.createDirectories(
                arguments.outputDirectory());

        Molecule molecule =
                water();

        FermiNetV1Configuration configuration =
                FermiNetV1Configuration.locked();

        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(
                        configuration,
                        molecule);

        double[] parameterValues =
                readParameters(
                        arguments.pretrainingDirectory()
                                .resolve(
                                        "pretrained-parameters.hex"),
                        layout.parameterCount());

        FermiNetV1State initialState =
                new FermiNetV1State(
                        molecule,
                        configuration,
                        FermiNetParameters.fromArray(
                                layout,
                                parameterValues));

        if (initialState.parameterCount()
                != 737_376) {
            throw new IllegalStateException(
                    "unexpected H2O parameter count: "
                            + initialState.parameterCount()
                            + " expected=737376");
        }

        /*
         * Load the exact N=1024 SR configurations from the completed N=512 experiment.
         */
        List<QuantumCoordinates> frozen512 =
                readWalkers(
                        arguments.frozenSamplesFile(),
                        molecule);

        if (frozen512.size()
                != EXPECTED_FROZEN_SAMPLES) {
            throw new IllegalStateException(
                    "frozen N=512 sample count mismatch: "
                            + frozen512.size());
        }

        verifySamplingParity(
                initialState,
                frozen512);

        System.out.printf(
                Locale.ROOT,
                """
                Prometheus FermiNet H2O N=1024 SR sample-size experiment
                -------------------------------------------------------
                pretraining input      : %s
                frozen N=512 samples    : %s
                output                 : %s
                parameters             : %d
                frozen samples         : %d
                target samples         : %d

                extension VMC:
                  implementation       : PARALLEL_DETERMINISTIC
                  parallelism          : %d
                  warmup sweeps        : %d
                  retained/walker      : %d
                  sweeps/retained      : %d
                  step size (bohr)     : %.8g
                  seed                 : %d

                SR:
                  formulation          : SAMPLE_SPACE_CHOLESKY
                  learning rate        : %.8g
                  damping              : %.8g
                  max update norm      : %.8g
                  parameter block size : %d

                IMPORTANT:
                  this starts from the ORIGINAL pretrained state theta0
                  this is NOT SR step 2

                """,
                arguments.pretrainingDirectory(),
                arguments.frozenSamplesFile(),
                arguments.outputDirectory(),
                initialState.parameterCount(),
                frozen512.size(),
                TARGET_SAMPLES,
                VMC_PARALLELISM,
                EXTENSION_WARMUP_SWEEPS,
                EXTENSION_RETAINED_PER_WALKER,
                EXTENSION_SWEEPS_BETWEEN_RETAINED,
                VMC_STEP_SIZE_BOHR,
                EXTENSION_VMC_SEED,
                SR_LEARNING_RATE,
                SR_DAMPING,
                SR_MAX_UPDATE_NORM,
                SR_PARAMETER_BLOCK_SIZE);

        Instant started =
                Instant.now();

        /*
         * Generate ONLY the additional 512 configurations.
         */
        FermiNetVmc.Configuration extensionConfiguration =
                new FermiNetVmc.Configuration(
                        frozen512.size(),
                        EXTENSION_WARMUP_SWEEPS,
                        EXTENSION_RETAINED_PER_WALKER,
                        EXTENSION_SWEEPS_BETWEEN_RETAINED,
                        VMC_STEP_SIZE_BOHR,
                        EXTENSION_VMC_SEED);

        FermiNetVmc.Result extension;

        try (FermiNetVmcParallel parallelVmc =
                     new FermiNetVmcParallel(
                             VMC_PARALLELISM)) {

            extension =
                    parallelVmc.sample(
                            initialState,
                            extensionConfiguration,
                            frozen512);
        }

        requireOperationalAcceptance(
                extension.acceptance(),
                "extension");

        if (extension.samples().size()
                != EXPECTED_FROZEN_SAMPLES) {
            throw new IllegalStateException(
                    "extension sample count mismatch: "
                            + extension.samples().size());
        }

        List<QuantumCoordinates> samples1024 =
                new ArrayList<>(
                        TARGET_SAMPLES);

        samples1024.addAll(
                frozen512);

        samples1024.addAll(
                extension.samples());

        if (samples1024.size()
                != TARGET_SAMPLES) {
            throw new IllegalStateException(
                    "combined N=1024 sample count mismatch: "
                            + samples1024.size());
        }

        /*
         * Explicitly verify that samples 0..63 remain byte-for-byte equivalent
         * at the coordinate-value level to the frozen input list.
         */
        verifyFrozenPrefix(
                frozen512,
                samples1024);

        /*
         * Evaluate the N=1024 baseline energies on the exact SR sample set.
         * This is energy-only; derivative-complete SR evaluations happen once
         * inside the sample-space optimizer.
         */
        List<LocalEnergyComponents> baselineEnergies =
                localEnergies(
                        initialState,
                        samples1024);

        EnergyStatistics baselineEnergy =
                energyStatistics(
                        baselineEnergies);

        System.out.printf(
                Locale.ROOT,
                """
                N=1024 sample set complete
                -------------------------
                frozen prefix samples : %d
                newly added samples   : %d
                extension acceptance  : %.6f
                baseline energy       : %+.10f +/- %.10f Ha
                baseline stddev       : %.10f Ha

                """,
                frozen512.size(),
                extension.samples().size(),
                extension.acceptance(),
                baselineEnergy.mean(),
                baselineEnergy.standardError(),
                baselineEnergy.standardDeviation());

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> srSamples =
                new ArrayList<>(
                        TARGET_SAMPLES);

        for (QuantumCoordinates coordinates :
                samples1024) {

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
                        SR_PARAMETER_BLOCK_SIZE,
                        LEGACY_MAX_SOLVER_ITERATIONS,
                        LEGACY_RELATIVE_TOLERANCE,
                        LEGACY_ABSOLUTE_TOLERANCE);

        System.out.println(
                "Starting exactly ONE N=1024 SR update from theta0...");

        FermiNetMatrixFreeSrOptimizer.Result sr =
                new FermiNetMatrixFreeSrOptimizer()
                        .oneIteration(
                                initialState,
                                srSamples,
                                srConfiguration);

        if (sr.sampleEvaluations()
                != TARGET_SAMPLES) {
            throw new IllegalStateException(
                    "sample-space SR evaluated "
                            + sr.sampleEvaluations()
                            + " samples; expected "
                            + TARGET_SAMPLES);
        }

        FermiNetV1State updatedState =
                sr.state();

        verifyFiniteParameters(
                updatedState);

        verifySamplingParity(
                updatedState,
                samples1024);

        System.out.printf(
                Locale.ROOT,
                """
                N=1024 SR solve complete
                -----------------------
                SR initial energy       : %+.10f Ha
                gradient norm           : %.10e
                sample-space solves     : %d
                relative residual       : %.10e
                derivative sweeps       : %d
                neural evaluations      : %d
                raw update norm         : %.10e
                applied update norm     : %.10e
                update rescaled         : %s

                """,
                sr.initialEnergyHartree(),
                sr.gradientNorm(),
                sr.solverIterations(),
                sr.relativeTrueResidual(),
                sr.streamedOperatorPasses(),
                sr.sampleEvaluations(),
                sr.rawUpdateNorm(),
                sr.appliedUpdateNorm(),
                sr.updateRescaled());

        /*
         * Independent post-SR VMC begins from all 128 N=1024 SR configurations.
         */
        FermiNetVmc.Configuration postConfiguration =
                new FermiNetVmc.Configuration(
                        samples1024.size(),
                        POST_SR_WARMUP_SWEEPS,
                        POST_SR_RETAINED_PER_WALKER,
                        POST_SR_SWEEPS_BETWEEN_RETAINED,
                        VMC_STEP_SIZE_BOHR,
                        POST_SR_VMC_SEED);

        FermiNetVmc.Result post;

        try (FermiNetVmcParallel parallelVmc =
                     new FermiNetVmcParallel(
                             VMC_PARALLELISM)) {

            post =
                    parallelVmc.sample(
                            updatedState,
                            postConfiguration,
                            samples1024);
        }

        requireOperationalAcceptance(
                post.acceptance(),
                "post-SR");

        EnergyStatistics postEnergy =
                energyStatistics(
                        post.localEnergies());

        double deltaEnergy =
                postEnergy.mean()
                        - baselineEnergy.mean();

        String direction =
                deltaEnergy < 0.0
                        ? "DOWN"
                        : deltaEnergy > 0.0
                                ? "UP"
                                : "UNCHANGED";

        Instant finished =
                Instant.now();

        /*
         * Persist N=1024 evidence in a separate directory. Never overwrite N=512.
         */
        writeParameters(
                arguments.outputDirectory()
                        .resolve(
                                "post-sr-parameters.hex"),
                updatedState.parameterArray());

        writeWalkers(
                arguments.outputDirectory()
                        .resolve(
                                "frozen-n512-prefix.csv"),
                frozen512);

        writeWalkers(
                arguments.outputDirectory()
                        .resolve(
                                "extension-n512.csv"),
                extension.samples());

        writeWalkers(
                arguments.outputDirectory()
                        .resolve(
                                "sr-n1024-samples.csv"),
                samples1024);

        writeWalkers(
                arguments.outputDirectory()
                        .resolve(
                                "post-sr-retained-walkers.csv"),
                post.samples());

        writeEnergySamples(
                arguments.outputDirectory()
                        .resolve(
                                "baseline-n512-local-energy-samples.csv"),
                baselineEnergies);

        writeEnergySamples(
                arguments.outputDirectory()
                        .resolve(
                                "post-sr-local-energy-samples.csv"),
                post.localEnergies());

        writeSummary(
                arguments,
                extension,
                baselineEnergy,
                sr,
                post,
                postEnergy,
                deltaEnergy,
                direction,
                started,
                finished);

        System.out.printf(
                Locale.ROOT,
                """
                H2O N=1024 one-step SR experiment complete.
                -------------------------------------------
                baseline N=1024 energy  : %+.10f +/- %.10f Ha
                post-SR energy         : %+.10f +/- %.10f Ha

                delta E (after-before) : %+.10f Ha
                observed direction     : %s

                SR samples             : %d
                SR steps executed      : 1
                additional SR steps    : 0
                start state            : ORIGINAL PRETRAINED theta0
                forces evaluated       : false

                STOP: inspect N=1024 convergence before any larger-sample run.
                """,
                baselineEnergy.mean(),
                baselineEnergy.standardError(),
                postEnergy.mean(),
                postEnergy.standardError(),
                deltaEnergy,
                direction,
                TARGET_SAMPLES);
    }

    private static List<LocalEnergyComponents> localEnergies(
            FermiNetV1State state,
            List<QuantumCoordinates> samples) {

        LocalEnergyComponents[] result =
                new LocalEnergyComponents[
                        samples.size()];

        AtomicReference<RuntimeException> failure =
                new AtomicReference<>();

        ForkJoinPool pool =
                new ForkJoinPool(
                        VMC_PARALLELISM);

        try {
            pool.submit(
                            () ->
                                    IntStream.range(
                                                    0,
                                                    samples.size())
                                            .parallel()
                                            .forEach(
                                                    index -> {
                                                        if (failure.get()
                                                                != null) {
                                                            return;
                                                        }

                                                        try {
                                                            result[index] =
                                                                    FermiNetVmc.localEnergy(
                                                                            state,
                                                                            samples.get(
                                                                                    index));
                                                        } catch (RuntimeException exception) {
                                                            failure.compareAndSet(
                                                                    null,
                                                                    exception);
                                                        }
                                                    }))
                    .join();
        } finally {
            pool.shutdown();
        }

        RuntimeException exception =
                failure.get();

        if (exception != null) {
            throw exception;
        }

        return List.of(
                result);
    }

    private static void verifyFrozenPrefix(
            List<QuantumCoordinates> frozen,
            List<QuantumCoordinates> combined) {

        if (combined.size()
                < frozen.size()) {
            throw new IllegalArgumentException(
                    "combined sample set shorter than frozen prefix");
        }

        for (int sample = 0;
             sample < frozen.size();
             sample++) {

            List<QuantumCoordinates.ParticleCoordinate> expected =
                    frozen.get(sample)
                            .particles();

            List<QuantumCoordinates.ParticleCoordinate> actual =
                    combined.get(sample)
                            .particles();

            if (expected.size()
                    != actual.size()) {
                throw new IllegalStateException(
                        "frozen prefix particle count mismatch at sample "
                                + sample);
            }

            for (int particle = 0;
                 particle < expected.size();
                 particle++) {

                var left =
                        expected.get(
                                particle);

                var right =
                        actual.get(
                                particle);

                if (left.particleIndex()
                        != right.particleIndex()
                        || left.spin()
                                != right.spin()
                        || Double.doubleToRawLongBits(
                                left.xBohr())
                                != Double.doubleToRawLongBits(
                                        right.xBohr())
                        || Double.doubleToRawLongBits(
                                left.yBohr())
                                != Double.doubleToRawLongBits(
                                        right.yBohr())
                        || Double.doubleToRawLongBits(
                                left.zBohr())
                                != Double.doubleToRawLongBits(
                                        right.zBohr())) {

                    throw new IllegalStateException(
                            "frozen prefix changed at sample="
                                    + sample
                                    + " particle="
                                    + particle);
                }
            }
        }
    }

    private static void requireOperationalAcceptance(
            double acceptance,
            String stage) {

        if (!Double.isFinite(
                acceptance)
                || acceptance
                        < MIN_ACCEPTANCE
                || acceptance
                        > MAX_ACCEPTANCE) {

            throw new IllegalStateException(
                    stage
                            + " VMC acceptance outside operational range: "
                            + acceptance);
        }
    }

    private static void verifyFiniteParameters(
            FermiNetV1State state) {

        double[] parameters =
                state.parameterArray();

        for (int i = 0;
             i < parameters.length;
             i++) {

            if (!Double.isFinite(
                    parameters[i])) {

                throw new IllegalStateException(
                        "non-finite updated parameter at index "
                                + i);
            }
        }
    }

    private static void verifySamplingParity(
            FermiNetV1State state,
            List<QuantumCoordinates> walkers) {

        int checks =
                Math.min(
                        4,
                        walkers.size());

        for (int i = 0;
             i < checks;
             i++) {

            QuantumCoordinates coordinates =
                    walkers.get(
                            i);

            var fast =
                    state.samplingEvaluation(
                            coordinates);

            var full =
                    state.spatialEvaluation(
                            coordinates);

            if (fast.sign()
                    != full.sign()) {

                throw new IllegalStateException(
                        "sampling/spatial sign mismatch at walker "
                                + i);
            }

            double delta =
                    Math.abs(
                            fast.logAbsoluteWavefunction()
                                    - full.logAbsoluteWavefunction());

            if (delta
                    > 1.0e-12) {

                throw new IllegalStateException(
                        "sampling/spatial log|Psi| mismatch at walker "
                                + i
                                + ": "
                                + delta);
            }
        }
    }

    private static EnergyStatistics energyStatistics(
            List<LocalEnergyComponents> energies) {

        if (energies.size()
                < 2) {

            throw new IllegalArgumentException(
                    "at least two local-energy samples are required");
        }

        double sum =
                0.0;

        for (LocalEnergyComponents energy :
                energies) {

            double value =
                    energy.totalHartree();

            if (!Double.isFinite(
                    value)) {

                throw new IllegalArgumentException(
                        "non-finite local energy");
            }

            sum +=
                    value;
        }

        double mean =
                sum
                        / energies.size();

        double sumSquared =
                0.0;

        for (LocalEnergyComponents energy :
                energies) {

            double delta =
                    energy.totalHartree()
                            - mean;

            sumSquared +=
                    delta
                            * delta;
        }

        double variance =
                sumSquared
                        / (energies.size()
                                - 1);

        double standardDeviation =
                Math.sqrt(
                        variance);

        double standardError =
                standardDeviation
                        / Math.sqrt(
                                energies.size());

        return new EnergyStatistics(
                energies.size(),
                mean,
                standardDeviation,
                standardError);
    }

    private static double[] readParameters(
            Path file,
            int expectedCount)
            throws IOException {

        if (!Files.isRegularFile(
                file)) {

            throw new IOException(
                    "missing pretrained parameters: "
                            + file);
        }

        double[] values =
                new double[
                        expectedCount];

        boolean[] seen =
                new boolean[
                        expectedCount];

        for (String line :
                Files.readAllLines(
                        file)) {

            String trimmed =
                    line.trim();

            if (trimmed.isEmpty()
                    || trimmed.startsWith(
                            "#")) {
                continue;
            }

            String[] fields =
                    trimmed.split(
                            "\\s+");

            if (fields.length
                    != 2) {

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
                    || index
                            >= expectedCount
                    || seen[index]) {

                throw new IOException(
                        "invalid/duplicate parameter index: "
                                + index);
            }

            if (!Double.isFinite(
                    value)) {

                throw new IOException(
                        "non-finite pretrained parameter: "
                                + index);
            }

            values[index] =
                    value;

            seen[index] =
                    true;
        }

        for (int i = 0;
             i < expectedCount;
             i++) {

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

        if (!Files.isRegularFile(
                file)) {

            throw new IOException(
                    "missing walker artifact: "
                            + file);
        }

        List<String> lines =
                Files.readAllLines(
                        file);

        if (lines.isEmpty()) {
            throw new IOException(
                    "walker artifact is empty");
        }

        Map<Integer, List<QuantumCoordinates.ParticleCoordinate>> grouped =
                new LinkedHashMap<>();

        for (int lineIndex = 1;
             lineIndex < lines.size();
             lineIndex++) {

            String line =
                    lines.get(
                                    lineIndex)
                            .trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] fields =
                    line.split(
                            ",");

            if (fields.length
                    != 6) {

                throw new IOException(
                        "invalid walker CSV line: "
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
                            "non-finite walker coordinate");
                }

                grouped.computeIfAbsent(
                                walker,
                                ignored ->
                                        new ArrayList<>())
                        .add(
                                new QuantumCoordinates.ParticleCoordinate(
                                        electron,
                                        x,
                                        y,
                                        z,
                                        spin));

            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "invalid walker CSV line: "
                                + line,
                        exception);
            }
        }

        if (grouped.isEmpty()) {
            throw new IOException(
                    "walker artifact is empty");
        }

        List<QuantumCoordinates> result =
                new ArrayList<>();

        int expectedWalker =
                0;

        for (var entry :
                grouped.entrySet()) {

            if (entry.getKey()
                    != expectedWalker++) {

                throw new IOException(
                        "walker indices must be contiguous from zero");
            }

            List<QuantumCoordinates.ParticleCoordinate> particles =
                    entry.getValue();

            particles.sort(
                    Comparator.comparingInt(
                            QuantumCoordinates.ParticleCoordinate::particleIndex));

            if (particles.size()
                    != molecule.electrons()
                            .value()) {

                throw new IOException(
                        "walker electron count mismatch");
            }

            for (int i = 0;
                 i < particles.size();
                 i++) {

                var particle =
                        particles.get(
                                i);

                if (particle.particleIndex()
                        != i) {

                    throw new IOException(
                            "walker particle ordering mismatch");
                }

                SpinProjection expected =
                        i
                                < molecule.spin()
                                        .alphaElectrons()
                                ? SpinProjection.ALPHA
                                : SpinProjection.BETA;

                if (particle.spin()
                        != expected) {

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

    private static void writeParameters(
            Path file,
            double[] parameters)
            throws IOException {

        StringBuilder text =
                new StringBuilder(
                        "# index value_hex\n");

        for (int i = 0;
             i < parameters.length;
             i++) {

            text.append(
                            i)
                    .append(
                            ' ')
                    .append(
                            Double.toHexString(
                                    parameters[i]))
                    .append(
                            '\n');
        }

        Files.writeString(
                file,
                text.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void writeWalkers(
            Path file,
            List<QuantumCoordinates> walkers)
            throws IOException {

        StringBuilder csv =
                new StringBuilder(
                        "sample,electron,spin,x_bohr_hex,y_bohr_hex,z_bohr_hex\n");

        for (int sample = 0;
             sample < walkers.size();
             sample++) {

            for (var electron :
                    walkers.get(
                                    sample)
                            .particles()) {

                csv.append(
                                sample)
                        .append(
                                ',')
                        .append(
                                electron.particleIndex())
                        .append(
                                ',')
                        .append(
                                electron.spin())
                        .append(
                                ',')
                        .append(
                                Double.toHexString(
                                        electron.xBohr()))
                        .append(
                                ',')
                        .append(
                                Double.toHexString(
                                        electron.yBohr()))
                        .append(
                                ',')
                        .append(
                                Double.toHexString(
                                        electron.zBohr()))
                        .append(
                                '\n');
            }
        }

        Files.writeString(
                file,
                csv.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void writeEnergySamples(
            Path file,
            List<LocalEnergyComponents> energies)
            throws IOException {

        StringBuilder csv =
                new StringBuilder(
                        "sample,kinetic_hartree,electron_nuclear_hartree,"
                                + "electron_electron_hartree,nuclear_nuclear_hartree,total_hartree\n");

        for (int i = 0;
             i < energies.size();
             i++) {

            LocalEnergyComponents energy =
                    energies.get(
                            i);

            csv.append(
                            i)
                    .append(
                            ',')
                    .append(
                            Double.toHexString(
                                    energy.kineticHartree()))
                    .append(
                            ',')
                    .append(
                            Double.toHexString(
                                    energy.electronNuclearHartree()))
                    .append(
                            ',')
                    .append(
                            Double.toHexString(
                                    energy.electronElectronHartree()))
                    .append(
                            ',')
                    .append(
                            Double.toHexString(
                                    energy.nuclearNuclearHartree()))
                    .append(
                            ',')
                    .append(
                            Double.toHexString(
                                    energy.totalHartree()))
                    .append(
                            '\n');
        }

        Files.writeString(
                file,
                csv.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void writeSummary(
            Arguments arguments,
            FermiNetVmc.Result extension,
            EnergyStatistics baselineEnergy,
            FermiNetMatrixFreeSrOptimizer.Result sr,
            FermiNetVmc.Result post,
            EnergyStatistics postEnergy,
            double deltaEnergy,
            String direction,
            Instant started,
            Instant finished)
            throws IOException {

        Map<String, Object> summary =
                new LinkedHashMap<>();

        summary.put(
                "schema",
                "prometheus-ferminet-h2o-sr-sample-size-n1024-v1");

        summary.put(
                "started_utc",
                started.toString());

        summary.put(
                "finished_utc",
                finished.toString());

        summary.put(
                "pretraining_directory",
                arguments.pretrainingDirectory()
                        .toString());

        summary.put(
                "frozen_n256_samples_file",
                arguments.frozenSamplesFile()
                        .toString());

        summary.put(
                "parameter_count",
                sr.state()
                        .parameterCount());

        summary.put(
                "target_sample_count",
                TARGET_SAMPLES);

        summary.put(
                "frozen_prefix_count",
                EXPECTED_FROZEN_SAMPLES);

        summary.put(
                "new_extension_count",
                extension.samples()
                        .size());

        summary.put(
                "extension_seed",
                EXTENSION_VMC_SEED);

        summary.put(
                "vmc_implementation",
                "PARALLEL_DETERMINISTIC");

        summary.put(
                "vmc_parallelism",
                VMC_PARALLELISM);

        summary.put(
                "extension_acceptance",
                extension.acceptance());

        summary.put(
                "start_state",
                "ORIGINAL_PRETRAINED_THETA0");

        summary.put(
                "sr_steps_executed",
                1);

        summary.put(
                "additional_sr_steps_executed",
                0);

        Map<String, Object> baselineMap =
                new LinkedHashMap<>();

        baselineMap.put(
                "samples",
                baselineEnergy.count());

        baselineMap.put(
                "mean_energy_hartree",
                baselineEnergy.mean());

        baselineMap.put(
                "sample_standard_deviation_hartree",
                baselineEnergy.standardDeviation());

        baselineMap.put(
                "naive_standard_error_hartree",
                baselineEnergy.standardError());

        summary.put(
                "baseline_n512",
                baselineMap);

        Map<String, Object> srMap =
                new LinkedHashMap<>();

        srMap.put(
                "formulation",
                "SAMPLE_SPACE_CHOLESKY");

        srMap.put(
                "learning_rate",
                SR_LEARNING_RATE);

        srMap.put(
                "damping",
                SR_DAMPING);

        srMap.put(
                "max_update_norm",
                SR_MAX_UPDATE_NORM);

        srMap.put(
                "parameter_block_size",
                SR_PARAMETER_BLOCK_SIZE);

        srMap.put(
                "gradient_norm",
                sr.gradientNorm());

        srMap.put(
                "raw_update_norm",
                sr.rawUpdateNorm());

        srMap.put(
                "applied_update_norm",
                sr.appliedUpdateNorm());

        srMap.put(
                "update_rescaled",
                sr.updateRescaled());

        srMap.put(
                "sample_space_solves",
                sr.solverIterations());

        srMap.put(
                "relative_sample_space_residual",
                sr.relativeTrueResidual());

        srMap.put(
                "derivative_sweeps",
                sr.streamedOperatorPasses());

        srMap.put(
                "neural_evaluations",
                sr.sampleEvaluations());

        srMap.put(
                "sr_sample_mean_energy_hartree",
                sr.initialEnergyHartree());

        summary.put(
                "sr",
                srMap);

        Map<String, Object> postMap =
                new LinkedHashMap<>();

        postMap.put(
                "acceptance",
                post.acceptance());

        postMap.put(
                "samples",
                postEnergy.count());

        postMap.put(
                "mean_energy_hartree",
                postEnergy.mean());

        postMap.put(
                "sample_standard_deviation_hartree",
                postEnergy.standardDeviation());

        postMap.put(
                "naive_standard_error_hartree",
                postEnergy.standardError());

        summary.put(
                "post_sr",
                postMap);

        summary.put(
                "delta_energy_hartree",
                deltaEnergy);

        summary.put(
                "observed_energy_direction",
                direction);

        summary.put(
                "scientifically_accepted",
                false);

        summary.put(
                "forces_evaluated",
                false);

        summary.put(
                "continue_sr_automatically",
                false);

        JSON.writeValue(
                arguments.outputDirectory()
                        .resolve(
                                "sr-n512-summary.json")
                        .toFile(),
                summary);
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-v1-water",
                List.of(
                        new NuclearCenter(
                                0,
                                "O",
                                new NuclearCharge(
                                        8),
                                new CartesianPosition(
                                        0.0,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(
                                        1),
                                new CartesianPosition(
                                        1.7952398191849366,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(
                                        1),
                                new CartesianPosition(
                                        -0.46464225035067114,
                                        1.7340684963325879,
                                        0.0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(
                        0),
                new ElectronCount(
                        10),
                new SpinSector(
                        5,
                        5,
                        1));
    }

    private record EnergyStatistics(
            int count,
            double mean,
            double standardDeviation,
            double standardError) {
    }

    private record Arguments(
            Path pretrainingDirectory,
            Path frozenSamplesFile,
            Path outputDirectory) {

        private static Arguments parse(
                String[] args) {

            Path pretraining =
                    Path.of(
                            "analysis",
                            "prometheus-ferminet-h2o-pretraining");

            Path frozenSamples =
                    Path.of(
                            "analysis",
                            "prometheus-ferminet-h2o-sr-n512",
                            "sr-n512-samples.csv");

            Path output =
                    Path.of(
                            "analysis",
                            "prometheus-ferminet-h2o-sr-n1024");

            for (int i = 0;
                 i < args.length;
                 i++) {

                switch (args[i]) {
                    case "--pretraining" -> {
                        if (++i
                                >= args.length) {

                            throw usage(
                                    "--pretraining requires a path");
                        }

                        pretraining =
                                Path.of(
                                        args[i]);
                    }

                    case "--frozen-samples" -> {
                        if (++i
                                >= args.length) {

                            throw usage(
                                    "--frozen-samples requires a path");
                        }

                        frozenSamples =
                                Path.of(
                                        args[i]);
                    }

                    case "--output" -> {
                        if (++i
                                >= args.length) {

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
                    pretraining.toAbsolutePath()
                            .normalize(),
                    frozenSamples.toAbsolutePath()
                            .normalize(),
                    output.toAbsolutePath()
                            .normalize());
        }

        private static IllegalArgumentException usage(
                String problem) {

            return new IllegalArgumentException(
                    problem
                            + System.lineSeparator()
                            + """
                            --pretraining PATH
                            --frozen-samples PATH
                            --output PATH
                            """);
        }
    }
}
