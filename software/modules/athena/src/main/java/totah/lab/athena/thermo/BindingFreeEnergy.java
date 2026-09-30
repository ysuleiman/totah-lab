package totah.lab.athena.thermo;

import java.util.Objects;

/** Standard binding free energy, never a potential-energy alias. */
public record BindingFreeEnergy(MolarEnergy energy, StandardState standardState) {
    public BindingFreeEnergy {
        Objects.requireNonNull(energy, "energy");
        Objects.requireNonNull(standardState, "standardState");
    }
}
