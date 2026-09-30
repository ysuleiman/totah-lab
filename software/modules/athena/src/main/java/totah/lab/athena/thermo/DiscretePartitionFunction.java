package totah.lab.athena.thermo;

import totah.lab.athena.energy.ScientificMethod;

import java.util.List;
import java.util.Objects;

/** Measure-aware discrete configurational partition calculation. */
public final class DiscretePartitionFunction {
    private DiscretePartitionFunction() { }

    public static Result evaluate(List<WeightedEnergyState> states,
            Temperature temperature, String measureProvenance) {
        states = List.copyOf(Objects.requireNonNull(states, "states"));
        Objects.requireNonNull(temperature, "temperature");
        if (states.isEmpty()) throw new IllegalArgumentException("states must not be empty");
        if (measureProvenance == null || measureProvenance.isBlank()) {
            throw new IllegalArgumentException("sampling measure provenance is required");
        }
        double rt = ThermodynamicConversions.GAS_CONSTANT_J_PER_MOL_K
                * temperature.kelvin();
        double maxLogWeight = states.stream().mapToDouble(state ->
                Math.log(state.measure())
                        - state.energy().in(EnergyUnit.JOULES_PER_MOLE).value() / rt)
                .max().orElseThrow();
        double scaled = states.stream().mapToDouble(state -> Math.exp(
                Math.log(state.measure())
                        - state.energy().in(EnergyUnit.JOULES_PER_MOLE).value() / rt
                        - maxLogWeight)).sum();
        double logPartition = maxLogWeight + Math.log(scaled);
        return new Result(logPartition,
                new MolarEnergy(-rt * logPartition, EnergyUnit.JOULES_PER_MOLE),
                measureProvenance.trim(), ScientificMethod.DISCRETE_STATISTICAL_ESTIMATE);
    }

    public record Result(double logPartition, MolarEnergy freeEnergy,
            String measureProvenance, ScientificMethod method) { }
}
