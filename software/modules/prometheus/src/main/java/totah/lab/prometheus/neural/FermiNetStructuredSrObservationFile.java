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
import java.util.concurrent.atomic.LongAdder;

/** Ephemeral compact-statistics store for exact Jacobian-free FermiNet SR. */
final class FermiNetStructuredSrObservationFile implements AutoCloseable {

    interface BuildHook {
        BuildHook NONE = row -> { };

        void beforeEvaluation(int row);
    }

    private final Path path;
    private final FileChannel channel;
    private final FermiNetStructuredSrStatistics.Schema schema;
    private final double[] weights;
    private final double[] energies;
    private final long generationNanos;
    private final long writeNanos;
    private final ThreadLocal<ByteBuffer> readBuffers =
            ThreadLocal.withInitial(() ->
                    ByteBuffer.allocateDirect(Double.BYTES)
                            .order(ByteOrder.nativeOrder()));
    private boolean closed;

    private FermiNetStructuredSrObservationFile(
            Path path,
            FileChannel channel,
            FermiNetStructuredSrStatistics.Schema schema,
            double[] weights,
            double[] energies,
            long generationNanos,
            long writeNanos) {
        this.path = path;
        this.channel = channel;
        this.schema = schema;
        this.weights = weights;
        this.energies = energies;
        this.generationNanos = generationNanos;
        this.writeNanos = writeNanos;
    }

    static FermiNetStructuredSrObservationFile buildParallel(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            int parallelism)
            throws IOException {

        return buildParallel(
                state,
                samples,
                parallelism,
                BuildHook.NONE);
    }

    static FermiNetStructuredSrObservationFile buildParallel(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            int parallelism,
            BuildHook hook)
            throws IOException {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(hook, "hook");
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1");
        }

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> retained =
                samples.stream()
                        .filter(sample -> sample.weight() != 0.0)
                        .toList();
        if (retained.isEmpty()) {
            throw new IllegalArgumentException("no non-zero SR samples");
        }

        FermiNetStructuredSrStatistics.Schema schema =
                new FermiNetStructuredSrStatistics.Schema(
                        new FermiNetParameterLayout(
                                state.configuration(),
                                state.molecule()));
        long rowBytes = Math.multiplyExact(
                (long) schema.statisticCount(),
                Double.BYTES);
        long totalBytes = Math.multiplyExact(rowBytes, retained.size());
        int rowByteCount = Math.toIntExact(rowBytes);
        Path path = Files.createTempFile(
                "prometheus-ferminet-structured-sr-", ".bin");
        FileChannel channel = null;
        ExecutorService executor = null;

        try {
            channel = FileChannel.open(
                    path,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            if (totalBytes > 0L) {
                channel.position(totalBytes - 1L);
                channel.write(ByteBuffer.wrap(new byte[]{0}));
            }

            double[] weights = new double[retained.size()];
            double[] energies = new double[retained.size()];
            LongAdder generation = new LongAdder();
            LongAdder writes = new LongAdder();
            executor = Executors.newFixedThreadPool(parallelism);
            ThreadLocal<ByteBuffer> writeBuffers =
                    ThreadLocal.withInitial(() ->
                            ByteBuffer.allocateDirect(rowByteCount)
                                    .order(ByteOrder.nativeOrder()));
            List<Future<Void>> futures = new ArrayList<>(retained.size());
            FileChannel workerChannel = channel;

            for (int row = 0; row < retained.size(); row++) {
                int physicalRow = row;
                var sample = retained.get(row);
                futures.add(executor.submit(() -> {
                    hook.beforeEvaluation(physicalRow);
                    long started = System.nanoTime();
                    FermiNetV1State.StructuredSrEvaluation evaluation =
                            state.structuredSrEvaluation(sample.coordinates());
                    generation.add(System.nanoTime() - started);

                    FermiNetV1State.Evaluation spatial =
                            new FermiNetV1State.Evaluation(
                                    evaluation.sign(),
                                    evaluation.logAbsoluteWavefunction(),
                                    evaluation.logCoordinateGradient(),
                                    evaluation.laplacianOverWavefunction(),
                                    new double[0]);
                    double energy = FermiNetVmc.localEnergy(
                                    state,
                                    sample.coordinates(),
                                    spatial)
                            .totalHartree();
                    if (!Double.isFinite(energy)) {
                        throw new IllegalArgumentException(
                                "non-finite structured SR local energy");
                    }

                    double[] values = evaluation.statistics()
                            .internalValuesForSpoolWrite();
                    if (values.length != schema.statisticCount()) {
                        throw new IllegalArgumentException(
                                "structured SR schema mismatch");
                    }
                    weights[physicalRow] = sample.weight();
                    energies[physicalRow] = energy;

                    started = System.nanoTime();
                    writeRow(
                            workerChannel,
                            writeBuffers.get(),
                            physicalRow,
                            rowBytes,
                            values);
                    writes.add(System.nanoTime() - started);
                    return null;
                }));
            }

            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException(
                            "interrupted structured SR construction",
                            exception);
                } catch (ExecutionException exception) {
                    throw new IOException(
                            "failed structured SR construction",
                            exception.getCause());
                }
            }
            executor.shutdown();
            executor = null;

            return new FermiNetStructuredSrObservationFile(
                    path,
                    channel,
                    schema,
                    weights,
                    energies,
                    generation.sum(),
                    writes.sum());
        } catch (Throwable failure) {
            if (executor != null) {
                executor.shutdownNow();
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException suppressed) {
                    failure.addSuppressed(suppressed);
                }
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException suppressed) {
                failure.addSuppressed(suppressed);
            }
            throw failure;
        }
    }

    private static void writeRow(
            FileChannel channel,
            ByteBuffer bytes,
            int row,
            long rowBytes,
            double[] values)
            throws IOException {
        bytes.clear();
        bytes.asDoubleBuffer().put(values);
        bytes.limit(values.length * Double.BYTES);
        long position = Math.multiplyExact((long) row, rowBytes);
        while (bytes.hasRemaining()) {
            int written = channel.write(bytes, position);
            if (written == 0) {
                Thread.onSpinWait();
            } else {
                position += written;
            }
        }
    }

    double[] readFamily(FermiNetStructuredSrStatistics.Family family)
            throws IOException {
        ensureOpen();
        int familyLength = family.statisticLength();
        double[] result = new double[Math.multiplyExact(sampleCount(), familyLength)];
        int byteCount = Math.multiplyExact(familyLength, Double.BYTES);
        ByteBuffer bytes = readBuffers.get();
        if (bytes.capacity() < byteCount) {
            bytes = ByteBuffer.allocateDirect(byteCount)
                    .order(ByteOrder.nativeOrder());
            readBuffers.set(bytes);
        }
        long rowBytes = Math.multiplyExact(
                (long) schema.statisticCount(),
                Double.BYTES);
        long familyByteOffset = Math.multiplyExact(
                (long) family.statisticOffset(),
                Double.BYTES);
        for (int row = 0; row < sampleCount(); row++) {
            bytes.clear();
            bytes.limit(byteCount);
            long position = Math.addExact(
                    Math.multiplyExact((long) row, rowBytes),
                    familyByteOffset);
            while (bytes.hasRemaining()) {
                int read = channel.read(bytes, position);
                if (read < 0) {
                    throw new IOException("unexpected structured SR EOF");
                }
                if (read == 0) {
                    Thread.onSpinWait();
                } else {
                    position += read;
                }
            }
            bytes.flip();
            DoubleBuffer doubles = bytes.asDoubleBuffer();
            doubles.get(result, row * familyLength, familyLength);
        }
        return result;
    }

    int sampleCount() {
        return weights.length;
    }

    int parameterCount() {
        return schema.layout().parameterCount();
    }

    FermiNetStructuredSrStatistics.Schema schema() {
        return schema;
    }

    double weight(int sample) {
        return weights[sample];
    }

    double localEnergyHartree(int sample) {
        return energies[sample];
    }

    long spoolBytes() throws IOException {
        return channel.size();
    }

    long generationNanos() {
        return generationNanos;
    }

    long writeNanos() {
        return writeNanos;
    }

    long neuralEvaluations() {
        return sampleCount();
    }

    void printTiming(
            long constructionNanos,
            int parallelism)
            throws IOException {
        System.out.printf("""
                FERMINET_STRUCTURED_SR_STATISTICS_TIMING
                  implementation=compact_sufficient_statistics_spool
                  parallelism=%d
                  samples=%d
                  statistics_spool_bytes=%d
                  statistics_construction_wall_ms=%.3f
                  statistics_generation_sum_ms=%.3f
                  statistics_spool_write_sum_ms=%.3f
                  full_n_by_p_jacobian=false
                  derivative_file=false

                """,
                parallelism,
                sampleCount(),
                spoolBytes(),
                constructionNanos / 1.0e6,
                generationNanos / 1.0e6,
                writeNanos / 1.0e6);
    }

    Path path() {
        return path;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("structured SR store closed");
        }
    }

    @Override
    public void close() throws IOException {
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
        readBuffers.remove();
        if (failure != null) {
            throw failure;
        }
    }
}
