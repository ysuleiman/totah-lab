package totah.lab.prometheus.neural.ferminet.reference;

import java.io.IOException;
import java.util.List;

import totah.lab.prometheus.neural.ferminet.runtime.FermiNetMatrixFreeSrOptimizer;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;

/** Narrow bridge for deterministic explicit-Jacobian reference comparisons. */
public final class FermiNetReferenceAccess {

    private FermiNetReferenceAccess() {}

    public static ExplicitSnapshot explicitSnapshot(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples)
            throws IOException {
        try (FermiNetSrObservationFile observations =
                     FermiNetSrObservationFile.build(state, samples)) {
            int sampleCount = observations.sampleCount();
            int parameterCount = observations.parameterCount();
            double[] weights = new double[sampleCount];
            double[] energies = new double[sampleCount];
            for (int sample = 0; sample < sampleCount; sample++) {
                weights[sample] = observations.weight(sample);
                energies[sample] = observations.localEnergyHartree(sample);
            }
            double[] derivatives = new double[Math.multiplyExact(
                    sampleCount, parameterCount)];
            observations.readParameterBlock(0, parameterCount, derivatives);
            return new ExplicitSnapshot(
                    sampleCount, parameterCount, weights, energies, derivatives);
        }
    }

    public record ExplicitSnapshot(
            int sampleCount,
            int parameterCount,
            double[] weights,
            double[] localEnergiesHartree,
            double[] parameterLogDerivatives) {
        public ExplicitSnapshot {
            weights = weights.clone();
            localEnergiesHartree = localEnergiesHartree.clone();
            parameterLogDerivatives = parameterLogDerivatives.clone();
        }

        @Override
        public double[] weights() {
            return weights.clone();
        }

        @Override
        public double[] localEnergiesHartree() {
            return localEnergiesHartree.clone();
        }

        @Override
        public double[] parameterLogDerivatives() {
            return parameterLogDerivatives.clone();
        }
    }
}
