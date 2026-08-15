package totah.lab.prometheus.variational;

/** Differentiable state with an explicit fixed-H2-bond-length response. */
public interface GeometryDifferentiableQuantumState extends DifferentiableQuantumState {
    double geometryCoordinateBohr();

    GeometryDifferentiableQuantumState atGeometry(double geometryCoordinateBohr);

    GeometryStateEvaluation evaluateWithGeometryDerivatives(QuantumCoordinates coordinates);

    @Override default DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates) {
        return evaluateWithGeometryDerivatives(coordinates).stateEvaluation();
    }
}
