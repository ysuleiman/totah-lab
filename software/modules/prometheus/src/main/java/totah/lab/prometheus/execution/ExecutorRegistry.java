package totah.lab.prometheus.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.planning.CalculationSpecification;

/**
 * Routes a {@link CalculationSpecification} to the registered executor that
 * supports it. Registration order decides when several executors claim the
 * same specification.
 */
public final class ExecutorRegistry {

    private final List<EvidenceExecutor> executors = new ArrayList<>();

    public void register(EvidenceExecutor executor) {
        executors.add(Objects.requireNonNull(executor, "executor"));
    }

    /**
     * Returns the first registered executor whose {@code supports(spec)} is true.
     *
     * @throws EvidenceExecutionException when no registered executor supports the
     *         specification; the reason names the specification's software
     */
    public EvidenceExecutor route(CalculationSpecification spec) throws EvidenceExecutionException {
        Objects.requireNonNull(spec, "spec");
        for (EvidenceExecutor executor : executors) {
            if (executor.supports(spec)) {
                return executor;
            }
        }
        throw new EvidenceExecutionException(
                "no registered executor supports software '" + spec.protocol().software()
                        + "' required by specification " + spec.specificationId());
    }
}
