package totah.lab.prometheus.neural;

import java.util.Arrays;
import java.util.Objects;

/** Immutable fully connected layer. */
public final class DenseLayer {
    private final ParameterTensor weights;
    private final double[] biases;
    private final Activation activation;

    public DenseLayer(ParameterTensor weights, double[] biases, Activation activation) {
        this.weights=Objects.requireNonNull(weights,"weights"); this.biases=biases.clone();
        this.activation=Objects.requireNonNull(activation,"activation");
        if (biases.length != weights.rows()) throw new IllegalArgumentException("one bias is required per output");
    }

    public int inputSize() { return weights.columns(); }
    public int outputSize() { return weights.rows(); }
    public ParameterTensor weights() { return weights; }
    public double bias(int output) { return biases[output]; }
    public double[] biases() { return biases.clone(); }
    public Activation activation() { return activation; }

    @Override public boolean equals(Object object) {
        return object instanceof DenseLayer other && weights.equals(other.weights)
                && Arrays.equals(biases, other.biases) && activation.id().equals(other.activation.id());
    }

    @Override public int hashCode() { return Objects.hash(weights, Arrays.hashCode(biases), activation.id()); }
}
