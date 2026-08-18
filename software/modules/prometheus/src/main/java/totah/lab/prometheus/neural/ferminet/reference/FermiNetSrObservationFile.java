package totah.lab.prometheus.neural.ferminet.reference;

import totah.lab.prometheus.neural.ferminet.runtime.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * Temporary off-heap/on-disk store for one SR iteration's derivative-complete
 * FermiNet observations.
 *
 * <p>Rows are sample-major. Parallel construction assigns every non-zero-weight
 * sample its final compacted physical row before work begins. Workers may
 * therefore finish in arbitrary order while preserving deterministic file
 * contents.
 */
public final class FermiNetSrObservationFile implements AutoCloseable {

    private static final int WRITE_BUFFER_BYTES = 1 << 20;

    private final Path path;
    private final FileChannel channel;
    private final int sampleCount;
    private final int parameterCount;
    private final double[] weights;
    private final double[] localEnergiesHartree;
    private final long derivativeBytes;
    private final long neuralEvaluations;
    private final Timing timing;

    private boolean closed;

    private FermiNetSrObservationFile(
            Path path,
            FileChannel channel,
            int sampleCount,
            int parameterCount,
            double[] weights,
            double[] localEnergiesHartree,
            long derivativeBytes,
            long neuralEvaluations,
            Timing timing) {

        this.path = path;
        this.channel = channel;
        this.sampleCount = sampleCount;
        this.parameterCount = parameterCount;
        this.weights = weights;
        this.localEnergiesHartree = localEnergiesHartree;
        this.derivativeBytes = derivativeBytes;
        this.neuralEvaluations = neuralEvaluations;
        this.timing = timing;
    }

    /**
     * Test/diagnostic hook used by the existing parallel-observation tests.
     */
    public interface ParallelBuildHook {

        ParallelBuildHook NONE = new ParallelBuildHook() {
        };

        default void beforeEvaluation(int row) {
        }

        default void afterWrite(int row) {
        }
    }

    /**
     * Serial observation construction.
     */
    public static FermiNetSrObservationFile build(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples)
            throws IOException {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");

        long totalStarted = System.nanoTime();

        ValidatedInput input =
                validateInput(state, samples);

        long setupStarted = System.nanoTime();

        Path path =
                Files.createTempFile(
                        "prometheus-ferminet-sr-observations-",
                        ".bin");

        FileChannel channel = null;

        try {
            channel =
                    FileChannel.open(
                            path,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING);

            double[] weights =
                    new double[input.nonZeroSamples()];

            double[] energies =
                    new double[input.nonZeroSamples()];

            ByteBuffer writeBuffer =
                    ByteBuffer.allocateDirect(WRITE_BUFFER_BYTES)
                            .order(ByteOrder.nativeOrder());

            long setupNanos =
                    System.nanoTime() - setupStarted;

            long evaluationNanos = 0L;
            long derivativeExtractionNanos = 0L;
            long localEnergyNanos = 0L;
            long validationNanos = 0L;
            long writeNanos = 0L;
            long maxEvaluationNanos = 0L;

            int stored = 0;

            for (var sample : samples) {
                if (sample.weight() == 0.0) {
                    continue;
                }

                long started =
                        System.nanoTime();

                FermiNetV1State.Evaluation evaluation =
                        state.evaluate(sample.coordinates());

                long elapsed =
                        System.nanoTime() - started;

                evaluationNanos += elapsed;

                maxEvaluationNanos =
                        Math.max(
                                maxEvaluationNanos,
                                elapsed);

                started =
                        System.nanoTime();

                double[] derivatives =
                        evaluation.parameterLogDerivatives();

                derivativeExtractionNanos +=
                        System.nanoTime() - started;

                started =
                        System.nanoTime();

                if (derivatives.length != input.parameterCount()) {
                    throw new IllegalArgumentException(
                            "parameter derivative dimension mismatch");
                }

                requireFinite(
                        derivatives,
                        "parameter log derivative");

                validationNanos +=
                        System.nanoTime() - started;

                started =
                        System.nanoTime();

                double energy =
                        FermiNetRuntimeSampling.localEnergy(
                                        state,
                                        sample.coordinates(),
                                        evaluation)
                                .totalHartree();

                localEnergyNanos +=
                        System.nanoTime() - started;

                started =
                        System.nanoTime();

                if (!Double.isFinite(energy)) {
                    throw new IllegalArgumentException(
                            "non-finite local energy");
                }

                validationNanos +=
                        System.nanoTime() - started;

                weights[stored] =
                        sample.weight();

                energies[stored] =
                        energy;

                started =
                        System.nanoTime();

                writeRowSequentially(
                        channel,
                        writeBuffer,
                        derivatives);

                writeNanos +=
                        System.nanoTime() - started;

                stored++;
            }

            long verificationStarted =
                    System.nanoTime();

            verifyStoredFile(
                    channel,
                    stored,
                    input.nonZeroSamples(),
                    input.derivativeBytes());

            long verificationNanos =
                    System.nanoTime() - verificationStarted;

            long totalNanos =
                    System.nanoTime() - totalStarted;

            Timing timing =
                    new Timing(
                            1,
                            input.nonZeroSamples(),
                            totalNanos,
                            setupNanos,
                            evaluationNanos,
                            derivativeExtractionNanos,
                            localEnergyNanos,
                            validationNanos,
                            writeNanos,
                            0L,
                            verificationNanos,
                            maxEvaluationNanos);

            return new FermiNetSrObservationFile(
                    path,
                    channel,
                    input.nonZeroSamples(),
                    input.parameterCount(),
                    weights,
                    energies,
                    input.derivativeBytes(),
                    stored,
                    timing);

        } catch (Throwable failure) {
            cleanupFailure(
                    channel,
                    path);

            throw failure;
        }
    }

    public static FermiNetSrObservationFile buildParallel(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            int parallelism)
            throws IOException {

        return buildParallel(
                state,
                samples,
                parallelism,
                ParallelBuildHook.NONE);
    }

    /**
     * Parallel construction preserving deterministic physical rows.
     */
    public static FermiNetSrObservationFile buildParallel(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            int parallelism,
            ParallelBuildHook hook)
            throws IOException {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(hook, "hook");

        if (parallelism < 1) {
            throw new IllegalArgumentException(
                    "parallelism must be >= 1");
        }

        long totalStarted =
                System.nanoTime();

        ValidatedInput input =
                validateInput(
                        state,
                        samples);

        long setupStarted =
                System.nanoTime();

        Path path =
                Files.createTempFile(
                        "prometheus-ferminet-sr-observations-parallel-",
                        ".bin");

        FileChannel channel = null;
        ExecutorService executor = null;

        try {
            channel =
                    FileChannel.open(
                            path,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING);

            /*
             * Establish final file size before concurrent positional writes.
             */
            if (input.derivativeBytes() > 0L) {
                channel.position(
                        input.derivativeBytes() - 1L);

                channel.write(
                        ByteBuffer.wrap(
                                new byte[]{0}));
            }

            double[] weights =
                    new double[input.nonZeroSamples()];

            double[] energies =
                    new double[input.nonZeroSamples()];

            LongAdder evaluationNanos =
                    new LongAdder();

            LongAdder derivativeExtractionNanos =
                    new LongAdder();

            LongAdder localEnergyNanos =
                    new LongAdder();

            LongAdder validationNanos =
                    new LongAdder();

            LongAdder writeNanos =
                    new LongAdder();

            LongAccumulator maxEvaluationNanos =
                    new LongAccumulator(
                            Long::max,
                            0L);

            executor =
                    Executors.newFixedThreadPool(
                            parallelism);

            List<Future<Void>> futures =
                    new ArrayList<>(
                            input.nonZeroSamples());

            int compactedRow = 0;

            for (var sample : samples) {
                if (sample.weight() == 0.0) {
                    continue;
                }

                final int physicalRow =
                        compactedRow++;

                final FileChannel workerChannel =
                        channel;

                futures.add(
                        executor.submit(() -> {
                            try {
                                hook.beforeEvaluation(
                                        physicalRow);

                                long started =
                                        System.nanoTime();

                                FermiNetV1State.Evaluation evaluation =
                                        state.evaluate(
                                                sample.coordinates());

                                long elapsed =
                                        System.nanoTime() - started;

                                evaluationNanos.add(
                                        elapsed);

                                maxEvaluationNanos.accumulate(
                                        elapsed);

                                started =
                                        System.nanoTime();

                                double[] derivatives =
                                        evaluation.parameterLogDerivatives();

                                derivativeExtractionNanos.add(
                                        System.nanoTime() - started);

                                started =
                                        System.nanoTime();

                                if (derivatives.length
                                        != input.parameterCount()) {

                                    throw new IllegalArgumentException(
                                            "parameter derivative dimension mismatch");
                                }

                                requireFinite(
                                        derivatives,
                                        "parameter log derivative");

                                validationNanos.add(
                                        System.nanoTime() - started);

                                started =
                                        System.nanoTime();

                                double energy =
                                        FermiNetRuntimeSampling.localEnergy(
                                                        state,
                                                        sample.coordinates(),
                                                        evaluation)
                                                .totalHartree();

                                localEnergyNanos.add(
                                        System.nanoTime() - started);

                                started =
                                        System.nanoTime();

                                if (!Double.isFinite(energy)) {
                                    throw new IllegalArgumentException(
                                            "non-finite local energy");
                                }

                                validationNanos.add(
                                        System.nanoTime() - started);

                                weights[physicalRow] =
                                        sample.weight();

                                energies[physicalRow] =
                                        energy;

                                started =
                                        System.nanoTime();

                                writeRowAt(
                                        workerChannel,
                                        physicalRow,
                                        input.parameterCount(),
                                        derivatives);

                                writeNanos.add(
                                        System.nanoTime() - started);

                                hook.afterWrite(
                                        physicalRow);

                                return null;

                            } catch (Throwable failure) {
                                throw new IOException(
                                        "failed to build stored sample "
                                                + physicalRow,
                                        failure);
                            }
                        }));
            }

            long setupNanos =
                    System.nanoTime() - setupStarted;

            long waitStarted =
                    System.nanoTime();

            IOException workerFailure = null;

            for (Future<Void> future : futures) {
                try {
                    future.get();

                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    workerFailure =
                            new IOException(
                                    "interrupted while waiting for SR observation workers",
                                    exception);

                    break;

                } catch (ExecutionException exception) {
                    Throwable cause =
                            exception.getCause();

                    if (cause instanceof IOException ioException) {
                        workerFailure =
                                ioException;
                    } else {
                        workerFailure =
                                new IOException(
                                        "parallel SR observation worker failed",
                                        cause);
                    }

                    break;
                }
            }

            long waitNanos =
                    System.nanoTime() - waitStarted;

            if (workerFailure != null) {
                for (Future<Void> future : futures) {
                    future.cancel(true);
                }

                throw workerFailure;
            }

            executor.shutdown();
            executor = null;

            long verificationStarted =
                    System.nanoTime();

            verifyStoredFile(
                    channel,
                    input.nonZeroSamples(),
                    input.nonZeroSamples(),
                    input.derivativeBytes());

            long verificationNanos =
                    System.nanoTime() - verificationStarted;

            long totalNanos =
                    System.nanoTime() - totalStarted;

            Timing timing =
                    new Timing(
                            parallelism,
                            input.nonZeroSamples(),
                            totalNanos,
                            setupNanos,
                            evaluationNanos.sum(),
                            derivativeExtractionNanos.sum(),
                            localEnergyNanos.sum(),
                            validationNanos.sum(),
                            writeNanos.sum(),
                            waitNanos,
                            verificationNanos,
                            maxEvaluationNanos.get());

            return new FermiNetSrObservationFile(
                    path,
                    channel,
                    input.nonZeroSamples(),
                    input.parameterCount(),
                    weights,
                    energies,
                    input.derivativeBytes(),
                    input.nonZeroSamples(),
                    timing);

        } catch (Throwable failure) {
            if (executor != null) {
                executor.shutdownNow();
            }

            cleanupFailure(
                    channel,
                    path);

            throw failure;
        }
    }

    private static ValidatedInput validateInput(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples) {

        if (samples.isEmpty()) {
            throw new IllegalArgumentException(
                    "empty FermiNet SR sample set");
        }

        int nonZero = 0;

        for (var sample : samples) {
            Objects.requireNonNull(
                    sample,
                    "sample");

            if (!Double.isFinite(sample.weight())
                    || sample.weight() < 0.0) {

                throw new IllegalArgumentException(
                        "invalid FermiNet SR sample weight");
            }

            if (sample.weight() > 0.0) {
                nonZero++;
            }
        }

        if (nonZero == 0) {
            throw new IllegalArgumentException(
                    "zero total FermiNet SR sample weight");
        }

        int parameters =
                state.parameterCount();

        long elements =
                Math.multiplyExact(
                        (long) nonZero,
                        parameters);

        long bytes =
                Math.multiplyExact(
                        elements,
                        Double.BYTES);

        return new ValidatedInput(
                nonZero,
                parameters,
                bytes);
    }

    private static void verifyStoredFile(
            FileChannel channel,
            int stored,
            int expectedSamples,
            long expectedBytes)
            throws IOException {

        if (stored != expectedSamples) {
            throw new IllegalStateException(
                    "stored sample count mismatch: "
                            + stored
                            + " expected="
                            + expectedSamples);
        }

        long actualBytes =
                channel.size();

        if (actualBytes != expectedBytes) {
            throw new IllegalStateException(
                    "observation file size mismatch: "
                            + actualBytes
                            + " expected="
                            + expectedBytes);
        }
    }

    public int sampleCount() {
        return sampleCount;
    }

    public int parameterCount() {
        return parameterCount;
    }

    public double weight(int sampleIndex) {
        checkSample(sampleIndex);
        return weights[sampleIndex];
    }

    public double localEnergyHartree(int sampleIndex) {
        checkSample(sampleIndex);
        return localEnergiesHartree[sampleIndex];
    }

    public long derivativeBytes() {
        return derivativeBytes;
    }

    public long neuralEvaluations() {
        return neuralEvaluations;
    }

    public Path path() {
        return path;
    }

    public Timing timing() {
        return timing;
    }

    /**
     * Prints aggregate timing for this build.
     *
     * <p>Worker timings are SUMMED across workers. They are therefore not wall
     * times when parallelism is greater than one.
     */
    public void printTiming() {

        System.out.printf("""
                SR_OBSERVATION_TIMING
                  parallelism=%d
                  samples=%d
                  derivative_bytes=%d
                  total_wall_ms=%.3f
                  setup_wall_ms=%.3f
                  worker_evaluation_sum_ms=%.3f
                  derivative_extraction_sum_ms=%.3f
                  local_energy_sum_ms=%.3f
                  validation_sum_ms=%.3f
                  file_write_sum_ms=%.3f
                  completion_wait_wall_ms=%.3f
                  verification_wall_ms=%.3f
                  mean_evaluation_ms=%.3f
                  max_evaluation_ms=%.3f
                  worker_compute_sum_ms=%.3f
                  worker_compute_to_wall_ratio=%.3f

                """,
                timing.parallelism(),
                timing.samples(),
                derivativeBytes,
                millis(timing.totalNanos()),
                millis(timing.setupNanos()),
                millis(timing.evaluationNanos()),
                millis(timing.derivativeExtractionNanos()),
                millis(timing.localEnergyNanos()),
                millis(timing.validationNanos()),
                millis(timing.writeNanos()),
                millis(timing.waitNanos()),
                millis(timing.verificationNanos()),
                timing.samples() == 0
                        ? 0.0
                        : millis(timing.evaluationNanos())
                        / timing.samples(),
                millis(timing.maxEvaluationNanos()),
                millis(timing.workerComputeNanos()),
                timing.totalNanos() == 0L
                        ? 0.0
                        : (double) timing.workerComputeNanos()
                        / timing.totalNanos());
    }

    public void readParameterBlock(
            int parameterStart,
            int length,
            double[] destination)
            throws IOException {

        ensureOpen();

        if (parameterStart < 0
                || length < 1
                || parameterStart + length > parameterCount) {

            throw new IllegalArgumentException(
                    "invalid parameter block");
        }

        int required =
                Math.multiplyExact(
                        sampleCount,
                        length);

        if (destination.length < required) {
            throw new IllegalArgumentException(
                    "destination too small");
        }

        int blockBytes =
                Math.multiplyExact(
                        length,
                        Double.BYTES);

        ByteBuffer bytes =
                ByteBuffer.allocateDirect(blockBytes)
                        .order(ByteOrder.nativeOrder());

        for (int sample = 0;
             sample < sampleCount;
             sample++) {

            bytes.clear();

            long elementOffset =
                    Math.addExact(
                            Math.multiplyExact(
                                    (long) sample,
                                    parameterCount),
                            parameterStart);

            long byteOffset =
                    Math.multiplyExact(
                            elementOffset,
                            Double.BYTES);

            readFully(
                    channel,
                    bytes,
                    byteOffset);

            bytes.flip();

            DoubleBuffer doubles =
                    bytes.asDoubleBuffer();

            doubles.get(
                    destination,
                    sample * length,
                    length);
        }
    }

    private static void writeRowSequentially(
            FileChannel channel,
            ByteBuffer bytes,
            double[] row)
            throws IOException {

        int at = 0;

        while (at < row.length) {
            bytes.clear();

            DoubleBuffer doubles =
                    bytes.asDoubleBuffer();

            int count =
                    Math.min(
                            doubles.capacity(),
                            row.length - at);

            doubles.put(
                    row,
                    at,
                    count);

            bytes.limit(
                    count * Double.BYTES);

            bytes.position(0);

            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }

            at += count;
        }
    }

    /**
     * Positional row write used by parallel workers.
     */
    private static void writeRowAt(
            FileChannel channel,
            int rowIndex,
            int parameterCount,
            double[] row)
            throws IOException {

        ByteBuffer bytes =
                ByteBuffer.allocateDirect(WRITE_BUFFER_BYTES)
                        .order(ByteOrder.nativeOrder());

        long rowElementOffset =
                Math.multiplyExact(
                        (long) rowIndex,
                        parameterCount);

        long position =
                Math.multiplyExact(
                        rowElementOffset,
                        Double.BYTES);

        int at = 0;

        while (at < row.length) {
            bytes.clear();

            DoubleBuffer doubles =
                    bytes.asDoubleBuffer();

            int count =
                    Math.min(
                            doubles.capacity(),
                            row.length - at);

            doubles.put(
                    row,
                    at,
                    count);

            bytes.limit(
                    count * Double.BYTES);

            bytes.position(0);

            while (bytes.hasRemaining()) {
                int written =
                        channel.write(
                                bytes,
                                position);

                if (written < 0) {
                    throw new IOException(
                            "failed positional write for SR observation row "
                                    + rowIndex);
                }

                if (written == 0) {
                    Thread.onSpinWait();
                    continue;
                }

                position += written;
            }

            at += count;
        }
    }

    private static void readFully(
            FileChannel channel,
            ByteBuffer buffer,
            long position)
            throws IOException {

        long at = position;

        while (buffer.hasRemaining()) {
            int read =
                    channel.read(
                            buffer,
                            at);

            if (read < 0) {
                throw new IOException(
                        "unexpected EOF in SR observation file");
            }

            at += read;
        }
    }

    private void checkSample(int sampleIndex) {
        if (sampleIndex < 0
                || sampleIndex >= sampleCount) {

            throw new IndexOutOfBoundsException(
                    "sample index: "
                            + sampleIndex);
        }
    }

    private void ensureOpen() {
        if (closed || !channel.isOpen()) {
            throw new IllegalStateException(
                    "SR observation file is closed");
        }
    }

    private static void requireFinite(
            double[] values,
            String label) {

        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "non-finite "
                                + label);
            }
        }
    }

    private static void cleanupFailure(
            FileChannel channel,
            Path path) {

        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static double millis(long nanos) {
        return nanos / 1.0e6;
    }

    @Override
    public void close()
            throws IOException {

        if (closed) {
            return;
        }

        closed = true;

        IOException failure = null;

        try {
            channel.close();
        } catch (IOException exception) {
            failure = exception;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private record ValidatedInput(
            int nonZeroSamples,
            int parameterCount,
            long derivativeBytes) {
    }

    public record Timing(
            int parallelism,
            int samples,
            long totalNanos,
            long setupNanos,
            long evaluationNanos,
            long derivativeExtractionNanos,
            long localEnergyNanos,
            long validationNanos,
            long writeNanos,
            long waitNanos,
            long verificationNanos,
            long maxEvaluationNanos) {

        public long workerComputeNanos() {
            return evaluationNanos
                    + derivativeExtractionNanos
                    + localEnergyNanos
                    + validationNanos;
        }
    }
}
