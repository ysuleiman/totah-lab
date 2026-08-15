package totah.lab.prometheus.variational;

import java.util.List;
import java.util.Objects;

/** Complex derivative of the state value with respect to each state parameter. */
public record ParameterGradient(List<QuantumAmplitude> derivatives) {
    public ParameterGradient {
        derivatives = List.copyOf(Objects.requireNonNull(derivatives, "derivatives"));
    }
}
