package totah.lab.prometheus.neural;

/** Scalar activation with derivatives needed for input gradients and Laplacians. */
public interface Activation {
    String id();
    double value(double input);
    double firstDerivative(double input);
    double secondDerivative(double input);
}
