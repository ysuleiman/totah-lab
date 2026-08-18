package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.pretraining.GaussianHartreeFockOrbitalTargetTest;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * One-shot benchmark for the FermiNet SR observation derivative stage.
 *
 * <p>This is a performance diagnostic, not a scientific acceptance test.
 *
 * <p>The benchmark measures:
 *
 * <ul>
 *     <li>legacy serial {@link FermiNetSrObservationFile#build} performance;</li>
 *     <li>{@link FermiNetSrObservationFile#buildParallel} with one worker;</li>
 *     <li>parallel scaling across a bounded worker-count sweep;</li>
 *     <li>neural-evaluation count invariance.</li>
 * </ul>
 *
 * <p>The derivative-memory numbers printed here are lower-bound estimates only.
 * {@code Evaluation.parameterLogDerivatives()} returns a clone, and neural-network
 * evaluation itself creates additional transient state.
 */
public final class FermiNetSrObservationFileParallelBenchmark {

    private static final int SAMPLE_COUNT = 64;
    private static final int MEASURED_ROUNDS = 1;

    private static final int[] REQUESTED_PARALLELISMS = {
            1, 2, 4, 6, 8, 12
    };

    private FermiNetSrObservationFileParallelBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            args = new String[]{"/Users/yazan/totah-lab"};
            // throw new IllegalArgumentException("expected repository root path");
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();

        Path pretraining = root.resolve(
                "software/modules/analysis/prometheus-ferminet-h2o-pretraining");

        Path pilot = root.resolve(
                "software/modules/analysis/prometheus-ferminet-h2o-sr-pilot");

        Molecule molecule = GaussianHartreeFockOrbitalTargetTest.water();

        FermiNetV1Configuration configuration =
                FermiNetV1Configuration.locked();

        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(configuration, molecule);

        FermiNetV1State state = new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.fromArray(
                        layout,
                        readParameters(
                                pretraining.resolve("pretrained-parameters.hex"),
                                layout.parameterCount())));

        List<QuantumCoordinates> allCoordinates = readWalkers(
                pilot.resolve("baseline-retained-walkers.csv"),
                molecule);

        if (allCoordinates.size() < SAMPLE_COUNT) {
            throw new IllegalStateException(
                    "benchmark requires at least "
                            + SAMPLE_COUNT
                            + " walkers but found "
                            + allCoordinates.size());
        }

        List<QuantumCoordinates> coordinates =
                List.copyOf(allCoordinates.subList(0, SAMPLE_COUNT));

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples =
                coordinates.stream()
                        .map(value ->
                                new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                        1.0,
                                        value))
                        .toList();

        int availableProcessors =
                Runtime.getRuntime().availableProcessors();

        int[] parallelisms = Arrays.stream(REQUESTED_PARALLELISMS)
                .filter(value -> value <= availableProcessors)
                .distinct()
                .sorted()
                .toArray();

        if (parallelisms.length == 0 || parallelisms[0] != 1) {
            throw new IllegalStateException(
                    "parallelism sweep must contain one worker");
        }

        System.out.printf("""
                FERMINET_SR_OBSERVATION_PARALLEL_BENCHMARK
                  parameters=%d
                  samples=%d
                  available_processors=%d
                  measured_rounds=%d
                  requested_parallelisms=%s
                  tested_parallelisms=%s
                """,
                state.parameterCount(),
                samples.size(),
                availableProcessors,
                MEASURED_ROUNDS,
                Arrays.toString(REQUESTED_PARALLELISMS),
                Arrays.toString(parallelisms));

        /*
         * Full-workload warmup.
         *
         * The first pass warms the serial implementation and shared neural code.
         * The second warms buildParallel with one worker.
         * A final bounded multi-worker pass warms executor/concurrent paths.
         *
         * None of these timings are included in reported measurements.
         */
        warmUp(
                state,
                samples,
                Math.min(4, availableProcessors));

        /*
         * Measure legacy serial build separately.
         *
         * This tells us whether buildParallel(..., 1) itself introduces
         * significant implementation overhead.
         */
        long[] legacySerialRounds = new long[MEASURED_ROUNDS];

        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            legacySerialRounds[round] =
                    measureSerial(state, samples);
        }

        long legacySerialNanos =
                median(legacySerialRounds);

        /*
         * Run all parallelism configurations.
         *
         * Alternate sweep direction between rounds so that higher worker counts
         * do not systematically benefit from always running later in the JVM.
         */
        Map<Integer, long[]> measuredByParallelism =
                new LinkedHashMap<>();

        for (int parallelism : parallelisms) {
            measuredByParallelism.put(
                    parallelism,
                    new long[MEASURED_ROUNDS]);
        }

        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            if ((round & 1) == 0) {
                for (int parallelism : parallelisms) {
                    measuredByParallelism
                            .get(parallelism)[round] =
                            measureParallel(
                                    state,
                                    samples,
                                    parallelism);
                }
            } else {
                for (int index = parallelisms.length - 1;
                     index >= 0;
                     index--) {

                    int parallelism = parallelisms[index];

                    measuredByParallelism
                            .get(parallelism)[round] =
                            measureParallel(
                                    state,
                                    samples,
                                    parallelism);
                }
            }
        }

        long parallelOneNanos =
                median(measuredByParallelism.get(1));

        long derivativeBytesPerSample =
                Math.multiplyExact(
                        (long) state.parameterCount(),
                        Double.BYTES);

        System.out.println();
        System.out.printf("""
                BASELINES
                  legacy_serial_wall_seconds=%.9f
                  parallel_p1_wall_seconds=%.9f
                  parallel_p1_vs_legacy_ratio=%.9f
                  parallel_p1_overhead_percent=%.3f
                """,
                seconds(legacySerialNanos),
                seconds(parallelOneNanos),
                (double) parallelOneNanos / legacySerialNanos,
                100.0
                        * (parallelOneNanos - legacySerialNanos)
                        / legacySerialNanos);

        System.out.println();

        System.out.printf(
                "%-12s %-15s %-15s %-15s %-24s%n",
                "parallelism",
                "seconds",
                "speedup",
                "efficiency",
                "derivative_lower_bound");

        int fastestParallelism = -1;
        long fastestNanos = Long.MAX_VALUE;

        for (int parallelism : parallelisms) {
            long nanos =
                    median(measuredByParallelism.get(parallelism));

            double speedup =
                    (double) parallelOneNanos / nanos;

            double efficiency =
                    speedup / parallelism;

            long derivativeLowerBound =
                    Math.multiplyExact(
                            derivativeBytesPerSample,
                            parallelism);

            System.out.printf(
                    "%-12d %-15.9f %-15.6f %-15.6f %-24d%n",
                    parallelism,
                    seconds(nanos),
                    speedup,
                    efficiency,
                    derivativeLowerBound);

            if (nanos < fastestNanos) {
                fastestNanos = nanos;
                fastestParallelism = parallelism;
            }
        }

        System.out.println();

        System.out.printf("""
                RESULT
                  fastest_parallelism=%d
                  fastest_wall_seconds=%.9f
                  fastest_speedup_vs_parallel_p1=%.9f
                  fastest_speedup_vs_legacy_serial=%.9f
                  neural_evaluations_per_run=%d
                  derivative_bytes_per_sample=%d
                  full_64_sample_derivative_matrix_bytes=%d
                  memory_note=reported derivative byte counts are lower bounds only; Evaluation.parameterLogDerivatives returns a clone and neural evaluation has additional transient allocations
                """,
                fastestParallelism,
                seconds(fastestNanos),
                (double) parallelOneNanos / fastestNanos,
                (double) legacySerialNanos / fastestNanos,
                samples.size(),
                derivativeBytesPerSample,
                Math.multiplyExact(
                        derivativeBytesPerSample,
                        samples.size()));
    }

    private static long measureSerial(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples)
            throws IOException {

        long started = System.nanoTime();

        try (FermiNetSrObservationFile observations =
                     FermiNetSrObservationFile.build(
                             state,
                             samples)) {

            verifyEvaluationCount(
                    "serial",
                    observations,
                    samples.size());

            observations.printTiming();
        }

        return System.nanoTime() - started;
    }

    private static long measureParallel(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            int parallelism)
            throws IOException {

        long started = System.nanoTime();

        try (FermiNetSrObservationFile observations =
                     FermiNetSrObservationFile.buildParallel(
                             state,
                             samples,
                             parallelism)) {

            verifyEvaluationCount(
                    "parallelism=" + parallelism,
                    observations,
                    samples.size());

            observations.printTiming();
        }

        return System.nanoTime() - started;
    }

    private static void verifyEvaluationCount(
            String label,
            FermiNetSrObservationFile observations,
            int expected) {

        if (observations.neuralEvaluations() != expected) {
            throw new IllegalStateException(
                    label
                            + " neural evaluation count mismatch: expected "
                            + expected
                            + " but found "
                            + observations.neuralEvaluations());
        }
    }

    private static void warmUp(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            int multiWorkerParallelism)
            throws IOException {

        try (FermiNetSrObservationFile observations =
                     FermiNetSrObservationFile.build(
                             state,
                             samples)) {

            verifyEvaluationCount(
                    "warmup serial",
                    observations,
                    samples.size());
        }

        try (FermiNetSrObservationFile observations =
                     FermiNetSrObservationFile.buildParallel(
                             state,
                             samples,
                             1)) {

            verifyEvaluationCount(
                    "warmup parallelism=1",
                    observations,
                    samples.size());
        }

        if (multiWorkerParallelism > 1) {
            try (FermiNetSrObservationFile observations =
                         FermiNetSrObservationFile.buildParallel(
                                 state,
                                 samples,
                                 multiWorkerParallelism)) {

                verifyEvaluationCount(
                        "warmup parallelism="
                                + multiWorkerParallelism,
                        observations,
                        samples.size());
            }
        }
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static double seconds(long nanos) {
        return nanos / 1.0e9;
    }

    private static double[] readParameters(
            Path file,
            int count)
            throws IOException {

        double[] values = new double[count];
        boolean[] seen = new boolean[count];

        for (String line : Files.readAllLines(file)) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()
                    || trimmed.startsWith("#")) {
                continue;
            }

            String[] fields =
                    trimmed.split("\\s+");

            if (fields.length < 2) {
                throw new IOException(
                        "invalid parameter line: " + line);
            }

            int index =
                    Integer.parseInt(fields[0]);

            if (index < 0 || index >= count) {
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
                    Double.parseDouble(fields[1]);

            seen[index] = true;
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
                Files.readAllLines(file);

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

            if (fields.length < 6) {
                throw new IOException(
                        "invalid walker line "
                                + (lineIndex + 1)
                                + ": "
                                + line);
            }

            int walker =
                    Integer.parseInt(fields[0]);

            grouped.computeIfAbsent(
                            walker,
                            ignored -> new ArrayList<>())
                    .add(
                            new QuantumCoordinates.ParticleCoordinate(
                                    Integer.parseInt(fields[1]),
                                    Double.parseDouble(fields[3]),
                                    Double.parseDouble(fields[4]),
                                    Double.parseDouble(fields[5]),
                                    SpinProjection.valueOf(fields[2])));
        }

        List<QuantumCoordinates> result =
                new ArrayList<>();

        for (var entry : grouped.entrySet()) {
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
                    new QuantumCoordinates(particles));
        }

        return result;
    }
}