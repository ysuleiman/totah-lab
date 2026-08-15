package totah.lab.prometheus.neural;

import java.util.List;
import java.util.Objects;

/** Scalar network output and derivatives from one shared forward/reverse evaluation. */
public record NetworkEvaluation(double value, double inputFirstDerivative,
        double inputSecondDerivative, List<Double> parameterGradient) {
    public NetworkEvaluation {
        parameterGradient = List.copyOf(Objects.requireNonNull(parameterGradient, "parameterGradient"));
    }
}
