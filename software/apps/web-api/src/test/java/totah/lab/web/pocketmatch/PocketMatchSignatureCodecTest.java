package totah.lab.web.pocketmatch;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.pocketmatch.PocketMatchCategories;
import totah.lab.athena.pocket.pocketmatch.PocketMatchSignature;
import totah.lab.web.pocketmatch.PocketMatchSignatureCodec
        .StoredPocketMatchSignature;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PocketMatchSignatureCodecTest {

    @Test
    void roundTripsRecords() throws IOException {
        PocketMatchSignature first = signatureWith(2.5, 7.25, 9.0);
        PocketMatchSignature second = signatureWith();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(buffer);
        PocketMatchSignatureCodec.writeRecord(output, 42L, first);
        PocketMatchSignatureCodec.writeRecord(output, 43L, second);
        output.flush();

        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(buffer.toByteArray())
        );

        StoredPocketMatchSignature one =
                PocketMatchSignatureCodec.readRecord(input);
        assertEquals(42L, one.pocketId());
        assertEquals(3, one.signature().totalDistanceCount());
        assertArrayEquals(
                new double[]{2.5, 7.25, 9.0},
                one.signature().sortedDistances()[0],
                1.0e-6
        );

        StoredPocketMatchSignature two =
                PocketMatchSignatureCodec.readRecord(input);
        assertEquals(43L, two.pocketId());
        assertEquals(0, two.signature().totalDistanceCount());

        assertNull(PocketMatchSignatureCodec.readRecord(input));
    }

    private static PocketMatchSignature signatureWith(
            double... distances
    ) {
        double[][] lists =
                new double[PocketMatchCategories.CATEGORY_COUNT][];
        for (int index = 0; index < lists.length; index++) {
            lists[index] = new double[0];
        }
        lists[0] = distances;
        return PocketMatchSignature.ofPersisted(
                lists,
                distances.length
        );
    }
}
