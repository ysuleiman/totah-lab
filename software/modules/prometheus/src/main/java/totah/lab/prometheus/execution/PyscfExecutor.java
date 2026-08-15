package totah.lab.prometheus.execution;

import java.util.Locale;
import java.util.Objects;

import totah.lab.prometheus.planning.CalculationSpecification;

/**
 * Skeleton PySCF executor. Supports specifications whose protocol software
 * starts with "PySCF" (case-insensitive).
 *
 * <p>Explicit unsupported state: no calculation logic is implemented and no
 * installation is probed. {@link #execute} always throws
 * {@link EvidenceExecutionException}; running anything requires explicit
 * authorization and a configured engine.
 */
public final class PyscfExecutor implements EvidenceExecutor {

    private final String configuredPath;

    /** @param configuredPath optional engine path; may be null (not configured) */
    public PyscfExecutor(String configuredPath) {
        this.configuredPath = configuredPath;
    }

    @Override
    public String executorId() {
        return "pyscf";
    }

    @Override
    public boolean supports(CalculationSpecification spec) {
        Objects.requireNonNull(spec, "spec");
        return spec.protocol().software().toLowerCase(Locale.ROOT).startsWith("pyscf");
    }

    @Override
    public RawCalculationResult execute(CalculationSpecification spec) throws EvidenceExecutionException {
        Objects.requireNonNull(spec, "spec");
        throw new EvidenceExecutionException(
                "PySCF is not installed/configured in this environment; execution requires"
                        + " explicit authorization and a configured engine"
                        + (configuredPath == null ? "" : " (configured path: " + configuredPath + ")"));
    }
}
