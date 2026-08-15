package totah.lab.prometheus.validation;

import java.util.Arrays;

import totah.lab.prometheus.numerics.SecondOrderJet;

/** Spatial second-order jet carrying exact first tangents for model parameters. */
final class MixedParameterSpatialJet {
    private final SecondOrderJet primal;
    private final SecondOrderJet[] tangent;

    private MixedParameterSpatialJet(SecondOrderJet primal, SecondOrderJet[] tangent) {
        this.primal = primal;
        this.tangent = tangent;
    }

    static MixedParameterSpatialJet constant(double value, int spatialDimensions, int parameters) {
        return new MixedParameterSpatialJet(SecondOrderJet.constant(value, spatialDimensions), zeros(spatialDimensions, parameters));
    }

    static MixedParameterSpatialJet coordinate(double value, int spatialDimensions, int parameters, int axis) {
        return new MixedParameterSpatialJet(SecondOrderJet.variable(value, spatialDimensions, axis), zeros(spatialDimensions, parameters));
    }

    static MixedParameterSpatialJet parameter(double value, int spatialDimensions, int parameters, int index) {
        SecondOrderJet[] tangent = zeros(spatialDimensions, parameters);
        tangent[index] = SecondOrderJet.constant(1.0, spatialDimensions);
        return new MixedParameterSpatialJet(SecondOrderJet.constant(value, spatialDimensions), tangent);
    }

    double value() { return primal.value(); }
    double gradient(int axis) { return primal.gradient(axis); }
    double laplacian(int axes) { return primal.laplacian(axes); }
    double parameterDerivative(int parameter) { return tangent[parameter].value(); }
    double laplacianParameterDerivative(int parameter, int axes) { return tangent[parameter].laplacian(axes); }

    MixedParameterSpatialJet add(MixedParameterSpatialJet other) {
        requireSameSize(other); SecondOrderJet[] result = new SecondOrderJet[tangent.length];
        for (int i = 0; i < result.length; i++) result[i] = tangent[i].add(other.tangent[i]);
        return new MixedParameterSpatialJet(primal.add(other.primal), result);
    }

    MixedParameterSpatialJet add(double value) {
        return new MixedParameterSpatialJet(primal.add(value), tangent.clone());
    }

    MixedParameterSpatialJet subtract(MixedParameterSpatialJet other) {
        requireSameSize(other); SecondOrderJet[] result = new SecondOrderJet[tangent.length];
        for (int i = 0; i < result.length; i++) result[i] = tangent[i].subtract(other.tangent[i]);
        return new MixedParameterSpatialJet(primal.subtract(other.primal), result);
    }

    MixedParameterSpatialJet multiply(MixedParameterSpatialJet other) {
        requireSameSize(other); SecondOrderJet[] result = new SecondOrderJet[tangent.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = tangent[i].multiply(other.primal).add(primal.multiply(other.tangent[i]));
        }
        return new MixedParameterSpatialJet(primal.multiply(other.primal), result);
    }

    MixedParameterSpatialJet multiply(double value) {
        SecondOrderJet[] result = Arrays.stream(tangent).map(item -> item.multiply(value)).toArray(SecondOrderJet[]::new);
        return new MixedParameterSpatialJet(primal.multiply(value), result);
    }

    MixedParameterSpatialJet divide(MixedParameterSpatialJet other) { return multiply(other.reciprocal()); }
    MixedParameterSpatialJet reciprocal() { return unary(primal.reciprocal(), primal.reciprocal().multiply(primal.reciprocal()).multiply(-1)); }
    MixedParameterSpatialJet exp() { SecondOrderJet result = primal.exp(); return unary(result, result); }
    MixedParameterSpatialJet sqrt() { return unary(primal.sqrt(), primal.sqrt().reciprocal().multiply(.5)); }
    MixedParameterSpatialJet tanh() { SecondOrderJet result = primal.tanh(); return unary(result, SecondOrderJet.constant(1, dimensions()).subtract(result.multiply(result))); }

    private MixedParameterSpatialJet unary(SecondOrderJet result, SecondOrderJet derivative) {
        return new MixedParameterSpatialJet(result, Arrays.stream(tangent).map(item -> derivative.multiply(item)).toArray(SecondOrderJet[]::new));
    }

    private int dimensions() { return tangent.length == 0 ? 0 : inferDimensions(); }
    private int inferDimensions() {
        int dimensions = 0;
        while (true) {
            try { primal.gradient(dimensions); dimensions++; }
            catch (ArrayIndexOutOfBoundsException exception) { return dimensions; }
        }
    }
    private void requireSameSize(MixedParameterSpatialJet other) {
        if (tangent.length != other.tangent.length) throw new IllegalArgumentException("parameter dimensions disagree");
    }
    private static SecondOrderJet[] zeros(int spatialDimensions, int parameters) {
        SecondOrderJet[] result = new SecondOrderJet[parameters];
        Arrays.setAll(result, ignored -> SecondOrderJet.constant(0, spatialDimensions));
        return result;
    }
}
