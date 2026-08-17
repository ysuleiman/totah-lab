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

/** One-shot derivative-stage benchmark; not an acceptance test. */
public final class FermiNetSrObservationFileParallelBenchmark {

    private static final int SAMPLE_COUNT = 8;
    private static final int MEASURED_ROUNDS = 3;

    private FermiNetSrObservationFileParallelBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("expected repository root path");
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path pretraining = root.resolve(
                "software/modules/analysis/prometheus-ferminet-h2o-pretraining");
        Path pilot = root.resolve(
                "software/modules/analysis/prometheus-ferminet-h2o-sr-pilot");

        Molecule molecule = GaussianHartreeFockOrbitalTargetTest.water();
        FermiNetV1Configuration configuration = FermiNetV1Configuration.locked();
        FermiNetParameterLayout layout = new FermiNetParameterLayout(configuration, molecule);
        FermiNetV1State state = new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.fromArray(
                        layout,
                        readParameters(
                                pretraining.resolve("pretrained-parameters.hex"),
                                layout.parameterCount())));

        List<QuantumCoordinates> coordinates = readWalkers(
                pilot.resolve("baseline-retained-walkers.csv"), molecule)
                .subList(0, SAMPLE_COUNT);
        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples = coordinates.stream()
                .map(value -> new FermiNetMatrixFreeSrOptimizer.WeightedSample(1.0, value))
                .toList();

        int available = Runtime.getRuntime().availableProcessors();
        int parallelism = Math.min(2, available);

        warmUp(state, samples.getFirst());

        long[] serialRounds = new long[MEASURED_ROUNDS];
        long[] parallelRounds = new long[MEASURED_ROUNDS];
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            if (round % 2 == 0) {
                serialRounds[round] = measureSerial(state, samples);
                parallelRounds[round] = measureParallel(state, samples, parallelism);
            } else {
                parallelRounds[round] = measureParallel(state, samples, parallelism);
                serialRounds[round] = measureSerial(state, samples);
            }
        }
        long serialNanos = median(serialRounds);
        long parallelNanos = median(parallelRounds);

        long derivativeBytes = Math.multiplyExact(
                (long) state.parameterCount(), Double.BYTES);

        System.out.printf("""
                FERMINET_SR_OBSERVATION_PARALLEL_BENCHMARK
                  parameters=%d
                  samples=%d
                  available_processors=%d
                  configured_parallelism=%d
                  serial_wall_seconds=%.9f
                  parallel_wall_seconds=%.9f
                  speedup=%.9f
                  serial_neural_evaluations=%d
                  parallel_neural_evaluations=%d
                  serial_estimated_derivative_bytes=%d
                  parallel_estimated_derivative_bytes=%d
                  memory_note=Evaluation.parameterLogDerivatives returns a clone; transient memory exceeds the simple derivative-vector estimate
                """,
                state.parameterCount(),
                samples.size(),
                available,
                parallelism,
                serialNanos / 1.0e9,
                parallelNanos / 1.0e9,
                (double) serialNanos / parallelNanos,
                samples.size(),
                samples.size(),
                derivativeBytes,
                Math.multiplyExact(derivativeBytes, parallelism));
    }

    private static long measureSerial(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples) throws IOException {
        long started = System.nanoTime();
        try (FermiNetSrObservationFile observations =
                     FermiNetSrObservationFile.build(state, samples)) {
            if (observations.neuralEvaluations() != samples.size()) {
                throw new IllegalStateException("serial evaluation count mismatch");
            }
        }
        return System.nanoTime() - started;
    }

    private static long measureParallel(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            int parallelism) throws IOException {
        long started = System.nanoTime();
        try (FermiNetSrObservationFile observations =
                     FermiNetSrObservationFile.buildParallel(
                             state, samples, parallelism)) {
            if (observations.neuralEvaluations() != samples.size()) {
                throw new IllegalStateException("parallel evaluation count mismatch");
            }
        }
        return System.nanoTime() - started;
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static void warmUp(
            FermiNetV1State state,
            FermiNetMatrixFreeSrOptimizer.WeightedSample sample) throws IOException {
        try (FermiNetSrObservationFile ignored =
                     FermiNetSrObservationFile.build(state, List.of(sample))) {
        }
        try (FermiNetSrObservationFile ignored =
                     FermiNetSrObservationFile.buildParallel(state, List.of(sample), 1)) {
        }
    }

    private static double[] readParameters(Path file, int count) throws IOException {
        double[] values = new double[count];
        boolean[] seen = new boolean[count];
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] fields = trimmed.split("\\s+");
            int index = Integer.parseInt(fields[0]);
            values[index] = Double.parseDouble(fields[1]);
            seen[index] = true;
        }
        for (int index = 0; index < count; index++) {
            if (!seen[index]) throw new IOException("missing parameter " + index);
        }
        return values;
    }

    private static List<QuantumCoordinates> readWalkers(Path file, Molecule molecule)
            throws IOException {
        Map<Integer, List<QuantumCoordinates.ParticleCoordinate>> grouped =
                new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file);
        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex).trim();
            if (line.isEmpty()) continue;
            String[] fields = line.split(",");
            int walker = Integer.parseInt(fields[0]);
            grouped.computeIfAbsent(walker, ignored -> new ArrayList<>()).add(
                    new QuantumCoordinates.ParticleCoordinate(
                            Integer.parseInt(fields[1]),
                            Double.parseDouble(fields[3]),
                            Double.parseDouble(fields[4]),
                            Double.parseDouble(fields[5]),
                            SpinProjection.valueOf(fields[2])));
        }
        List<QuantumCoordinates> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<QuantumCoordinates.ParticleCoordinate> particles = entry.getValue();
            particles.sort(Comparator.comparingInt(
                    QuantumCoordinates.ParticleCoordinate::particleIndex));
            if (particles.size() != molecule.electrons().value()) {
                throw new IOException("walker electron count mismatch");
            }
            result.add(new QuantumCoordinates(particles));
        }
        return result;
    }
}
