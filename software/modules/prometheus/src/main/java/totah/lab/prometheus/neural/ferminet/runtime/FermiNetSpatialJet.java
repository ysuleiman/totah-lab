package totah.lab.prometheus.neural.ferminet.runtime;

/**
 * Validated scalar reference jet: value, Cartesian gradient, and summed
 * Cartesian Laplacian for one scalar.
 *
 * <p>The REFERENCE_JET arithmetic below intentionally retains its established
 * operation order. The only cross-backend behavior is dispatch when an
 * explicitly batched operand is supplied; reference-only execution never
 * enters those branches.
 */
class FermiNetSpatialJet {
    private double value;
    private final double[] gradient;
    private double laplacian;
    private final FermiNetSpatialJet directional;

    protected FermiNetSpatialJet(
            double value, double[] gradient, double laplacian) {
        this(value, gradient, laplacian, null);
    }

    private FermiNetSpatialJet(
            double value, double[] gradient, double laplacian,
            FermiNetSpatialJet directional) {
        this.value = value;
        this.gradient = gradient;
        this.laplacian = laplacian;
        this.directional = directional;
    }

    static FermiNetSpatialJet constant(double value, int dimensions) {
        return new FermiNetSpatialJet(value, new double[dimensions], 0.0);
    }

    static FermiNetSpatialJet variable(
            double value, int dimensions, int axis) {
        double[] gradient = new double[dimensions];
        gradient[axis] = 1.0;
        return new FermiNetSpatialJet(value, gradient, 0.0);
    }

    static FermiNetSpatialJet directionalConstant(
            double value, int dimensions, double directionalValue) {
        return new FermiNetSpatialJet(value, new double[dimensions], 0.0,
                constant(directionalValue, dimensions));
    }

    static FermiNetSpatialJet directionalVariable(
            double value, int dimensions, int axis, double directionalValue) {
        double[] gradient = new double[dimensions];
        gradient[axis] = 1.0;
        return new FermiNetSpatialJet(value, gradient, 0.0,
                constant(directionalValue, dimensions));
    }

    double value() { return value; }
    double gradient(int axis) { return gradient[axis]; }
    double[] gradient() { return gradient.clone(); }
    double laplacian() { return laplacian; }
    int dimensions() { return gradient.length; }
    double directionalValue() { return requireDirectional().value; }
    double directionalLaplacian() { return requireDirectional().laplacian; }
    boolean hasDirectional() { return directional != null; }

    /** Batched-backend initialization hook; reference jets never call it. */
    protected final void setPrimal(double value, double laplacian) {
        this.value = value;
        this.laplacian = laplacian;
    }

    /** Batched-backend fused-affine hook; reference jets never call it. */
    protected final void addPrimalValue(double increment) {
        value += increment;
    }

    /** Batched-backend fused-affine hook; reference jets never call it. */
    protected final void addPrimalLaplacian(double increment) {
        laplacian += increment;
    }

    FermiNetSpatialJet add(FermiNetSpatialJet other) {
        if (other instanceof FermiNetBatchedSpatialJet batched) {
            return batched.addReference(this);
        }
        require(other);
        double[] nextGradient = new double[gradient.length];
        for (int i = 0; i < nextGradient.length; i++) {
            nextGradient[i] = gradient[i] + other.gradient[i];
        }
        FermiNetSpatialJet nextDirectional = null;
        if (directional != null || other.directional != null) {
            nextDirectional = directionalOrZero().add(other.directionalOrZero());
        }
        return new FermiNetSpatialJet(value + other.value, nextGradient,
                laplacian + other.laplacian, nextDirectional);
    }

    FermiNetSpatialJet add(double scalar) {
        return new FermiNetSpatialJet(value + scalar, gradient.clone(),
                laplacian, directional);
    }

    FermiNetSpatialJet subtract(FermiNetSpatialJet other) {
        return add(other.multiply(-1.0));
    }

    FermiNetSpatialJet multiply(double scalar) {
        double[] nextGradient = gradient.clone();
        for (int i = 0; i < nextGradient.length; i++) {
            nextGradient[i] *= scalar;
        }
        return new FermiNetSpatialJet(value * scalar, nextGradient,
                laplacian * scalar,
                directional == null ? null : directional.multiply(scalar));
    }

    FermiNetSpatialJet multiply(FermiNetSpatialJet other) {
        if (other instanceof FermiNetBatchedSpatialJet batched) {
            return batched.multiplyReference(this);
        }
        require(other);
        double[] nextGradient = new double[gradient.length];
        double dot = 0.0;
        for (int i = 0; i < nextGradient.length; i++) {
            nextGradient[i] = gradient[i] * other.value
                    + value * other.gradient[i];
            dot += gradient[i] * other.gradient[i];
        }
        FermiNetSpatialJet nextDirectional = null;
        if (directional != null || other.directional != null) {
            nextDirectional = directionalOrZero().multiply(other.primal())
                    .add(primal().multiply(other.directionalOrZero()));
        }
        return new FermiNetSpatialJet(value * other.value, nextGradient,
                laplacian * other.value + 2.0 * dot
                        + value * other.laplacian,
                nextDirectional);
    }

    FermiNetSpatialJet reciprocal() {
        FermiNetSpatialJet derivative = null;
        if (directional != null) {
            FermiNetSpatialJet result = primalReciprocal();
            derivative = result.multiply(result).multiply(-1.0);
        }
        return unary(1.0 / value, -1.0 / (value * value),
                2.0 / (value * value * value), derivative);
    }

    FermiNetSpatialJet divide(FermiNetSpatialJet other) {
        return multiply(other.reciprocal());
    }

    FermiNetSpatialJet exp() {
        double result = Math.exp(value);
        FermiNetSpatialJet derivative = directional == null
                ? null : primal().unary(result, result, result);
        return unary(result, result, result, derivative);
    }

    FermiNetSpatialJet sqrt() {
        double result = Math.sqrt(value);
        FermiNetSpatialJet derivative = directional == null ? null
                : primal().unary(result, 0.5 / result,
                        -0.25 / (value * result)).reciprocal().multiply(0.5);
        return unary(result, 0.5 / result, -0.25 / (value * result),
                derivative);
    }

    FermiNetSpatialJet tanh() {
        double result = Math.tanh(value);
        double first = 1.0 - result * result;
        FermiNetSpatialJet derivative = null;
        if (directional != null) {
            FermiNetSpatialJet primal = primal().unary(
                    result, first, -2.0 * result * first);
            derivative = constant(1.0, dimensions())
                    .subtract(primal.multiply(primal));
        }
        return unary(result, first, -2.0 * result * first, derivative);
    }

    static FermiNetSpatialJet affine(
            FermiNetSpatialJet[] input, double[] weights,
            int offset, double bias) {
        for (FermiNetSpatialJet value : input) {
            if (value instanceof FermiNetBatchedSpatialJet) {
                return FermiNetBatchedSpatialJet.affineBatched(
                        input, weights, offset, bias);
            }
        }
        int dimensions = input[0].dimensions();
        double value = bias;
        double laplacian = 0.0;
        double[] gradient = new double[dimensions];
        boolean directional = false;
        for (int j = 0; j < input.length; j++) {
            double weight = weights[offset + j];
            value += weight * input[j].value;
            laplacian += weight * input[j].laplacian;
            directional |= input[j].directional != null;
            for (int axis = 0; axis < dimensions; axis++) {
                gradient[axis] += weight * input[j].gradient[axis];
            }
        }
        FermiNetSpatialJet nextDirectional = null;
        if (directional) {
            nextDirectional = constant(0.0, dimensions);
            for (int j = 0; j < input.length; j++) {
                if (input[j].directional != null) {
                    nextDirectional = nextDirectional.add(
                            input[j].directional.multiply(weights[offset + j]));
                }
            }
        }
        return new FermiNetSpatialJet(value, gradient, laplacian,
                nextDirectional);
    }

    private FermiNetSpatialJet unary(
            double result, double first, double second) {
        return unary(result, first, second, null);
    }

    private FermiNetSpatialJet unary(
            double result, double first, double second,
            FermiNetSpatialJet derivative) {
        double[] nextGradient = new double[gradient.length];
        double norm = 0.0;
        for (int i = 0; i < nextGradient.length; i++) {
            nextGradient[i] = first * gradient[i];
            norm += gradient[i] * gradient[i];
        }
        FermiNetSpatialJet nextDirectional = directional == null
                ? null : derivative.multiply(directional);
        return new FermiNetSpatialJet(result, nextGradient,
                second * norm + first * laplacian, nextDirectional);
    }

    private FermiNetSpatialJet primal() {
        return directional == null
                ? this : new FermiNetSpatialJet(value, gradient, laplacian);
    }

    private FermiNetSpatialJet primalReciprocal() {
        return primal().unary(1.0 / value, -1.0 / (value * value),
                2.0 / (value * value * value));
    }

    private FermiNetSpatialJet directionalOrZero() {
        return directional == null ? constant(0.0, dimensions()) : directional;
    }

    private FermiNetSpatialJet requireDirectional() {
        if (directional == null) {
            throw new IllegalStateException("directional tangent is absent");
        }
        return directional;
    }

    private void require(FermiNetSpatialJet other) {
        if (gradient.length != other.gradient.length) {
            throw new IllegalArgumentException("spatial dimensions disagree");
        }
    }
}
