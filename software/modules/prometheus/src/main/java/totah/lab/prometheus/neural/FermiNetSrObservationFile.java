package totah.lab.prometheus.neural;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

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