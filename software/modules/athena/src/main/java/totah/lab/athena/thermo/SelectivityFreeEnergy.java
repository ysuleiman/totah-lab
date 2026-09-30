package totah.lab.athena.thermo;

import java.util.Objects;

/** Frozen convention: delta-delta-G(B-A) = G_B - G_A. */
public record SelectivityFreeEnergy(MolarEnergy bMinusA) {
    public SelectivityFreeEnergy {
        Objects.requireNonNull(bMinusA, "bMinusA");
    }
}
