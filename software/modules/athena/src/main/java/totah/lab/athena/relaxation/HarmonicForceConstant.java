package totah.lab.athena.relaxation;

import java.util.Objects;

/** Harmonic positional-restraint force constant with explicit units. */
public record HarmonicForceConstant(double value, Unit unit) {
    public HarmonicForceConstant {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("force constant must be finite and positive");
        }
        Objects.requireNonNull(unit, "unit");
    }

    public double kilojoulesPerMoleNanometreSquared() {
        return value * unit.kilojoulesPerMoleNanometreSquared;
    }

    public enum Unit {
        KILOJOULES_PER_MOLE_NANOMETRE_SQUARED(1.0),
        KILOCALORIES_PER_MOLE_ANGSTROM_SQUARED(418.4);

        private final double kilojoulesPerMoleNanometreSquared;

        Unit(double conversion) {
            this.kilojoulesPerMoleNanometreSquared = conversion;
        }
    }
}
