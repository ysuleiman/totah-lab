package totah.lab.prometheus.neural.ferminet.force;

import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Immutable canonical configuration for every FermiNet force estimator. */
public record NuclearForceConfiguration(
        NuclearForceEstimatorType estimatorType,
        CorrelatedFdConfiguration correlatedFd,
        PathakWagnerConfiguration pathakWagner) {

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
        if (pathakWagner != null
                && estimatorType != NuclearForceEstimatorType.AC_ZVZB_DERIV) {
            throw new IllegalArgumentException("Pathak-Wagner requires AC_ZVZB_DERIV");
        }
    }

    public static NuclearForceConfiguration correlatedFd(double deltaBohr) {
        return new NuclearForceConfiguration(
                NuclearForceEstimatorType.CORRELATED_FD,
                new CorrelatedFdConfiguration(deltaBohr), null);
    }

    /** SWCT has no numerical knobs: the derivation is fully analytic. */
    public static NuclearForceConfiguration swct() {
        return new NuclearForceConfiguration(
                NuclearForceEstimatorType.SWCT, null, null);
    }

    /** AC-ZV has no numerical knobs: the derivation is fully analytic. */
    public static NuclearForceConfiguration acZv() {
        return new NuclearForceConfiguration(
                NuclearForceEstimatorType.AC_ZV, null, null);
    }

    /** AC-ZVZB has no numerical knobs: the derivation is fully analytic. */
    public static NuclearForceConfiguration acZvzb() {
        return new NuclearForceConfiguration(
                NuclearForceEstimatorType.AC_ZVZB, null, null);
    }

    /** AC-ZVZB-DERIV has no numerical knobs: the derivation is fully analytic. */
    public static NuclearForceConfiguration acZvzbDeriv() {
        return new NuclearForceConfiguration(
                NuclearForceEstimatorType.AC_ZVZB_DERIV, null, null);
    }

    public static NuclearForceConfiguration acZvzbDerivPathakWagner(
            double... epsilonBohr) {
        return new NuclearForceConfiguration(
                NuclearForceEstimatorType.AC_ZVZB_DERIV, null,
                new PathakWagnerConfiguration(epsilonBohr));
    }

    public static NuclearForceConfiguration unsupported(
            NuclearForceEstimatorType type) {
        if (type == NuclearForceEstimatorType.CORRELATED_FD) {
            throw new IllegalArgumentException("use correlatedFd configuration factory");
        }
        return new NuclearForceConfiguration(type, null, null);
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
            if (pathakWagner != null) {
                for (double epsilon : pathakWagner.epsilonBohr()) {
                    long bits = Double.doubleToRawLongBits(epsilon);
                    for (int shift = 56; shift >= 0; shift -= 8) {
                        digest.update((byte) (bits >>> shift));
                    }
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

    public record PathakWagnerConfiguration(double[] epsilonBohr) {
        public PathakWagnerConfiguration {
            epsilonBohr = epsilonBohr.clone();
            if (epsilonBohr.length < 1) {
                throw new IllegalArgumentException("empty Pathak-Wagner epsilon panel");
            }
            double previous = Double.POSITIVE_INFINITY;
            for (double epsilon : epsilonBohr) {
                if (!(epsilon > 0.0) || !Double.isFinite(epsilon)
                        || epsilon >= previous) {
                    throw new IllegalArgumentException(
                            "Pathak-Wagner epsilons must be positive and descending");
                }
                previous = epsilon;
            }
        }
        @Override public double[] epsilonBohr() { return epsilonBohr.clone(); }
    }
}
