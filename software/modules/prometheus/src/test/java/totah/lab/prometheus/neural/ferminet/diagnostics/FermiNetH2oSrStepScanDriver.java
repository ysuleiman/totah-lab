package totah.lab.prometheus.neural.ferminet.diagnostics;

import totah.lab.prometheus.neural.ferminet.pretraining.GaussianHartreeFockOrbitalTargetTest;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * H2O SR step-length diagnostic.
 *
 * <p>Computes the current sample-space SR direction exactly once from the same
 * pretrained H2O state and 64 retained walkers, then evaluates several positive
 * step lengths along that direction using the same VMC protocol and seed.
 *
 * <p>This driver does not adopt or overwrite any parameter state.
 */
public final class FermiNetH2oSrStepScanDriver {

    private static final int SAMPLE_COUNT = 64;

    /*
     * Same SR damping as the real one-step H2O pilot.
     */
    private static final double SR_DAMPING = 1.0;

    /*
     * Diagnostic SR solve:
     *
     * learningRate = 1.0 means the returned parameter displacement is the raw
     * SR direction delta.
     *
     * Very large maxUpdateNorm prevents diagnostic clipping.
     */
    private static final double DIAGNOSTIC_LEARNING_RATE = 1.0;
    private static final double DIAGNOSTIC_MAX_UPDATE_NORM = 1.0e100;

    private static final int OBSERVATION_PARALLELISM = 12;
    private static final int BLOCK_SIZE = 8192;

    private static final int MAX_SOLVER_ITERATIONS = 50;
    private static final double RELATIVE_TOLERANCE = 1.0e-6;
    private static final double ABSOLUTE_TOLERANCE = 1.0e-8;

    /*
     * Same VMC protocol as FermiNetH2oSrDriver.
     */
    private static final int VMC_WARMUP_SWEEPS = 100;
    private static final int VMC_RETAINED_PER_WALKER = 1;
    private static final int VMC_SWEEPS_BETWEEN_RETAINED = 10;
    private static final double VMC_STEP_SIZE_BOHR = 0.02;

    /*
     * Use the SAME seed for every alpha.
     *
     * This is deliberate for this directional diagnostic so stochastic
     * differences are reduced as much as possible.
     */
    private static final long STEP_SCAN_SEED = 20260820L;

    /*
     * Current production step is 0.01.
     *
     * These points determine whether:
     *
     *   - small positive steps improve,
     *   - only the large step fails,
     *   - or the entire positive direction fails in VMC.
     */
    private static final double[] ALPHAS = {
            0.0,
            0.001,
            0.0025,
            0.005,
            0.010
    };

    private static final double MIN_ACCEPTANCE = 0.20;
    private static final double MAX_ACCEPTANCE = 0.90;

    private FermiNetH2oSrStepScanDriver() {
    }

    public static void main(String[] args)
            throws Exception {

        Path root =
                args.length == 0
                        ? Path.of("/Users/yazan/totah-lab")
                        : Path.of(args[0]);

        root =
                root.toAbsolutePath()
                        .normalize();

        Path pretraining =
                root.resolve(
                        "software/modules/analysis/"
                                + "prometheus-ferminet-h2o-pretraining");

        Path pilot =
                root.resolve(
                        "software/modules/analysis/"
                                + "prometheus-ferminet-h2o-sr-pilot");

        Path output =
                root.resolve(
                        "software/modules/analysis/"
                                + "prometheus-ferminet-h2o-sr-step-scan");

        Files.createDirectories(output);

        Path parameterFile =
                pretraining.resolve(
                        "pretrained-parameters.hex");

        Path walkerFile =
                pilot.resolve(
                        "baseline-retained-walkers.csv");

        requireFile(parameterFile);
        requireFile(walkerFile);

        Molecule molecule =
                GaussianHartreeFockOrbitalTargetTest.water();

        FermiNetV1Configuration networkConfiguration =
                FermiNetV1Configuration.locked();

        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(
                        networkConfiguration,
                        molecule);

        double[] initialParameters =
                readParameters(
                        parameterFile,
                        layout.parameterCount());

        FermiNetV1State initialState =
                createState(
                        molecule,
                        networkConfiguration,
                        layout,
                        initialParameters);

        List<QuantumCoordinates> allWalkers =
                readWalkers(
                        walkerFile,
                        molecule);

        if (allWalkers.size() < SAMPLE_COUNT) {
            throw new IllegalStateException(
                    "need "
                            + SAMPLE_COUNT
                            + " walkers but found "
                            + allWalkers.size());
        }

        List<QuantumCoordinates> walkers =
                List.copyOf(
                        allWalkers.subList(
                                0,
                                SAMPLE_COUNT));

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> srSamples =
                walkers.stream()
                        .map(
                                coordinates ->
                                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                                1.0,
                                                coordinates))
                        .toList();

        System.out.printf(Locale.ROOT, """
                FERMINET_H2O_SR_STEP_SCAN
                ------------------------
                parameters             : %d
                walkers                : %d
                damping                : %.8g
                observation workers    : %d
                parameter block size   : %d

                VMC:
                  warmup sweeps        : %d
                  retained/walker      : %d
                  sweeps/retained      : %d
                  step size (bohr)     : %.8g
                  common seed          : %d

                Computing SR direction ONCE...
                %n""",
                initialState.parameterCount(),
                walkers.size(),
                SR_DAMPING,
                OBSERVATION_PARALLELISM,
                BLOCK_SIZE,
                VMC_WARMUP_SWEEPS,
                VMC_RETAINED_PER_WALKER,
                VMC_SWEEPS_BETWEEN_RETAINED,
                VMC_STEP_SIZE_BOHR,
                STEP_SCAN_SEED);

        /*
         * ============================================================
         * Compute the SR direction once.
         * ============================================================
         */

        FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration =
                new FermiNetMatrixFreeSrOptimizer.Configuration(
                        DIAGNOSTIC_LEARNING_RATE,
                        SR_DAMPING,
                        DIAGNOSTIC_MAX_UPDATE_NORM,
                        OBSERVATION_PARALLELISM,
                        BLOCK_SIZE,
                        MAX_SOLVER_ITERATIONS,
                        RELATIVE_TOLERANCE,
                        ABSOLUTE_TOLERANCE);

        FermiNetMatrixFreeSrOptimizer.Result sr =
                new FermiNetMatrixFreeSrOptimizer()
                        .oneIteration(
                                initialState,
                                srSamples,
                                srConfiguration);

        if (sr.updateRescaled()) {
            throw new IllegalStateException(
                    "diagnostic SR direction was unexpectedly rescaled");
        }

        double[] oneStepParameters =
                FermiNetStateAccess.parameterSnapshot(sr.state());

        double[] delta =
                subtract(
                        oneStepParameters,
                        initialParameters);

        double deltaNorm =
                norm(delta);

        double gradientDotDelta =
                dot(
                        sr.energyGradient(),
                        delta);

        System.out.printf(Locale.ROOT, """
                SR direction complete.
                  SR energy             : %+.10f Ha
                  gradient norm         : %.10e
                  delta norm            : %.10e
                  gradient dot delta    : %.10e
                  relative residual     : %.10e

                Beginning VMC step scan...

                %n""",
                sr.initialEnergyHartree(),
                sr.gradientNorm(),
                deltaNorm,
                gradientDotDelta,
                sr.relativeTrueResidual());

        List<ScanPoint> scan =
                new ArrayList<>();

        EnergyStatistics baseline =
                null;

        for (double alpha : ALPHAS) {

            double[] parameters =
                    addScaled(
                            initialParameters,
                            delta,
                            alpha);

            FermiNetV1State state =
                    createState(
                            molecule,
                            networkConfiguration,
                            layout,
                            parameters);

            verifyFiniteParameters(
                    state);

            FermiNetRuntimeSampling.Request vmcConfiguration =
                    new FermiNetRuntimeSampling.Request(
                            walkers.size(),
                            VMC_WARMUP_SWEEPS,
                            VMC_RETAINED_PER_WALKER,
                            VMC_SWEEPS_BETWEEN_RETAINED,
                            VMC_STEP_SIZE_BOHR,
                            STEP_SCAN_SEED);

            long started =
                    System.nanoTime();

            FermiNetRuntimeSampling.Result vmc =
                    FermiNetRuntimeSampling.sampleSerial(
                            state, vmcConfiguration, walkers);

            long wallNanos =
                    System.nanoTime()
                            - started;

            requireOperationalAcceptance(
                    vmc.acceptance(),
                    alpha);

            EnergyStatistics statistics =
                    energyStatistics(
                            vmc.localEnergies());

            if (alpha == 0.0) {
                baseline =
                        statistics;
            }

            if (baseline == null) {
                throw new IllegalStateException(
                        "alpha=0 baseline must be evaluated first");
            }

            double deltaEnergy =
                    statistics.mean()
                            - baseline.mean();

            double deltaEnergySe =
                    Math.hypot(
                            statistics.standardError(),
                            baseline.standardError());

            ScanPoint point =
                    new ScanPoint(
                            alpha,
                            alpha * deltaNorm,
                            vmc.acceptance(),
                            statistics,
                            deltaEnergy,
                            deltaEnergySe,
                            wallNanos / 1.0e9);

            scan.add(
                    point);

            System.out.printf(Locale.ROOT, """
                    alpha=%-8.5f
                      parameter_displacement_norm = %.10e
                      acceptance                 = %.6f
                      energy                     = %+.10f +/- %.10f Ha
                      delta_E_vs_alpha0           = %+.10f +/- %.10f Ha
                      wall_seconds                = %.3f

                    %n""",
                    point.alpha(),
                    point.parameterDisplacementNorm(),
                    point.acceptance(),
                    point.energy().mean(),
                    point.energy().standardError(),
                    point.deltaEnergy(),
                    point.deltaEnergyStandardError(),
                    point.wallSeconds());
        }

        /*
         * ============================================================
         * Persist only diagnostic table.
         * No parameter files are written.
         * ============================================================
         */

        Path csv =
                output.resolve(
                        "sr-step-scan.csv");

        writeCsv(
                csv,
                scan);

        ScanPoint best =
                scan.stream()
                        .min(
                                Comparator.comparingDouble(
                                        point ->
                                                point.energy().mean()))
                        .orElseThrow();

        System.out.printf(Locale.ROOT, """
                ============================================================
                STEP SCAN COMPLETE
                ============================================================

                alpha=0 energy:
                  %+.10f +/- %.10f Ha

                lowest observed mean:
                  alpha              = %.5f
                  displacement norm  = %.10e
                  energy             = %+.10f +/- %.10f Ha
                  delta E            = %+.10f +/- %.10f Ha

                diagnostic CSV:
                  %s

                No parameters were adopted or written.

                Interpretation:
                  - if small positive alpha lowers energy but 0.01 raises it,
                    the SR direction is correct and the production step is too large.
                  - if every positive alpha raises energy, investigate the
                    gradient/local-energy relationship rather than the linear solve.
                  - if uncertainty dominates all differences, increase the VMC
                    validation sample size before choosing a learning rate.
                ============================================================
                %n""",
                baseline.mean(),
                baseline.standardError(),

                best.alpha(),
                best.parameterDisplacementNorm(),
                best.energy().mean(),
                best.energy().standardError(),
                best.deltaEnergy(),
                best.deltaEnergyStandardError(),

                csv);
    }

    private static FermiNetV1State createState(
            Molecule molecule,
            FermiNetV1Configuration configuration,
            FermiNetParameterLayout layout,
            double[] parameters) {

        return new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.fromArray(
                        layout,
                        parameters));
    }

    private static double[] subtract(
            double[] left,
            double[] right) {

        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "vector dimension mismatch");
        }

        double[] result =
                new double[left.length];

        for (int i = 0;
             i < result.length;
             i++) {

            result[i] =
                    left[i]
                            - right[i];
        }

        return result;
    }

    private static double[] addScaled(
            double[] parameters,
            double[] delta,
            double alpha) {

        if (parameters.length != delta.length) {
            throw new IllegalArgumentException(
                    "vector dimension mismatch");
        }

        double[] result =
                parameters.clone();

        for (int i = 0;
             i < result.length;
             i++) {

            result[i] +=
                    alpha
                            * delta[i];

            if (!Double.isFinite(result[i])) {
                throw new IllegalStateException(
                        "non-finite parameter at index "
                                + i
                                + " for alpha "
                                + alpha);
            }
        }

        return result;
    }

    private static double dot(
            double[] left,
            double[] right) {

        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "vector dimension mismatch");
        }

        double value =
                0.0;

        for (int i = 0;
             i < left.length;
             i++) {

            value +=
                    left[i]
                            * right[i];
        }

        return value;
    }

    private static double norm(
            double[] values) {

        double scale =
                0.0;

        double sum =
                1.0;

        for (double value : values) {

            double absolute =
                    Math.abs(value);

            if (absolute == 0.0) {
                continue;
            }

            if (scale < absolute) {

                double ratio =
                        scale
                                / absolute;

                sum =
                        1.0
                                + sum
                                * ratio
                                * ratio;

                scale =
                        absolute;

            } else {

                double ratio =
                        absolute
                                / scale;

                sum +=
                        ratio
                                * ratio;
            }
        }

        return scale == 0.0
                ? 0.0
                : scale
                * Math.sqrt(sum);
    }

    private static void verifyFiniteParameters(
            FermiNetV1State state) {

        double[] parameters =
                FermiNetStateAccess.parameterSnapshot(state);

        for (int i = 0;
             i < parameters.length;
             i++) {

            if (!Double.isFinite(parameters[i])) {
                throw new IllegalStateException(
                        "non-finite parameter at index "
                                + i);
            }
        }
    }

    private static void requireOperationalAcceptance(
            double acceptance,
            double alpha) {

        if (!Double.isFinite(acceptance)
                || acceptance < MIN_ACCEPTANCE
                || acceptance > MAX_ACCEPTANCE) {

            throw new IllegalStateException(
                    "VMC acceptance outside operational range"
                            + " for alpha="
                            + alpha
                            + ": "
                            + acceptance);
        }
    }

    private static EnergyStatistics energyStatistics(
            List<LocalEnergyComponents> energies) {

        if (energies.size() < 2) {
            throw new IllegalArgumentException(
                    "at least two local-energy samples are required");
        }

        double sum =
                0.0;

        for (LocalEnergyComponents energy : energies) {

            double value =
                    energy.totalHartree();

            if (!Double.isFinite(value)) {
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

        for (LocalEnergyComponents energy : energies) {

            double difference =
                    energy.totalHartree()
                            - mean;

            sumSquared +=
                    difference
                            * difference;
        }

        double variance =
                sumSquared
                        / (energies.size() - 1);

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

    private static void writeCsv(
            Path file,
            List<ScanPoint> scan)
            throws IOException {

        StringBuilder csv =
                new StringBuilder();

        csv.append(
                "alpha,"
                        + "parameter_displacement_norm,"
                        + "acceptance,"
                        + "mean_energy_hartree,"
                        + "standard_deviation_hartree,"
                        + "standard_error_hartree,"
                        + "delta_energy_vs_alpha0_hartree,"
                        + "delta_energy_standard_error_hartree,"
                        + "wall_seconds\n");

        for (ScanPoint point : scan) {

            csv.append(
                            Double.toHexString(
                                    point.alpha()))
                    .append(',')

                    .append(
                            Double.toHexString(
                                    point.parameterDisplacementNorm()))
                    .append(',')

                    .append(
                            Double.toHexString(
                                    point.acceptance()))
                    .append(',')

                    .append(
                            Double.toHexString(
                                    point.energy().mean()))
                    .append(',')

                    .append(
                            Double.toHexString(
                                    point.energy().standardDeviation()))
                    .append(',')

                    .append(
                            Double.toHexString(
                                    point.energy().standardError()))
                    .append(',')

                    .append(
                            Double.toHexString(
                                    point.deltaEnergy()))
                    .append(',')

                    .append(
                            Double.toHexString(
                                    point.deltaEnergyStandardError()))
                    .append(',')

                    .append(
                            Double.toHexString(
                                    point.wallSeconds()))
                    .append('\n');
        }

        Files.writeString(
                file,
                csv.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void requireFile(
            Path file)
            throws IOException {

        if (!Files.isRegularFile(file)) {
            throw new IOException(
                    "missing file: "
                            + file);
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

            if (fields.length != 2) {
                throw new IOException(
                        "invalid parameter line: "
                                + line);
            }

            int index =
                    Integer.parseInt(
                            fields[0]);

            if (index < 0
                    || index >= count
                    || seen[index]) {

                throw new IOException(
                        "invalid parameter index: "
                                + index);
            }

            double value =
                    Double.parseDouble(
                            fields[1]);

            if (!Double.isFinite(value)) {
                throw new IOException(
                        "non-finite parameter "
                                + index);
            }

            values[index] =
                    value;

            seen[index] =
                    true;
        }

        for (int i = 0;
             i < count;
             i++) {

            if (!seen[i]) {
                throw new IOException(
                        "missing parameter "
                                + i);
            }
        }

        return values;
    }

    private static List<QuantumCoordinates> readWalkers(
            Path file,
            Molecule molecule)
            throws IOException {

        List<String> lines =
                Files.readAllLines(file);

        Map<Integer, List<QuantumCoordinates.ParticleCoordinate>> grouped =
                new LinkedHashMap<>();

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

            if (fields.length != 6) {
                throw new IOException(
                        "invalid walker line: "
                                + line);
            }

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
        }

        List<QuantumCoordinates> result =
                new ArrayList<>();

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
                        "walker electron count mismatch");
            }

            result.add(
                    new QuantumCoordinates(
                            particles));
        }

        return result;
    }

    private record EnergyStatistics(
            int count,
            double mean,
            double standardDeviation,
            double standardError) {
    }

    private record ScanPoint(
            double alpha,
            double parameterDisplacementNorm,
            double acceptance,
            EnergyStatistics energy,
            double deltaEnergy,
            double deltaEnergyStandardError,
            double wallSeconds) {
    }
}
