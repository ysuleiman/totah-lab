package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.pretraining.GaussianHartreeFockOrbitalTargetTest;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.neural.ferminet.reference.FermiNetSampleSpaceSrSolver;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

final class FermiNetSrObservationFileParallelTest {

    private static final int BLOCK_SIZE = 128;
    private static final double DAMPING = 0.2;

    @Test
    void serialAndParallelObservationsAreBitIdentical() throws Exception {
        Fixture fixture = fixture();

        try (FermiNetSrObservationFile serial =
                     FermiNetSrObservationFile.build(fixture.state(), fixture.samples());
             FermiNetSrObservationFile parallel =
                     FermiNetSrObservationFile.buildParallel(
                             fixture.state(), fixture.samples(), 3)) {

            assertEquals(serial.sampleCount(), parallel.sampleCount());
            assertEquals(serial.parameterCount(), parallel.parameterCount());
            assertEquals(serial.derivativeBytes(), parallel.derivativeBytes());
            assertEquals(serial.neuralEvaluations(), parallel.neuralEvaluations());
            assertEquals(-1L, Files.mismatch(serial.path(), parallel.path()));

            long weightMismatches = 0L;
            long energyMismatches = 0L;
            for (int sample = 0; sample < serial.sampleCount(); sample++) {
                if (bits(serial.weight(sample)) != bits(parallel.weight(sample))) {
                    weightMismatches++;
                }
                if (bits(serial.localEnergyHartree(sample))
                        != bits(parallel.localEnergyHartree(sample))) {
                    energyMismatches++;
                }
            }

            long derivativeMismatches = derivativeBitMismatches(serial, parallel);
            double[] serialGram = gram(serial, DAMPING);
            double[] parallelGram = gram(parallel, DAMPING);
            long gramMismatches = bitMismatches(serialGram, parallelGram);

            FermiNetSampleSpaceSrSolver solver = new FermiNetSampleSpaceSrSolver();
            double[] serialDelta = solver.solve(serial, DAMPING, BLOCK_SIZE).delta();
            double[] parallelDelta = solver.solve(parallel, DAMPING, BLOCK_SIZE).delta();
            long updateMismatches = bitMismatches(serialDelta, parallelDelta);
            double updateMaxError = maxAbsoluteError(serialDelta, parallelDelta);
            double updateMaxRelativeError = maxRelativeError(serialDelta, parallelDelta);

            System.out.printf("""
                    FERMINET_SR_OBSERVATION_PARALLEL_PARITY
                      samples=%d
                      parameters=%d
                      weight_bit_mismatches=%d
                      local_energy_bit_mismatches=%d
                      derivative_bit_mismatches=%d
                      gram_bit_mismatches=%d
                      sr_update_bit_mismatches=%d
                      sr_update_max_absolute_error=%.17g
                      sr_update_max_relative_error=%.17g

                    """,
                    serial.sampleCount(),
                    serial.parameterCount(),
                    weightMismatches,
                    energyMismatches,
                    derivativeMismatches,
                    gramMismatches,
                    updateMismatches,
                    updateMaxError,
                    updateMaxRelativeError);

            assertEquals(0L, weightMismatches);
            assertEquals(0L, energyMismatches);
            assertEquals(0L, derivativeMismatches);
            assertEquals(0L, gramMismatches);
            assertEquals(0L, updateMismatches);
            assertEquals(0.0, updateMaxError);
            assertEquals(0.0, updateMaxRelativeError);
        }
    }

    @Test
    void outOfOrderCompletionPreservesCompactedPhysicalRows() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch rowOneWritten = new CountDownLatch(1);
        List<Integer> completionOrder = Collections.synchronizedList(new ArrayList<>());

        FermiNetSrObservationFile.ParallelBuildHook hook =
                new FermiNetSrObservationFile.ParallelBuildHook() {
                    @Override
                    public void beforeEvaluation(int row) {
                        if (row == 0) {
                            try {
                                if (!rowOneWritten.await(30, TimeUnit.SECONDS)) {
                                    throw new AssertionError("row one did not finish first");
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(exception);
                            }
                        }
                    }

                    @Override
                    public void afterWrite(int row) {
                        completionOrder.add(row);
                        if (row == 1) {
                            rowOneWritten.countDown();
                        }
                    }
                };

        try (FermiNetSrObservationFile serial =
                     FermiNetSrObservationFile.build(fixture.state(), fixture.samples());
             FermiNetSrObservationFile parallel =
                     FermiNetSrObservationFile.buildParallel(
                             fixture.state(), fixture.samples(), 2, hook)) {

            assertFalse(completionOrder.isEmpty());
            assertEquals(1, completionOrder.getFirst());
            assertTrue(completionOrder.indexOf(1) < completionOrder.indexOf(0));
            assertEquals(-1L, Files.mismatch(serial.path(), parallel.path()));
            assertEquals(0L, derivativeBitMismatches(serial, parallel));

            double[] expectedWeights = {1.0, 2.0, 0.75, 1.25};
            double[] actualWeights = new double[parallel.sampleCount()];
            for (int row = 0; row < parallel.sampleCount(); row++) {
                actualWeights[row] = parallel.weight(row);
            }
            assertArrayEquals(expectedWeights, actualWeights);
        }
    }

    @Test
    void workerFailureDeletesPartialObservationFile() throws Exception {
        Fixture fixture = fixture();
        Set<Path> before = parallelTemporaryFiles();

        IOException failure = assertThrows(
                IOException.class,
                () -> FermiNetSrObservationFile.buildParallel(
                        fixture.state(),
                        fixture.samples(),
                        2,
                        new FermiNetSrObservationFile.ParallelBuildHook() {
                            @Override
                            public void beforeEvaluation(int row) {
                                if (row == 1) {
                                    throw new IllegalStateException("deliberate worker failure");
                                }
                            }
                        }));

        assertTrue(failure.getMessage().contains("stored sample 1"));
        assertEquals(before, parallelTemporaryFiles());
    }

    private static Set<Path> parallelTemporaryFiles() throws IOException {
        Path temporaryDirectory = Path.of(System.getProperty("java.io.tmpdir"));
        try (var paths = Files.list(temporaryDirectory)) {
            Set<Path> result = new HashSet<>();
            paths.filter(path -> path.getFileName().toString().startsWith(
                            "prometheus-ferminet-sr-observations-parallel-"))
                    .map(Path::toAbsolutePath)
                    .forEach(result::add);
            return result;
        }
    }

    private static long derivativeBitMismatches(
            FermiNetSrObservationFile left,
            FermiNetSrObservationFile right) throws IOException {
        long mismatches = 0L;
        int samples = left.sampleCount();
        for (int start = 0; start < left.parameterCount(); start += BLOCK_SIZE) {
            int length = Math.min(BLOCK_SIZE, left.parameterCount() - start);
            double[] leftBlock = new double[samples * length];
            double[] rightBlock = new double[samples * length];
            left.readParameterBlock(start, length, leftBlock);
            right.readParameterBlock(start, length, rightBlock);
            mismatches += bitMismatches(leftBlock, rightBlock);
        }
        return mismatches;
    }

    private static double[] gram(FermiNetSrObservationFile observations, double damping)
            throws IOException {
        int samples = observations.sampleCount();
        double weightSum = 0.0;
        for (int sample = 0; sample < samples; sample++) {
            weightSum += observations.weight(sample);
        }
        double[] normalized = new double[samples];
        double[] sqrtWeight = new double[samples];
        for (int sample = 0; sample < samples; sample++) {
            normalized[sample] = observations.weight(sample) / weightSum;
            sqrtWeight[sample] = Math.sqrt(normalized[sample]);
        }
        double[] result = new double[samples * samples];
        double[] centeredWeighted = new double[samples];
        for (int start = 0; start < observations.parameterCount(); start += BLOCK_SIZE) {
            int length = Math.min(BLOCK_SIZE, observations.parameterCount() - start);
            double[] block = new double[samples * length];
            observations.readParameterBlock(start, length, block);
            for (int local = 0; local < length; local++) {
                double mean = 0.0;
                for (int sample = 0; sample < samples; sample++) {
                    mean += normalized[sample] * block[sample * length + local];
                }
                for (int sample = 0; sample < samples; sample++) {
                    centeredWeighted[sample] = sqrtWeight[sample]
                            * (block[sample * length + local] - mean);
                }
                for (int row = 0; row < samples; row++) {
                    int offset = row * samples;
                    for (int column = 0; column <= row; column++) {
                        result[offset + column] += centeredWeighted[row]
                                * centeredWeighted[column];
                    }
                }
            }
        }
        for (int row = 0; row < samples; row++) {
            result[row * samples + row] += damping;
            for (int column = 0; column < row; column++) {
                result[column * samples + row] = result[row * samples + column];
            }
        }
        return result;
    }

    private static long bitMismatches(double[] left, double[] right) {
        long result = 0L;
        for (int i = 0; i < left.length; i++) {
            if (bits(left[i]) != bits(right[i])) result++;
        }
        return result;
    }

    private static long bits(double value) {
        return Double.doubleToLongBits(value);
    }

    private static double maxAbsoluteError(double[] left, double[] right) {
        double result = 0.0;
        for (int i = 0; i < left.length; i++) {
            result = Math.max(result, Math.abs(left[i] - right[i]));
        }
        return result;
    }

    private static double maxRelativeError(double[] left, double[] right) {
        double result = 0.0;
        for (int i = 0; i < left.length; i++) {
            double scale = Math.max(Math.max(Math.abs(left[i]), Math.abs(right[i])), 1e-300);
            result = Math.max(result, Math.abs(left[i] - right[i]) / scale);
        }
        return result;
    }

    private static Fixture fixture() {
        var molecule = GaussianHartreeFockOrbitalTargetTest.water();
        var configuration = FermiNetV1Configuration.testFixture();
        var state = new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.initialize(
                        new FermiNetParameterLayout(configuration, molecule),
                        44017L));
        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples = List.of(
                sample(1.0, 0.000),
                sample(0.0, 0.009),
                sample(2.0, 0.015),
                sample(0.75, -0.021),
                sample(0.0, -0.028),
                sample(1.25, 0.033));
        return new Fixture(state, samples);
    }

    private static FermiNetMatrixFreeSrOptimizer.WeightedSample sample(
            double weight, double shift) {
        double[][] xyz = {
                {.18, .11, .27}, {-.31, .42, -.16}, {.57, -.28, .33},
                {-.63, -.37, .21}, {.24, .71, -.45}, {-.22, -.15, -.38},
                {.36, -.54, .19}, {-.48, .26, .51}, {.69, .18, -.24},
                {-.12, .61, .37}
        };
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
        for (int i = 0; i < xyz.length; i++) {
            double signed = i % 2 == 0 ? shift : -shift;
            particles.add(new QuantumCoordinates.ParticleCoordinate(
                    i,
                    xyz[i][0] + signed,
                    xyz[i][1] - 0.5 * signed,
                    xyz[i][2] + 0.25 * signed,
                    i < 5 ? SpinProjection.ALPHA : SpinProjection.BETA));
        }
        return new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                weight, new QuantumCoordinates(particles));
    }

    private record Fixture(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples) {
    }
}
