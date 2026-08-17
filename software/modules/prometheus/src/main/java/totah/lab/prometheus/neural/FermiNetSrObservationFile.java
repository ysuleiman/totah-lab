package totah.lab.prometheus.neural;

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
import java.util.concurrent.TimeUnit;

/**
 * Temporary off-heap/on-disk store for one SR iteration's derivative-complete
 * FermiNet observations.
 *
 * <p>Rows are written sample-major because {@link FermiNetV1State#evaluate}
 * naturally produces one complete parameter-derivative vector per sample.
 * This makes construction sequential instead of scattering every sample across
 * the entire file.
 *
 * <p>The sample-space solver later reads rectangular parameter blocks across
 * all samples. That preserves O(N*P) derivative I/O per sweep without keeping
 * the N*P derivative matrix on the Java heap.
 */
public final class FermiNetSrObservationFile implements AutoCloseable {

    private static final int WRITE_BUFFER_BYTES = 1 << 20;
    private static final ParallelBuildHook NO_PARALLEL_BUILD_HOOK =
            new ParallelBuildHook() {};

    private final Path path;
    private final FileChannel channel;
    private final int sampleCount;
    private final int parameterCount;
    private final double[] weights;
    private final double[] localEnergiesHartree;
    private final long derivativeBytes;
    private final long neuralEvaluations;

    private boolean closed;

    private FermiNetSrObservationFile(
            Path path,
            FileChannel channel,
            int sampleCount,
            int parameterCount,
            double[] weights,
            double[] localEnergiesHartree,
            long derivativeBytes,
            long neuralEvaluations) {

        this.path = path;
        this.channel = channel;
        this.sampleCount = sampleCount;
        this.parameterCount = parameterCount;
        this.weights = weights;
        this.localEnergiesHartree = localEnergiesHartree;
        this.derivativeBytes = derivativeBytes;
        this.neuralEvaluations = neuralEvaluations;
    }

    /**
     * Evaluates each non-zero-weight sample exactly once and writes its complete
     * parameter log-derivative row sequentially to a temporary file.
     */
    public static FermiNetSrObservationFile build(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples)
            throws IOException {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");

        if (samples.isEmpty()) {
            throw new IllegalArgumentException("empty FermiNet SR sample set");
        }

        int nonZero = 0;

        for (var sample : samples) {
            Objects.requireNonNull(sample, "sample");

            if (!Double.isFinite(sample.weight()) || sample.weight() < 0.0) {
                throw new IllegalArgumentException("invalid FermiNet SR sample weight");
            }

            if (sample.weight() > 0.0) {
                nonZero++;
            }
        }

        if (nonZero == 0) {
            throw new IllegalArgumentException("zero total FermiNet SR sample weight");
        }

        int parameters = state.parameterCount();

        long elements =
                Math.multiplyExact(
                        (long) nonZero,
                        parameters);

        long bytes =
                Math.multiplyExact(
                        elements,
                        Double.BYTES);

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

            double[] weights = new double[nonZero];
            double[] energies = new double[nonZero];

            ByteBuffer writeBytes =
                    ByteBuffer.allocateDirect(WRITE_BUFFER_BYTES)
                            .order(ByteOrder.nativeOrder());

            int stored = 0;

            for (var sample : samples) {
                if (sample.weight() == 0.0) {
                    continue;
                }

                FermiNetV1State.Evaluation evaluation =
                        state.evaluate(sample.coordinates());

                double[] derivatives =
                        evaluation.parameterLogDerivatives();

                if (derivatives.length != parameters) {
                    throw new IllegalArgumentException(
                            "parameter derivative dimension mismatch");
                }

                requireFinite(
                        derivatives,
                        "parameter log derivative");

                double energy =
                        FermiNetVmc.localEnergy(
                                        state,
                                        sample.coordinates(),
                                        evaluation)
                                .totalHartree();

                if (!Double.isFinite(energy)) {
                    throw new IllegalArgumentException(
                            "non-finite local energy");
                }

                weights[stored] = sample.weight();
                energies[stored] = energy;

                writeRowSequentially(
                        channel,
                        writeBytes,
                        derivatives);

                stored++;
            }

            if (stored != nonZero) {
                throw new IllegalStateException(
                        "stored sample count mismatch");
            }

            if (channel.size() != bytes) {
                throw new IllegalStateException(
                        "observation file size mismatch: "
                                + channel.size()
                                + " expected="
                                + bytes);
            }

            return new FermiNetSrObservationFile(
                    path,
                    channel,
                    nonZero,
                    parameters,
                    weights,
                    energies,
                    bytes,
                    stored);

        } catch (Throwable failure) {
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

            throw failure;
        }
    }

    /**
     * Experimental parallel construction of derivative-complete SR observations.
     *
     * <p>This does not replace {@link #build}. Samples are still compacted in the
     * exact same non-zero-weight order as the serial implementation. Each worker
     * evaluates one stored sample independently and writes its derivative row to
     * that sample's predetermined byte range using positional FileChannel writes.
     */
    public static FermiNetSrObservationFile buildParallel(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples)
            throws IOException {

        return buildParallel(
                state,
                samples,
                Runtime.getRuntime().availableProcessors());
    }

    /**
     * Experimental parallel construction with explicit worker count.
     *
     * <p>The FermiNet state is shared because evaluation scratch state is local to
     * each call. No N x P derivative matrix is retained in heap. Each active task
     * owns only its evaluation result/derivative vector and each executor thread
     * reuses one direct row-write buffer.
     */
    public static FermiNetSrObservationFile buildParallel(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            int parallelism)
            throws IOException {

        return buildParallel(
                state,
                samples,
                parallelism,
                NO_PARALLEL_BUILD_HOOK);
    }

    static FermiNetSrObservationFile buildParallel(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            int parallelism,
            ParallelBuildHook hook)
            throws IOException {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(hook, "hook");

        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be positive");
        }

        if (samples.isEmpty()) {
            throw new IllegalArgumentException("empty FermiNet SR sample set");
        }

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> storedSamples =
                new ArrayList<>();

        for (var sample : samples) {
            Objects.requireNonNull(sample, "sample");

            if (!Double.isFinite(sample.weight()) || sample.weight() < 0.0) {
                throw new IllegalArgumentException("invalid FermiNet SR sample weight");
            }

            if (sample.weight() > 0.0) {
                storedSamples.add(sample);
            }
        }

        if (storedSamples.isEmpty()) {
            throw new IllegalArgumentException("zero total FermiNet SR sample weight");
        }

        int nonZero = storedSamples.size();
        int parameters = state.parameterCount();

        long elements =
                Math.multiplyExact(
                        (long) nonZero,
                        parameters);

        long bytes =
                Math.multiplyExact(
                        elements,
                        Double.BYTES);

        Path path =
                Files.createTempFile(
                        "prometheus-ferminet-sr-observations-parallel-",
                        ".bin");

        FileChannel channel = null;
        ExecutorService executor = null;
        List<Future<?>> futures = null;

        try {
            channel =
                    FileChannel.open(
                            path,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING);

            // Establish the complete file extent before concurrent positional writes.
            preallocate(channel, bytes);

            double[] weights = new double[nonZero];
            double[] energies = new double[nonZero];

            int workers =
                    Math.min(
                            parallelism,
                            nonZero);

            executor =
                    Executors.newFixedThreadPool(workers);

            FileChannel target = channel;

            ThreadLocal<ByteBuffer> writeBuffers =
                    ThreadLocal.withInitial(
                            () -> ByteBuffer
                                    .allocateDirect(WRITE_BUFFER_BYTES)
                                    .order(ByteOrder.nativeOrder()));

            futures =
                    new ArrayList<>(nonZero);

            for (int storedIndex = 0;
                 storedIndex < nonZero;
                 storedIndex++) {

                final int row = storedIndex;
                final var sample = storedSamples.get(storedIndex);

                futures.add(
                        executor.submit(
                                () -> {
                                    hook.beforeEvaluation(row);

                                    FermiNetV1State.Evaluation evaluation =
                                            state.evaluate(sample.coordinates());

                                    double[] derivatives =
                                            evaluation.parameterLogDerivatives();

                                    if (derivatives.length != parameters) {
                                        throw new IllegalArgumentException(
                                                "parameter derivative dimension mismatch");
                                    }

                                    requireFinite(
                                            derivatives,
                                            "parameter log derivative");

                                    double energy =
                                            FermiNetVmc.localEnergy(
                                                            state,
                                                            sample.coordinates(),
                                                            evaluation)
                                                    .totalHartree();

                                    if (!Double.isFinite(energy)) {
                                        throw new IllegalArgumentException(
                                                "non-finite local energy");
                                    }

                                    weights[row] = sample.weight();
                                    energies[row] = energy;

                                    long rowOffsetBytes =
                                            Math.multiplyExact(
                                                    Math.multiplyExact(
                                                            (long) row,
                                                            parameters),
                                                    Double.BYTES);

                                    writeRowAt(
                                            target,
                                            writeBuffers.get(),
                                            derivatives,
                                            rowOffsetBytes);

                                    hook.afterWrite(row);

                                    return null;
                                }));
            }

            Throwable firstFailure = null;
            int firstFailureRow = -1;

            for (int row = 0; row < futures.size(); row++) {
                try {
                    futures.get(row).get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    if (firstFailure == null) {
                        firstFailure = exception;
                        firstFailureRow = row;
                    }

                    break;
                } catch (ExecutionException exception) {
                    if (firstFailure == null) {
                        firstFailure = exception.getCause();
                        firstFailureRow = row;
                    }

                    break;
                }
            }

            if (firstFailure != null) {
                throw workerFailure(
                        firstFailureRow,
                        firstFailure);
            }

            executor.shutdown();
            executor = null;

            if (channel.size() != bytes) {
                throw new IllegalStateException(
                        "observation file size mismatch: "
                                + channel.size()
                                + " expected="
                                + bytes);
            }

            return new FermiNetSrObservationFile(
                    path,
                    channel,
                    nonZero,
                    parameters,
                    weights,
                    energies,
                    bytes,
                    nonZero);

        } catch (Throwable failure) {
            if (futures != null) {
                for (Future<?> future : futures) {
                    future.cancel(true);
                }
            }

            if (executor != null) {
                executor.shutdownNow();
                try {
                    executor.awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failure.addSuppressed(exception);
                }
            }

            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException exception) {
                    failure.addSuppressed(exception);
                }
            }

            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }

            if (failure instanceof IOException io) {
                throw io;
            }

            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }

            if (failure instanceof Error error) {
                throw error;
            }

            throw new IOException(
                    "parallel FermiNet SR observation construction failed",
                    failure);
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

    /**
     * Reads {@code length} consecutive parameters beginning at
     * {@code parameterStart} for every stored sample.
     *
     * <p>The destination layout is sample-major within the requested block:
     *
     * <pre>
     * destination[sample * length + localParameter]
     * </pre>
     *
     * The destination must have at least {@code sampleCount * length} entries.
     */
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

        for (int sample = 0; sample < sampleCount; sample++) {
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

    private static void preallocate(
            FileChannel channel,
            long bytes)
            throws IOException {

        if (bytes < 1L) {
            return;
        }

        ByteBuffer oneByte = ByteBuffer.allocate(1);
        oneByte.put((byte) 0);
        oneByte.flip();

        while (oneByte.hasRemaining()) {
            channel.write(oneByte, bytes - 1L);
        }
    }

    private static void writeRowAt(
            FileChannel channel,
            ByteBuffer bytes,
            double[] row,
            long rowOffsetBytes)
            throws IOException {

        int at = 0;
        long position = rowOffsetBytes;

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

            bytes.limit(count * Double.BYTES);
            bytes.position(0);

            while (bytes.hasRemaining()) {
                int written =
                        channel.write(
                                bytes,
                                position);

                if (written < 0) {
                    throw new IOException(
                            "unexpected EOF writing SR observation file");
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

    private static IOException workerFailure(
            int storedSampleIndex,
            Throwable cause) {

        return new IOException(
                "parallel FermiNet SR observation worker failed at stored sample "
                        + storedSampleIndex,
                cause);
    }

    interface ParallelBuildHook {

        default void beforeEvaluation(int storedSampleIndex) {
        }

        default void afterWrite(int storedSampleIndex) {
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
        if (sampleIndex < 0 || sampleIndex >= sampleCount) {
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
}
