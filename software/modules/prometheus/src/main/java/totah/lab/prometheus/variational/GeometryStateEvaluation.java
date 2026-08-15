package totah.lab.prometheus.variational;

import java.util.Objects;

/** One shared state graph containing spatial, parameter, and geometry derivatives. */
public record GeometryStateEvaluation(DifferentiableStateEvaluation stateEvaluation,
        double geometryLogDerivative) {
    public GeometryStateEvaluation {
        Objects.requireNonNull(stateEvaluation,"stateEvaluation");
        if(!Double.isFinite(geometryLogDerivative)) {
            throw new IllegalArgumentException("geometryLogDerivative must be finite");
        }
    }
}
