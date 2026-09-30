package totah.lab.athena.thermo;

/** Explicit molar standard-state concentration. */
public record StandardState(double molesPerLitre) {
    public StandardState {
        if (!Double.isFinite(molesPerLitre) || molesPerLitre <= 0.0) {
            throw new IllegalArgumentException(
                    "standard-state concentration must be finite and positive");
        }
    }

    public static StandardState oneMolar() {
        return new StandardState(1.0);
    }
}
