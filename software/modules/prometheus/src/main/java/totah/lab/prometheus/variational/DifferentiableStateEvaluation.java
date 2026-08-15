package totah.lab.prometheus.variational;

import java.util.Objects;

/** Value and all derivatives produced from one shared forward evaluation graph. */
public record DifferentiableStateEvaluation(
        QuantumAmplitude value,
        StateGradient coordinateGradient,
        StateLaplacian coordinateLaplacian,
        ParameterGradient parameterGradient,
        String evaluationIdentity) {
    public DifferentiableStateEvaluation {
        Objects.requireNonNull(value, "value"); Objects.requireNonNull(coordinateGradient, "coordinateGradient");
        Objects.requireNonNull(coordinateLaplacian, "coordinateLaplacian");
        Objects.requireNonNull(parameterGradient, "parameterGradient");
        Objects.requireNonNull(evaluationIdentity, "evaluationIdentity");
        if (!evaluationIdentity.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("evaluationIdentity must be lowercase SHA-256");
        }
    }
}
