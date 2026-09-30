package totah.lab.athena.thermo;

import totah.lab.athena.energy.ScientificMethod;

import java.util.List;
import java.util.Objects;

/** Zwanzig identity with explicit normalized A-ensemble weights. */
public final class ZwanzigFreeEnergyEstimator {
    private ZwanzigFreeEnergyEstimator() { }

    public static Result estimate(List<MatchedEnergy> states,
            Temperature temperature, String samplingProvenance) {
        states = List.copyOf(Objects.requireNonNull(states, "states"));
        Objects.requireNonNull(temperature, "temperature");
        if (states.isEmpty()) throw new IllegalArgumentException("states must not be empty");
        if (samplingProvenance == null || samplingProvenance.isBlank()) {
            throw new IllegalArgumentException("A-ensemble sampling provenance is required");
        }
        double sumWeights = states.stream().mapToDouble(MatchedEnergy::aEnsembleWeight).sum();
        if (Math.abs(sumWeights - 1.0) > 1.0e-12) {
            throw new IllegalArgumentException("A-ensemble weights must sum to one");
        }
        double rt = ThermodynamicConversions.GAS_CONSTANT_J_PER_MOL_K
                * temperature.kelvin();
        double[] logTerms = states.stream().mapToDouble(state ->
                Math.log(state.aEnsembleWeight())
                        - state.deltaEnergy().in(EnergyUnit.JOULES_PER_MOLE).value() / rt)
                .toArray();
        double max = java.util.Arrays.stream(logTerms).max().orElseThrow();
        double logMean = max + Math.log(java.util.Arrays.stream(logTerms)
                .map(value -> Math.exp(value - max)).sum());
        double exponentialSum = java.util.Arrays.stream(logTerms)
                .map(value -> Math.exp(value - max)).sum();
        double squaredNormalizedImportanceWeightSum = java.util.Arrays.stream(logTerms)
                .map(value -> Math.exp(value - max) / exponentialSum)
                .map(value -> value * value).sum();
        return new Result(new MolarEnergy(-rt * logMean, EnergyUnit.JOULES_PER_MOLE),
                1.0 / squaredNormalizedImportanceWeightSum, samplingProvenance.trim(),
                ScientificMethod.FEP_ESTIMATE);
    }

    public record MatchedEnergy(String stateId, MolarEnergy deltaEnergy,
            double aEnsembleWeight) {
        public MatchedEnergy {
            if (stateId == null || stateId.isBlank()) {
                throw new IllegalArgumentException("stateId must not be blank");
            }
            Objects.requireNonNull(deltaEnergy, "deltaEnergy");
            if (!Double.isFinite(aEnsembleWeight) || aEnsembleWeight <= 0.0) {
                throw new IllegalArgumentException("ensemble weight must be positive");
            }
        }
    }

    public record Result(MolarEnergy deltaFreeEnergy, double effectiveSampleSize,
            String samplingProvenance, ScientificMethod method) { }
}
