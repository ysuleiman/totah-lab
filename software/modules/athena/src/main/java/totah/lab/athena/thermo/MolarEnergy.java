package totah.lab.athena.thermo;

import java.util.Objects;

/** A finite molar energy with an explicit unit. */
public record MolarEnergy(double value, EnergyUnit unit) {
    public MolarEnergy {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        Objects.requireNonNull(unit, "unit");
    }

    public MolarEnergy in(EnergyUnit target) {
        return new MolarEnergy(unit.convert(value, target), target);
    }
}
