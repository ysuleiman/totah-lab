package totah.lab.prometheus.variational;

/** Complex-valued wavefunction amplitude. */
public record QuantumAmplitude(double real, double imaginary) {
    public static QuantumAmplitude real(double value) { return new QuantumAmplitude(value, 0.0); }
}
