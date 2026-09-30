package totah.lab.athena.landscape;

import totah.lab.athena.energy.EnergyComponent;
import totah.lab.athena.energy.ScientificMethod;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Raw local state collection; no implicit Boltzmann interpretation. */
public record LocalBindingLandscape(
        String landscapeId, List<LandscapeState> states, ScientificMethod method) {
    public LocalBindingLandscape {
        if (landscapeId == null || landscapeId.isBlank()) {
            throw new IllegalArgumentException("landscapeId must not be blank");
        }
        states = List.copyOf(Objects.requireNonNull(states, "states"));
        if (method != ScientificMethod.LOCAL_LANDSCAPE) {
            throw new IllegalArgumentException("method must be LOCAL_LANDSCAPE");
        }
    }

    public long viableStateCount() {
        return states.stream().filter(LandscapeState::physicallyValid).count();
    }

    public long forbiddenStateCount() {
        return states.size() - viableStateCount();
    }

    public OptionalDouble minimumPotentialEnergy() {
        return states.stream().filter(LandscapeState::physicallyValid)
                .mapToDouble(state -> state.energy().components()
                        .get(EnergyComponent.TOTAL_POTENTIAL)).min();
    }

    public OptionalDouble medianPotentialEnergy() {
        List<Double> energies = states.stream().filter(LandscapeState::physicallyValid)
                .map(state -> state.energy().components()
                        .get(EnergyComponent.TOTAL_POTENTIAL))
                .sorted(Comparator.naturalOrder()).toList();
        if (energies.isEmpty()) return OptionalDouble.empty();
        int middle = energies.size() / 2;
        return OptionalDouble.of(energies.size() % 2 == 1 ? energies.get(middle)
                : (energies.get(middle - 1) + energies.get(middle)) / 2.0);
    }
}
