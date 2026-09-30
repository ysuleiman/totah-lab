package totah.lab.mettl7.landscape;

import totah.lab.athena.energy.EnergyComponent;
import totah.lab.athena.energy.EnergyEvaluation;
import totah.lab.athena.thermo.EnergyUnit;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Component-wise B-minus-A state-energy evidence; not affinity or delta-delta-G. */
public record Mettl7DifferentialEnergy(
        String matchId,
        Map<EnergyComponent, Double> bMinusA,
        EnergyUnit unit,
        String comparabilityProvenance) {

    public static Mettl7DifferentialEnergy compare(String matchId,
            EnergyEvaluation a, EnergyEvaluation b,
            Mettl7EnergyComparability comparability) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        Objects.requireNonNull(comparability, "comparability");
        if (!a.forceFieldIdentity().equals(b.forceFieldIdentity())
                || !a.forceFieldIdentity().equals(comparability.forceFieldIdentity())) {
            throw new IllegalArgumentException("A/B force fields are not identical");
        }
        comparability.requiredEvaluationProvenance().forEach((key, expected) -> {
            if (!expected.equals(a.provenance().get(key))
                    || !expected.equals(b.provenance().get(key))) {
                throw new IllegalArgumentException(
                        "ENERGY_COMPARABILITY=FAIL: " + key);
            }
        });
        EnergyUnit unit = a.unit();
        EnumMap<EnergyComponent, Double> differences =
                new EnumMap<>(EnergyComponent.class);
        for (EnergyComponent component : comparability.comparableComponents()) {
            Double av = a.components().get(component);
            Double bv = b.components().get(component);
            if (av == null || bv == null) {
                throw new IllegalArgumentException(
                        "missing comparable component " + component);
            }
            double bInAUnits = b.unit().convert(bv, unit);
            differences.put(component, bInAUnits - av);
        }
        return new Mettl7DifferentialEnergy(matchId, differences, unit,
                comparability.preparationProvenance() + ";"
                        + comparability.cancellationAssumptions());
    }

    public Mettl7DifferentialEnergy {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        bMinusA = Map.copyOf(Objects.requireNonNull(bMinusA, "bMinusA"));
        Objects.requireNonNull(unit, "unit");
        if (comparabilityProvenance == null || comparabilityProvenance.isBlank()) {
            throw new IllegalArgumentException("comparability provenance is required");
        }
    }
}
