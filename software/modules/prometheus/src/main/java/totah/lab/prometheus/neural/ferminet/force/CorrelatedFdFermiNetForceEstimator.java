package totah.lab.prometheus.neural.ferminet.force;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;

/** Adapter preserving the completed correlated-FD implementation unchanged. */
public final class CorrelatedFdFermiNetForceEstimator
        implements FermiNetNuclearForceEstimator {

    public static final String CLASSIFICATION =
            "FERMINET_CORRELATED_FD_REFERENCE_NOISY";

    @Override
    public NuclearForceResult estimate(
            FermiNetForceEvaluationContext context,
            NuclearForceConfiguration configuration) throws IOException {
        if (configuration.estimatorType() != NuclearForceEstimatorType.CORRELATED_FD) {
            throw new IllegalArgumentException("correlated-FD estimator configuration mismatch");
        }
        double delta = configuration.correlatedFd().deltaBohr();
        if (Double.doubleToRawLongBits(delta) != Double.doubleToRawLongBits(
                FermiNetCorrelatedFiniteDifferenceForceReference.STEP_BOHR)) {
            throw new IllegalArgumentException("correlated-FD displacement differs from locked protocol");
        }
        context.verifyDataset();
        var reference = new FermiNetCorrelatedFiniteDifferenceForceReference().evaluate(
                context.state(), context.configurationFile(), context.dataset().walkerCount());
        return adapt(context, configuration, reference);
    }

    /** Pure schema adapter used to prove parity against frozen completed results. */
    public NuclearForceResult adapt(
            FermiNetForceEvaluationContext context,
            NuclearForceConfiguration configuration,
            FermiNetCorrelatedFiniteDifferenceForceReference.Result reference) {
        if (!context.parameterChecksum().equals(reference.parameterChecksum())
                || !context.geometryIdentity().equals(reference.centerGeometryChecksum())
                || !context.dataset().equals(reference.dataset())) {
            throw new IllegalArgumentException("correlated-FD reference provenance mismatch");
        }
        List<NuclearForceResult.Component> common = new ArrayList<>();
        List<NuclearForceResult.ComponentDiagnostics> diagnostics = new ArrayList<>();
        for (var value : reference.components()) {
            var tails = value.tails();
            double[] raw = value.rawForceSamples();
            int finite = 0;
            for (double sample : raw) if (Double.isFinite(sample)) finite++;
            common.add(new NuclearForceResult.Component(
                    value.nucleus(), value.axis(), value.axisName(),
                    value.forceHartreePerBohr(), value.forceStandardError(),
                    value.forceVariance(), finite, raw.length - finite,
                    new NuclearForceResult.TailDiagnostics(
                            tails.minimum(), tails.percentilePointOne(), tails.percentileOne(),
                            tails.median(), tails.percentileNinetyNine(),
                            tails.percentileNinetyNinePointNine(), tails.maximum(),
                            tails.beyondFiveSigma(), tails.beyondTenSigma()),
                    checksum(raw), raw));
            diagnostics.add(new NuclearForceResult.ComponentDiagnostics(
                    value.nucleus(), value.axis(), value.energyPlusHartree(),
                    value.energyMinusHartree(), value.energyContributionCovariance(),
                    value.plusEffectiveSampleSize(), value.minusEffectiveSampleSize(),
                    value.pairedEffectiveSampleSize(), value.plusGeometryChecksum(),
                    value.minusGeometryChecksum()));
        }
        return new NuclearForceResult(
                NuclearForceEstimatorType.CORRELATED_FD, CLASSIFICATION,
                context.parameterChecksum(), context.geometryIdentity(),
                context.dataset().sha256(), context.checkpointChecksum(),
                configuration.identity(), context.dataset().sampleCount(),
                context.dataset().walkerCount(), context.dataset().retainedPerWalker(),
                common, new NuclearForceResult.CorrelatedFdDiagnostics(
                        reference.stepBohr(), diagnostics));
    }

    private static String checksum(double[] values) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        for (double value : values) {
            long bits = Double.doubleToRawLongBits(value);
            for (int shift = 56; shift >= 0; shift -= 8) {
                digest.update((byte) (bits >>> shift));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
