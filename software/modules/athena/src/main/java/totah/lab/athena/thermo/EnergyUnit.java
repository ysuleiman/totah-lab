package totah.lab.athena.thermo;

/** Explicit molar-energy units supported by Athena thermodynamics. */
public enum EnergyUnit {
    JOULES_PER_MOLE(1.0),
    KILOJOULES_PER_MOLE(1_000.0),
    KILOCALORIES_PER_MOLE(4_184.0);

    private final double joulesPerMole;

    EnergyUnit(double joulesPerMole) {
        this.joulesPerMole = joulesPerMole;
    }

    public double convert(double value, EnergyUnit target) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("energy must be finite");
        }
        return value * joulesPerMole / target.joulesPerMole;
    }
}
