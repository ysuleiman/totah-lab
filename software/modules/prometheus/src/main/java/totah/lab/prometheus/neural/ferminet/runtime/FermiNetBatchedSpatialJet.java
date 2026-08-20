package totah.lab.prometheus.neural.ferminet.runtime;

/** Shared-primal, direction-major forward jet backed by an explicit arena. */
final class FermiNetBatchedSpatialJet extends FermiNetSpatialJet {
    private FermiNetBatchedJetWorkspace workspace;
    private double[] data;
    private int gradientOffset;
    private int directionalValueOffset;
    private int directionalGradientOffset;
    private int directionalLaplacianOffset;
    private int dimensions;
    private int directions;
    private boolean released;

    FermiNetBatchedSpatialJet() {
        super(0.0, new double[0], 0.0);
    }

    void initialize(
            FermiNetBatchedJetWorkspace workspace,
            double[] data,
            int offset,
            double value,
            double laplacian,
            int dimensions,
            int directions) {
        setPrimal(value, laplacian);
        this.workspace = workspace;
        this.data = data;
        this.gradientOffset = offset;
        this.directionalValueOffset = offset + dimensions;
        this.directionalGradientOffset = directionalValueOffset + directions;
        this.directionalLaplacianOffset = directionalGradientOffset
                + directions * dimensions;
        this.dimensions = dimensions;
        this.directions = directions;
        this.released = false;
    }

    void prepare(
            double value, double laplacian,
            int dimensions, int directions) {
        if (this.dimensions != dimensions || this.directions != directions) {
            throw new IllegalArgumentException("workspace jet shape changed");
        }
        setPrimal(value, laplacian);
        java.util.Arrays.fill(data, gradientOffset,
                directionalLaplacianOffset + directions, 0.0);
        released = false;
    }

    boolean releaseFrom(FermiNetBatchedJetWorkspace owner) {
        if (workspace != owner || released) return false;
        released = true;
        return true;
    }

    void markReleased() {
        released = true;
    }

    static FermiNetBatchedSpatialJet constant(
            FermiNetBatchedJetWorkspace workspace,
            double value, int dimensions, double[] directionalValue) {
        FermiNetBatchedSpatialJet result = workspace.acquire(
                value, 0.0, dimensions, directionalValue.length);
        for (int direction = 0; direction < directionalValue.length; direction++) {
            result.setDirectionalValue(direction, directionalValue[direction]);
        }
        return result;
    }

    static FermiNetBatchedSpatialJet variable(
            FermiNetBatchedJetWorkspace workspace,
            double value, int dimensions, int axis,
            double[] directionalValue) {
        FermiNetBatchedSpatialJet result = constant(
                workspace, value, dimensions, directionalValue);
        result.setGradient(axis, 1.0);
        return result;
    }

    int directions() { return directions; }
    @Override int dimensions() { return dimensions; }
    @Override double gradient(int axis) { return data[gradientOffset + axis]; }
    @Override double[] gradient() {
        double[] result = new double[dimensions];
        System.arraycopy(data, gradientOffset, result, 0, dimensions);
        return result;
    }
    double directionalValue(int direction) {
        requireDirection(direction);
        return data[directionalValueOffset + direction];
    }
    double directionalGradient(int direction, int axis) {
        return data[directionalGradientOffset + direction * dimensions + axis];
    }
    double directionalLaplacian(int direction) {
        requireDirection(direction);
        return data[directionalLaplacianOffset + direction];
    }
    @Override boolean hasDirectional() { return true; }
    @Override double directionalValue() { return directionalValue(0); }
    @Override double directionalLaplacian() { return directionalLaplacian(0); }

    @Override
    FermiNetBatchedSpatialJet add(FermiNetSpatialJet other) {
        requireDimensions(other);
        FermiNetBatchedSpatialJet right = batched(other);
        FermiNetBatchedSpatialJet result = workspace.acquire(
                value() + other.value(), laplacian() + other.laplacian(),
                dimensions, directions);
        for (int axis = 0; axis < dimensions; axis++) {
            result.setGradient(axis, gradient(axis) + other.gradient(axis));
        }
        for (int direction = 0; direction < directions; direction++) {
            result.setDirectionalValue(direction, directionalValue(direction)
                    + directionalValue(right, direction));
            result.setDirectionalLaplacian(direction,
                    directionalLaplacian(direction)
                            + directionalLaplacian(right, direction));
            for (int axis = 0; axis < dimensions; axis++) {
                result.setDirectionalGradient(direction, axis,
                        directionalGradient(direction, axis)
                                + directionalGradient(right, direction, axis));
            }
        }
        return result;
    }

    FermiNetBatchedSpatialJet addReference(FermiNetSpatialJet reference) {
        return add(reference);
    }

    @Override
    FermiNetBatchedSpatialJet add(double scalar) {
        FermiNetBatchedSpatialJet result = workspace.acquire(
                value() + scalar, laplacian(), dimensions, directions);
        copyDerivativesTo(result, 1.0);
        return result;
    }

    @Override
    FermiNetBatchedSpatialJet subtract(FermiNetSpatialJet other) {
        requireDimensions(other);
        FermiNetBatchedSpatialJet right = batched(other);
        FermiNetBatchedSpatialJet result = workspace.acquire(
                value() - other.value(), laplacian() - other.laplacian(),
                dimensions, directions);
        for (int axis = 0; axis < dimensions; axis++) {
            result.setGradient(axis, gradient(axis) - other.gradient(axis));
        }
        for (int direction = 0; direction < directions; direction++) {
            result.setDirectionalValue(direction, directionalValue(direction)
                    - directionalValue(right, direction));
            result.setDirectionalLaplacian(direction,
                    directionalLaplacian(direction)
                            - directionalLaplacian(right, direction));
            for (int axis = 0; axis < dimensions; axis++) {
                result.setDirectionalGradient(direction, axis,
                        directionalGradient(direction, axis)
                                - directionalGradient(right, direction, axis));
            }
        }
        return result;
    }

    @Override
    FermiNetBatchedSpatialJet multiply(double scalar) {
        FermiNetBatchedSpatialJet result = workspace.acquire(
                value() * scalar, laplacian() * scalar,
                dimensions, directions);
        copyDerivativesTo(result, scalar);
        return result;
    }

    @Override
    FermiNetBatchedSpatialJet multiply(FermiNetSpatialJet other) {
        requireDimensions(other);
        FermiNetBatchedSpatialJet right = batched(other);
        double gradientDot = 0.0;
        for (int axis = 0; axis < dimensions; axis++) {
            gradientDot += gradient(axis) * other.gradient(axis);
        }
        FermiNetBatchedSpatialJet result = workspace.acquire(
                value() * other.value(),
                laplacian() * other.value() + 2.0 * gradientDot
                        + value() * other.laplacian(),
                dimensions, directions);
        for (int axis = 0; axis < dimensions; axis++) {
            result.setGradient(axis, gradient(axis) * other.value()
                    + value() * other.gradient(axis));
        }
        for (int direction = 0; direction < directions; direction++) {
            double leftValue = directionalValue(direction);
            double rightValue = directionalValue(right, direction);
            double mixedDot = 0.0;
            for (int axis = 0; axis < dimensions; axis++) {
                double leftGradient = directionalGradient(direction, axis);
                double rightGradient = directionalGradient(right, direction, axis);
                result.setDirectionalGradient(direction, axis,
                        leftGradient * other.value()
                                + gradient(axis) * rightValue
                                + leftValue * other.gradient(axis)
                                + value() * rightGradient);
                mixedDot += leftGradient * other.gradient(axis)
                        + gradient(axis) * rightGradient;
            }
            result.setDirectionalValue(direction,
                    leftValue * other.value() + value() * rightValue);
            result.setDirectionalLaplacian(direction,
                    directionalLaplacian(direction) * other.value()
                            + laplacian() * rightValue + 2.0 * mixedDot
                            + leftValue * other.laplacian()
                            + value() * directionalLaplacian(right, direction));
        }
        return result;
    }

    FermiNetBatchedSpatialJet multiplyReference(FermiNetSpatialJet reference) {
        return multiply(reference);
    }

    @Override
    FermiNetBatchedSpatialJet reciprocal() {
        double inverse = 1.0 / value();
        return unary(inverse, -inverse * inverse,
                2.0 * inverse * inverse * inverse,
                -6.0 * inverse * inverse * inverse * inverse);
    }

    @Override FermiNetBatchedSpatialJet divide(FermiNetSpatialJet other) {
        return multiply(other.reciprocal());
    }

    @Override FermiNetBatchedSpatialJet exp() {
        double result = Math.exp(value());
        return unary(result, result, result, result);
    }

    @Override FermiNetBatchedSpatialJet sqrt() {
        double result = Math.sqrt(value());
        return unary(result, 0.5 / result,
                -0.25 / (value() * result),
                0.375 / (value() * value() * result));
    }

    @Override FermiNetBatchedSpatialJet tanh() {
        double result = Math.tanh(value());
        double first = 1.0 - result * result;
        return unary(result, first, -2.0 * result * first,
                first * (6.0 * result * result - 2.0));
    }

    static FermiNetBatchedSpatialJet affineBatched(
            FermiNetSpatialJet[] input, double[] weights,
            int offset, double bias) {
        FermiNetBatchedSpatialJet exemplar = null;
        for (FermiNetSpatialJet term : input) {
            if (term instanceof FermiNetBatchedSpatialJet batched) {
                exemplar = batched;
                break;
            }
        }
        if (exemplar == null) throw new IllegalArgumentException("missing batched input");
        FermiNetBatchedSpatialJet result = exemplar.workspace.acquire(
                bias, 0.0, exemplar.dimensions, exemplar.directions);
        for (int index = 0; index < input.length; index++) {
            FermiNetSpatialJet term = input[index];
            double weight = weights[offset + index];
            result.addPrimalValue(weight * term.value());
            result.addPrimalLaplacian(weight * term.laplacian());
            for (int axis = 0; axis < exemplar.dimensions; axis++) {
                result.addGradient(axis, weight * term.gradient(axis));
            }
            FermiNetBatchedSpatialJet batched = exemplar.batched(term);
            for (int direction = 0; direction < exemplar.directions; direction++) {
                result.addDirectionalValue(direction,
                        weight * exemplar.directionalValue(batched, direction));
                result.addDirectionalLaplacian(direction,
                        weight * exemplar.directionalLaplacian(batched, direction));
                for (int axis = 0; axis < exemplar.dimensions; axis++) {
                    result.addDirectionalGradient(direction, axis,
                            weight * exemplar.directionalGradient(
                                    batched, direction, axis));
                }
            }
        }
        return result;
    }

    private FermiNetBatchedSpatialJet unary(
            double resultValue, double first, double second, double third) {
        double gradientNorm = 0.0;
        for (int axis = 0; axis < dimensions; axis++) {
            gradientNorm += gradient(axis) * gradient(axis);
        }
        FermiNetBatchedSpatialJet result = workspace.acquire(
                resultValue, second * gradientNorm + first * laplacian(),
                dimensions, directions);
        for (int axis = 0; axis < dimensions; axis++) {
            result.setGradient(axis, first * gradient(axis));
        }
        for (int direction = 0; direction < directions; direction++) {
            double directionValue = directionalValue(direction);
            double mixedDot = 0.0;
            for (int axis = 0; axis < dimensions; axis++) {
                double directionGradient = directionalGradient(direction, axis);
                result.setDirectionalGradient(direction, axis,
                        second * directionValue * gradient(axis)
                                + first * directionGradient);
                mixedDot += gradient(axis) * directionGradient;
            }
            result.setDirectionalValue(direction, first * directionValue);
            result.setDirectionalLaplacian(direction,
                    third * directionValue * gradientNorm
                            + second * (2.0 * mixedDot
                                    + directionValue * laplacian())
                            + first * directionalLaplacian(direction));
        }
        return result;
    }

    private void copyDerivativesTo(
            FermiNetBatchedSpatialJet target, double scale) {
        for (int axis = 0; axis < dimensions; axis++) {
            target.setGradient(axis, scale * gradient(axis));
        }
        for (int direction = 0; direction < directions; direction++) {
            target.setDirectionalValue(direction,
                    scale * directionalValue(direction));
            target.setDirectionalLaplacian(direction,
                    scale * directionalLaplacian(direction));
            for (int axis = 0; axis < dimensions; axis++) {
                target.setDirectionalGradient(direction, axis,
                        scale * directionalGradient(direction, axis));
            }
        }
    }

    private FermiNetBatchedSpatialJet batched(FermiNetSpatialJet value) {
        if (value instanceof FermiNetBatchedSpatialJet batched) {
            if (batched.workspace != workspace || batched.directions != directions) {
                throw new IllegalArgumentException("batched jet workspace mismatch");
            }
            return batched;
        }
        if (value.hasDirectional()) {
            throw new IllegalArgumentException(
                    "cannot mix scalar and batched directional jets");
        }
        return null;
    }

    private double directionalValue(
            FermiNetBatchedSpatialJet value, int direction) {
        return value == null ? 0.0 : value.directionalValue(direction);
    }
    private double directionalGradient(
            FermiNetBatchedSpatialJet value, int direction, int axis) {
        return value == null ? 0.0 : value.directionalGradient(direction, axis);
    }
    private double directionalLaplacian(
            FermiNetBatchedSpatialJet value, int direction) {
        return value == null ? 0.0 : value.directionalLaplacian(direction);
    }

    private void requireDimensions(FermiNetSpatialJet other) {
        if (dimensions != other.dimensions()) {
            throw new IllegalArgumentException("spatial dimensions disagree");
        }
    }
    private void requireDirection(int direction) {
        if (direction < 0 || direction >= directions) {
            throw new IndexOutOfBoundsException(direction);
        }
    }

    private void setGradient(int axis, double value) {
        data[gradientOffset + axis] = value;
    }
    private void addGradient(int axis, double value) {
        data[gradientOffset + axis] += value;
    }
    private void setDirectionalValue(int direction, double value) {
        data[directionalValueOffset + direction] = value;
    }
    private void addDirectionalValue(int direction, double value) {
        data[directionalValueOffset + direction] += value;
    }
    private void setDirectionalGradient(int direction, int axis, double value) {
        data[directionalGradientOffset + direction * dimensions + axis] = value;
    }
    private void addDirectionalGradient(int direction, int axis, double value) {
        data[directionalGradientOffset + direction * dimensions + axis] += value;
    }
    private void setDirectionalLaplacian(int direction, double value) {
        data[directionalLaplacianOffset + direction] = value;
    }
    private void addDirectionalLaplacian(int direction, double value) {
        data[directionalLaplacianOffset + direction] += value;
    }
}
