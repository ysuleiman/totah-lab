package totah.lab.prometheus.variational;

/** State exposing the spatial and parameter derivatives required by variational solvers. */
public interface DifferentiableQuantumState extends ParameterizedQuantumState {
    @Override DifferentiableQuantumState withParameters(ParameterVector parameters);

    DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates);

    @Override default QuantumAmplitude value(QuantumCoordinates coordinates) {
        return evaluateWithDerivatives(coordinates).value();
    }

    default StateGradient coordinateGradient(QuantumCoordinates coordinates) {
        return evaluateWithDerivatives(coordinates).coordinateGradient();
    }

    default StateLaplacian coordinateLaplacian(QuantumCoordinates coordinates) {
        return evaluateWithDerivatives(coordinates).coordinateLaplacian();
    }

    default ParameterGradient parameterGradient(QuantumCoordinates coordinates) {
        return evaluateWithDerivatives(coordinates).parameterGradient();
    }
}
