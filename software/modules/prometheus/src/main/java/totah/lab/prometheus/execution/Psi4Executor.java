package totah.lab.prometheus.execution;

import java.util.Locale;
import java.util.Objects;

import totah.lab.prometheus.planning.CalculationSpecification;

/**
 * Skeleton Psi4 executor. Supports specifications whose protocol software
 * starts with "Psi4" (case-insensitive).
 *
 * <p>Explicit unsupported state: no calculation logic is implemented and no
 * installation is probed. {@link #execute} always throws
 * {@link EvidenceExecutionException}; running anything requires explicit
 * authorization and a configured engine.
 */
public final class Psi4Executor implements EvidenceExecutor {

    private final String configuredPath;

    /** @param configuredPath optional engine path; may be null (not configured) */
    public Psi4Executor(String configuredPath) {
        this.configuredPath = configuredPath;
    }

    @Override
    public String executorId() {
        return "psi4";
    }

    @Override
    public boolean supports(CalculationSpecification spec) {
        Objects.requireNonNull(spec, "spec");
        return spec.protocol().software().toLowerCase(Locale.ROOT).startsWith("psi4");
    }

    @Override
    public RawCalculationResult execute(CalculationSpecification spec) throws EvidenceExecutionException {
        Objects.requireNonNull(spec, "spec");
        throw new EvidenceExecutionException(
                "Psi4 is not installed/configured in this environment; execution requires"
                        + " explicit authorization and a configured engine"
                        + (configuredPath == null ? "" : " (configured path: " + configuredPath + ")"));
    }
}
