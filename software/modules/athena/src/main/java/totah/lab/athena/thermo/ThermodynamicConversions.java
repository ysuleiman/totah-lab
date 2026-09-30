package totah.lab.athena.thermo;

import java.util.Objects;

/** Exact ideal-standard-state conversions; no concentration unit is implicit. */
public final class ThermodynamicConversions {
    public static final double GAS_CONSTANT_J_PER_MOL_K = 8.31446261815324;

    private ThermodynamicConversions() { }

    public static BindingFreeEnergy fromKd(DissociationConstant kd,
            StandardState standardState, Temperature temperature) {
        Objects.requireNonNull(kd, "kd");
        Objects.requireNonNull(standardState, "standardState");
        Objects.requireNonNull(temperature, "temperature");
        double value = GAS_CONSTANT_J_PER_MOL_K * temperature.kelvin()
                * Math.log(kd.molesPerLitre() / standardState.molesPerLitre());
        return new BindingFreeEnergy(
                new MolarEnergy(value, EnergyUnit.JOULES_PER_MOLE), standardState);
    }

    public static DissociationConstant toKd(BindingFreeEnergy freeEnergy,
            Temperature temperature) {
        Objects.requireNonNull(freeEnergy, "freeEnergy");
        Objects.requireNonNull(temperature, "temperature");
        double joules = freeEnergy.energy().in(EnergyUnit.JOULES_PER_MOLE).value();
        return new DissociationConstant(freeEnergy.standardState().molesPerLitre()
                * Math.exp(joules / (GAS_CONSTANT_J_PER_MOL_K * temperature.kelvin())));
    }

    public static SelectivityFreeEnergy selectivity(SelectivityRatio ratio,
            Temperature temperature) {
        Objects.requireNonNull(ratio, "ratio");
        Objects.requireNonNull(temperature, "temperature");
        return new SelectivityFreeEnergy(new MolarEnergy(
                GAS_CONSTANT_J_PER_MOL_K * temperature.kelvin()
                        * Math.log(ratio.kdBOverKdA()),
                EnergyUnit.JOULES_PER_MOLE));
    }

    public static SelectivityRatio selectivityRatio(
            SelectivityFreeEnergy freeEnergy, Temperature temperature) {
        Objects.requireNonNull(freeEnergy, "freeEnergy");
        Objects.requireNonNull(temperature, "temperature");
        double joules = freeEnergy.bMinusA()
                .in(EnergyUnit.JOULES_PER_MOLE).value();
        return new SelectivityRatio(Math.exp(joules
                / (GAS_CONSTANT_J_PER_MOL_K * temperature.kelvin())));
    }
}
