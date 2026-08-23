package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Immutable canonical derivative-runtime configuration. */
public record FermiNetDerivativeConfiguration(
        FermiNetDerivativeEngineType engineType,
        int sampleParallelism) {

    public FermiNetDerivativeConfiguration {
        Objects.requireNonNull(engineType, "engineType");
        if (sampleParallelism < 1) {
            throw new IllegalArgumentException("invalid derivative sample parallelism");
        }
    }

    public FermiNetDerivativeConfiguration(
            FermiNetDerivativeEngineType engineType) {
        this(engineType, 1);
    }

    public static FermiNetDerivativeConfiguration referenceJet() {
        return new FermiNetDerivativeConfiguration(
                FermiNetDerivativeEngineType.REFERENCE_JET);
    }

    public static FermiNetDerivativeConfiguration batchedForward() {
        return new FermiNetDerivativeConfiguration(
                FermiNetDerivativeEngineType.BATCHED_FORWARD);
    }

    public static FermiNetDerivativeConfiguration batchedForward(
            int sampleParallelism) {
        return new FermiNetDerivativeConfiguration(
                FermiNetDerivativeEngineType.BATCHED_FORWARD,
                sampleParallelism);
    }

    /** Identity of every derivative-runtime value that may affect result bits. */
    public String scientificIdentity() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("ferminet-derivative-runtime-v1\n".getBytes(StandardCharsets.UTF_8));
            digest.update(engineType.name().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            // Parallel evaluation may change floating-point reduction order.
            digest.update(Integer.toString(sampleParallelism).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
