package totah.lab.prometheus.neural;

/** Linear output activation. */
public final class IdentityActivation implements Activation {
    @Override public String id() { return "identity"; }
    @Override public double value(double input) { return input; }
    @Override public double firstDerivative(double input) { return 1.0; }
    @Override public double secondDerivative(double input) { return 0.0; }
}
