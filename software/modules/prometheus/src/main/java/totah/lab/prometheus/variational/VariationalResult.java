package totah.lab.prometheus.variational;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable result; convergence and failed gates remain explicit evidence. */
public record VariationalResult(
        String scientificIdentity,
        ParameterVector parameters,
        double objective,
        boolean converged,
        List<String> passedGates,
        List<String> failedGates,
        Map<String, String> provenance) {
    public VariationalResult {
        Objects.requireNonNull(scientificIdentity, "scientificIdentity");
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(objective)) throw new IllegalArgumentException("objective must be finite");
        passedGates = List.copyOf(Objects.requireNonNull(passedGates, "passedGates"));
        failedGates = List.copyOf(Objects.requireNonNull(failedGates, "failedGates"));
        provenance = Map.copyOf(Objects.requireNonNull(provenance, "provenance"));
        if (converged && !failedGates.isEmpty()) {
            throw new IllegalArgumentException("a converged result cannot have failed gates");
        }
    }
}
