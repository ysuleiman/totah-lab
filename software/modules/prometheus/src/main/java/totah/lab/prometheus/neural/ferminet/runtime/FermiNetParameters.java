package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

/**
 * Immutable parameter storage for the reference-aligned Prometheus
 * FermiNet-v1 ansatz.
 *
 * <p>Initialization semantics follow the locked DeepMind FermiNet reference
 * architecture:
 *
 * <ul>
 *   <li>linear weights: Gaussian with standard deviation 1/sqrt(fan-in)</li>
 *   <li>linear biases: standard Gaussian</li>
 *   <li>isotropic envelope pi: 1.0</li>
 *   <li>isotropic envelope sigma: 1.0</li>
 *   <li>no orbital biases</li>
 *   <li>no trainable determinant coefficients</li>
 * </ul>
 */
public final class FermiNetParameters {

    private final FermiNetParameterLayout layout;
    private final double[] values;

    private FermiNetParameters(
            FermiNetParameterLayout layout,
            double[] values) {

        this.layout = Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(values, "values");

        this.values = values.clone();

        if (values.length != layout.parameterCount()) {
            throw new IllegalArgumentException(
                    "parameter count does not match layout");
        }

        if (Arrays.stream(values).anyMatch(x -> !Double.isFinite(x))) {
            throw new IllegalArgumentException(
                    "FermiNet parameters must be finite");
        }
    }

    /**
     * Deterministically initializes the reference-aligned parameter vector.
     *
     * <p>The Java PRNG is intentionally deterministic for Prometheus replay.
     * Exact Java-vs-JAX parity tests should inject identical parameter arrays
     * rather than assuming java.util.Random reproduces JAX PRNG sequences.
     */
    public static FermiNetParameters initialize(
            FermiNetParameterLayout layout,
            long seed) {

        Objects.requireNonNull(layout, "layout");

        double[] values =
                new double[layout.parameterCount()];

        Random random = new Random(seed);

        for (FermiNetParameterLayout.Block block : layout.blocks()) {

            String name = block.name();
            int[] shape = block.shape();

            if (name.endsWith(".weight")) {

                /*
                 * Layout stores linear weights as:
                 *
                 *   [outputDimension, inputDimension]
                 *
                 * therefore the final shape dimension is fan-in.
                 */
                int fanIn = shape[shape.length - 1];

                double scale =
                        1.0 / Math.sqrt((double) fanIn);

                for (int i = block.startInclusive();
                     i < block.endExclusive();
                     i++) {

                    values[i] =
                            random.nextGaussian() * scale;
                }

            } else if (name.endsWith(".bias")) {

                /*
                 * DeepMind init_linear_layer initializes biases from a
                 * standard normal distribution.
                 */
                for (int i = block.startInclusive();
                     i < block.endExclusive();
                     i++) {

                    values[i] = random.nextGaussian();
                }

            } else if (name.endsWith(".pi")) {

                /*
                 * Reference isotropic envelope:
                 *
                 * pi = ones(...)
                 */
                Arrays.fill(
                        values,
                        block.startInclusive(),
                        block.endExclusive(),
                        1.0);

            } else if (name.endsWith(".sigma")) {

                /*
                 * Reference isotropic envelope:
                 *
                 * sigma = ones(...)
                 */
                Arrays.fill(
                        values,
                        block.startInclusive(),
                        block.endExclusive(),
                        1.0);

            } else {

                throw new IllegalStateException(
                        "unrecognized FermiNet parameter block: "
                                + name);
            }
        }

        return new FermiNetParameters(
                layout,
                values);
    }

    /**
     * Creates parameters from an explicitly supplied vector.
     *
     * <p>This is essential for reference parity testing, where Java and the
     * official JAX FermiNet implementation must evaluate exactly the same
     * numerical parameters.
     */
    public static FermiNetParameters fromArray(
            FermiNetParameterLayout layout,
            double[] values) {

        return new FermiNetParameters(
                layout,
                values);
    }

    public FermiNetParameterLayout layout() {
        return layout;
    }

    public int size() {
        return values.length;
    }

    public double get(int index) {

        if (index < 0 || index >= values.length) {
            throw new IndexOutOfBoundsException(
                    "parameter index: " + index);
        }

        return values[index];
    }

    public double[] toArray() {
        return values.clone();
    }

    @Override
    public boolean equals(Object object) {

        if (this == object) {
            return true;
        }

        if (!(object instanceof FermiNetParameters other)) {
            return false;
        }

        return layout.blocks().equals(other.layout.blocks())
                && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return 31 * layout.blocks().hashCode()
                + Arrays.hashCode(values);
    }
}