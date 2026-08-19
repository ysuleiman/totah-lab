package totah.lab.prometheus.neural.ferminet.force;

import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Immutable canonical configuration for every FermiNet force estimator. */
public record NuclearForceConfiguration(
        NuclearForceEstimatorType estimatorType,
        CorrelatedFdConfiguration correlatedFd) {

    public NuclearForceConfiguration {
        Objects.requireNonNull(estimatorType, "estimatorType");
        if (estimatorType == NuclearForceEstimatorType.CORRELATED_FD
                && correlatedFd == null) {
            throw new IllegalArgumentException("CORRELATED_FD configuration is required");
        }
        if (estimatorType != NuclearForceEstimatorType.CORRELATED_FD
                && correlatedFd != null) {
            throw new IllegalArgumentException("estimator-specific configuration mismatch");
        }
    }

    public static NuclearForceConfiguration correlatedFd(double deltaBohr) {
        return new NuclearForceConfiguration(
                NuclearForceEstimatorType.CORRELATED_FD,
                new CorrelatedFdConfiguration(deltaBohr));
    }

    /** SWCT has no numerical knobs: the derivation is fully analytic. */
    public static NuclearForceConfiguration swct() {
        return new NuclearForceConfiguration(
                NuclearForceEstimatorType.SWCT, null);
    }

    /** AC-ZV has no numerical knobs: the derivation is fully analytic. */
    public static NuclearForceConfiguration acZv() {
        return new NuclearForceConfiguration(
                NuclearForceEstimatorType.AC_ZV, null);
    }

    /** AC-ZVZB has no numerical knobs: the derivation is fully analytic. */
    public static NuclearForceConfiguration acZvzb() {
        return new NuclearForceConfiguration(
                NuclearForceEstimatorType.AC_ZVZB, null);
    }

    public static NuclearForceConfiguration unsupported(
            NuclearForceEstimatorType type) {
        if (type == NuclearForceEstimatorType.CORRELATED_FD) {
            throw new IllegalArgumentException("use correlatedFd configuration factory");
        }
        return new NuclearForceConfiguration(type, null);
    }

    public String identity() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(estimatorType.name().getBytes(StandardCharsets.UTF_8));
            if (correlatedFd != null) {
                long bits = Double.doubleToRawLongBits(correlatedFd.deltaBohr());
                for (int shift = 56; shift >= 0; shift -= 8) {
                    digest.update((byte) (bits >>> shift));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record CorrelatedFdConfiguration(double deltaBohr) {
        public CorrelatedFdConfiguration {
            if (!(deltaBohr > 0.0) || !Double.isFinite(deltaBohr)) {
                throw new IllegalArgumentException("invalid correlated-FD displacement");
            }
        }
    }
}
