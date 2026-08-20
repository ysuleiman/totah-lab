package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;

/** Explicit reusable primitive storage for one batched derivative traversal. */
final class FermiNetBatchedJetWorkspace {
    private static final int CHUNK_DOUBLES = 1 << 20;

    private final List<double[]> chunks = new ArrayList<>();
    private final List<FermiNetBatchedSpatialJet> jets = new ArrayList<>();
    private final Deque<FermiNetBatchedSpatialJet> available = new ArrayDeque<>();
    private int chunkIndex;
    private int chunkOffset;

    FermiNetBatchedSpatialJet acquire(
            double value, double laplacian, int dimensions, int directions) {
        int length = dimensions + directions
                + directions * dimensions + directions;
        FermiNetBatchedSpatialJet jet;
        if (!available.isEmpty()) {
            jet = available.removeFirst();
            jet.prepare(value, laplacian, dimensions, directions);
        } else {
            Slice slice = allocate(length);
            jet = new FermiNetBatchedSpatialJet();
            jets.add(jet);
            jet.initialize(this, slice.values(), slice.offset(), value, laplacian,
                    dimensions, directions);
        }
        return jet;
    }

    void release(FermiNetSpatialJet jet) {
        if (jet instanceof FermiNetBatchedSpatialJet batched
                && batched.releaseFrom(this)) {
            available.addFirst(batched);
        }
    }

    void reset() {
        available.clear();
        for (FermiNetBatchedSpatialJet jet : jets) {
            jet.markReleased();
            available.addLast(jet);
        }
    }

    long retainedPrimitiveBytes() {
        long doubles = 0L;
        for (double[] chunk : chunks) doubles += chunk.length;
        return Math.multiplyExact(doubles, Double.BYTES);
    }

    int retainedJetObjects() {
        return jets.size();
    }

    private Slice allocate(int length) {
        if (length > CHUNK_DOUBLES) {
            throw new IllegalArgumentException("batched jet payload exceeds workspace chunk");
        }
        while (true) {
            if (chunkIndex == chunks.size()) {
                chunks.add(new double[CHUNK_DOUBLES]);
            }
            double[] chunk = chunks.get(chunkIndex);
            if (chunkOffset <= chunk.length - length) {
                int offset = chunkOffset;
                chunkOffset += length;
                java.util.Arrays.fill(chunk, offset, offset + length, 0.0);
                return new Slice(chunk, offset);
            }
            chunkIndex++;
            chunkOffset = 0;
        }
    }

    private record Slice(double[] values, int offset) {}
}
