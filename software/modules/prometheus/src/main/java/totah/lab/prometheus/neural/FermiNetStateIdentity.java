package totah.lab.prometheus.neural;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Exact identity of a FermiNet state for binding reusable computed evidence. */
final class FermiNetStateIdentity {

    private FermiNetStateIdentity() {}

    static String of(FermiNetV1State state) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, FermiNetV1Configuration.REPRESENTATION_ID);
            update(digest, state.molecule().scientificIdentity());
            update(digest, state.configuration().toString());
            updateLong(digest, state.parameterCount());
            for (int i = 0; i < state.parameterCount(); i++) {
                updateLong(digest, Double.doubleToRawLongBits(state.parameter(i)));
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateLong(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateLong(MessageDigest digest, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }
}
