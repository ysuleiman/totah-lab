package totah.lab.prometheus.neural;

/** Numerically stable hyperbolic-tangent activation. */
public final class TanhActivation implements Activation {
    @Override public String id() { return "tanh"; }
    @Override public double value(double input) { return Math.tanh(input); }
    @Override public double firstDerivative(double input) {
        double value = Math.tanh(input); return 1.0 - value * value;
    }
    @Override public double secondDerivative(double input) {
        double value = Math.tanh(input); return -2.0 * value * (1.0 - value * value);
    }
}
