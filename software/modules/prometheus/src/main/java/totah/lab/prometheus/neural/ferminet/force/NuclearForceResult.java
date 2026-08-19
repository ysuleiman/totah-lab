package totah.lab.prometheus.neural.ferminet.force;

import java.util.List;
import java.util.Objects;

/** Common immutable outer result schema for every FermiNet force estimator. */
public record NuclearForceResult(
        NuclearForceEstimatorType estimatorType,
        String classification,
        String parameterChecksum,
        String geometryIdentity,
        String datasetChecksum,
        String checkpointChecksum,
        String estimatorConfigurationIdentity,
        int sampleCount,
        int chainCount,
        int retainedPerChain,
        List<Component> components,
        EstimatorDiagnostics estimatorDiagnostics) {

    public NuclearForceResult {
        Objects.requireNonNull(estimatorType, "estimatorType");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(parameterChecksum, "parameterChecksum");
        Objects.requireNonNull(geometryIdentity, "geometryIdentity");
        Objects.requireNonNull(datasetChecksum, "datasetChecksum");
        Objects.requireNonNull(checkpointChecksum, "checkpointChecksum");
        Objects.requireNonNull(estimatorConfigurationIdentity,
                "estimatorConfigurationIdentity");
        components = List.copyOf(components);
        Objects.requireNonNull(estimatorDiagnostics, "estimatorDiagnostics");
        if (sampleCount < 1 || chainCount < 1 || retainedPerChain < 1
                || components.isEmpty()) {
            throw new IllegalArgumentException("incomplete nuclear-force result");
        }
    }

    public record Component(
            int nucleus,
            int axis,
            String axisName,
            double meanHartreePerBohr,
            double chainStandardError,
            double variance,
            int finiteCount,
            int nonfiniteCount,
            TailDiagnostics tails,
            String rawSampleChecksum,
            double[] rawSamples) {
        public Component {
            Objects.requireNonNull(axisName, "axisName");
            Objects.requireNonNull(tails, "tails");
            Objects.requireNonNull(rawSampleChecksum, "rawSampleChecksum");
            rawSamples = rawSamples.clone();
        }
        @Override public double[] rawSamples() { return rawSamples.clone(); }
    }

    public record TailDiagnostics(
            double minimum, double percentilePointOne, double percentileOne,
            double median, double percentileNinetyNine,
            double percentileNinetyNinePointNine, double maximum,
            long beyondFiveSigma, long beyondTenSigma) {}

    public sealed interface EstimatorDiagnostics permits CorrelatedFdDiagnostics, SwctDiagnostics {}

    public record CorrelatedFdDiagnostics(
            double deltaBohr,
            List<ComponentDiagnostics> components)
            implements EstimatorDiagnostics {
        public CorrelatedFdDiagnostics { components = List.copyOf(components); }
    }

    /**
     * SWCT decomposition and correlated-FD comparison evidence. The
     * comparison is diagnostic only: correlated FD is statistically noisy,
     * so an indistinguishable difference of means is not evidence that SWCT
     * is unbiased. No acceptance thresholds are applied anywhere.
     */
    public record SwctDiagnostics(
            double meanLocalEnergyHartree,
            List<SwctComponentDiagnostics> components)
            implements EstimatorDiagnostics {
        public SwctDiagnostics { components = List.copyOf(components); }
    }

    public record SwctComponentDiagnostics(
            int nucleus,
            int axis,
            double meanDirectForceTermHartreePerBohr,
            double meanKineticDirectionalDerivativePerBohr,
            double meanCoulombDirectionalDerivativePerBohr,
            double meanCovariancePulayTermHartreePerBohr,
            double meanWavefunctionLogTermHartreePerBohr,
            double meanJacobianDivergenceTermHartreePerBohr,
            double meanDirectionalLogDerivativePerBohr,
            double correlatedFdMeanHartreePerBohr,
            double correlatedFdChainStandardError,
            double correlatedFdVariance,
            double varianceReductionFactor,
            double meanDifferenceHartreePerBohr,
            double differenceCombinedUncertainty,
            double differenceOverCombinedUncertainty) {}

    public record ComponentDiagnostics(
            int nucleus,
            int axis,
            double energyPlusHartree,
            double energyMinusHartree,
            double energyContributionCovariance,
            double plusEffectiveSampleSize,
            double minusEffectiveSampleSize,
            double pairedEffectiveSampleSize,
            String plusGeometryChecksum,
            String minusGeometryChecksum) {}
}
