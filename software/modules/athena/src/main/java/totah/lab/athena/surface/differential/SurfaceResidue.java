package totah.lab.athena.surface.differential;

import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;

/** A residue plus the exposure values consumed by the scoring kernel. */
public record SurfaceResidue(
        ResidueId id,
        Residue residue,
        double sasaSquareAngstroms,
        double relativeSasa) {

    public SurfaceResidue {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(residue, "residue");
        requireNonNegative(sasaSquareAngstroms, "sasaSquareAngstroms");
        requireNonNegative(relativeSasa, "relativeSasa");
    }

    public boolean isSurface(DifferentialSurfaceOptions options) {
        Objects.requireNonNull(options, "options");
        return sasaSquareAngstroms > options.absoluteSasaThreshold()
                && relativeSasa > options.relativeSasaThreshold();
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative");
        }
    }
}
