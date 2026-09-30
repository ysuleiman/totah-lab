package totah.lab.athena.thermo;

/** Absolute temperature in kelvin. */
public record Temperature(double kelvin) {
    public Temperature {
        if (!Double.isFinite(kelvin) || kelvin <= 0.0) {
            throw new IllegalArgumentException("kelvin must be finite and positive");
        }
    }
}
