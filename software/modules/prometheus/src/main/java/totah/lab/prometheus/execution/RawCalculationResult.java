package totah.lab.prometheus.execution;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.planning.CalculationSpecification;

/**
 * The unvalidated result of one executed calculation: the raw artifacts, the
 * executor's convergence report, and the executor's note.
 *
 * <p>{@code specification} MUST be the SAME frozen
 * {@link CalculationSpecification} instance the executor received — executors
 * never alter the scientific specification. Validation, acceptance and
 * registration happen in Prometheus afterward, not in the executor.
 */
public record RawCalculationResult(
        CalculationSpecification specification,
        List<RawArtifact> artifacts,
        ConvergenceStatus convergence,
        String executorNote) {

    public RawCalculationResult {
        Objects.requireNonNull(specification, "specification");
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        Objects.requireNonNull(convergence, "convergence");
        Objects.requireNonNull(executorNote, "executorNote");
    }
}
