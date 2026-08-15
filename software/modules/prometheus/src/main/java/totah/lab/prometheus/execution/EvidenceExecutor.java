package totah.lab.prometheus.execution;

import totah.lab.prometheus.planning.CalculationSpecification;

/**
 * Boundary between Prometheus and a scientific calculation engine.
 *
 * <p>Contract:
 * <ul>
 *   <li>The executor receives a FROZEN {@link CalculationSpecification}; it
 *       MUST return the identical instance in
 *       {@link RawCalculationResult#specification()} and MUST NOT alter the
 *       specification or any copy of its scientific content.</li>
 *   <li>The executor performs the scientific calculation only — validation,
 *       acceptance and registration happen in Prometheus afterward.</li>
 *   <li>The executor must never silently substitute method, basis or software:
 *       if it cannot run exactly what the specification says, it throws
 *       {@link EvidenceExecutionException}.</li>
 *   <li>No expensive calculation launches without explicit external
 *       authorization; implementing {@link EvidenceExecutor} is not an
 *       authorization to run.</li>
 * </ul>
 */
public interface EvidenceExecutor {

    /** Stable identifier of this executor, e.g. "pyscf". */
    String executorId();

    /** True when this executor can run exactly the given specification. */
    boolean supports(CalculationSpecification spec);

    /**
     * Runs the specified calculation and returns its raw, unvalidated result.
     *
     * @throws EvidenceExecutionException when the engine is not available or
     *         the calculation cannot be performed exactly as specified
     */
    RawCalculationResult execute(CalculationSpecification spec) throws EvidenceExecutionException;
}
