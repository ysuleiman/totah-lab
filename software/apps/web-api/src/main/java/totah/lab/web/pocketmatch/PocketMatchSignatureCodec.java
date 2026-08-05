package totah.lab.web.pocketmatch;

import totah.lab.athena.pocket.pocketmatch.PocketMatchCategories;
import totah.lab.athena.pocket.pocketmatch.PocketMatchSignature;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;

/**
 * Binary serialization for PocketMatch signatures.
 *
 * <p>Record layout (all values big-endian):</p>
 *
 * <pre>
 * pocketId            long
 * totalDistanceCount  int
 * 90 times:
 *   count             int
 *   distances         count x float
 * </pre>
 *
 * <p>Distances are stored as floats: at roughly 7 significant decimal
 * digits, the representation error (micro-angstroms) is negligible
 * against the 0.50 angstrom matching tolerance, and the payload is half
 * the size of raw doubles.</p>
 */
public final class PocketMatchSignatureCodec {

    private PocketMatchSignatureCodec() {
    }

    public record StoredPocketMatchSignature(
            long pocketId,
            PocketMatchSignature signature
    ) {
    }

    public static void writeRecord(
            DataOutput output,
            long pocketId,
            PocketMatchSignature signature
    ) throws IOException {
        output.writeLong(pocketId);
        output.writeInt(signature.totalDistanceCount());
        for (double[] distances : signature.sortedDistances()) {
            output.writeInt(distances.length);
            for (double distance : distances) {
                output.writeFloat((float) distance);
            }
        }
    }

    /**
     * Reads one record, or returns {@code null} at a clean end of
     * stream.
     */
    public static StoredPocketMatchSignature readRecord(
            DataInput input
    ) throws IOException {
        final long pocketId;
        try {
            pocketId = input.readLong();
        } catch (EOFException endOfStream) {
            return null;
        }

        int totalDistanceCount = input.readInt();
        double[][] lists =
                new double[PocketMatchCategories.CATEGORY_COUNT][];
        int total = 0;
        for (int index = 0; index < lists.length; index++) {
            int count = input.readInt();
            double[] distances = new double[count];
            for (int position = 0; position < count; position++) {
                distances[position] = input.readFloat();
            }
            lists[index] = distances;
            total += count;
        }
        if (total != totalDistanceCount) {
            throw new IOException(
                    "corrupt signature record for pocket " + pocketId
                            + ": declared " + totalDistanceCount
                            + " distances but stored " + total
            );
        }
        return new StoredPocketMatchSignature(
                pocketId,
                PocketMatchSignature.ofPersisted(
                        lists,
                        totalDistanceCount
                )
        );
    }
}
