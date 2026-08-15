package totah.lab.prometheus.execution;

import java.util.Locale;
import java.util.Objects;

import totah.lab.prometheus.planning.CalculationSpecification;

/**
 * Skeleton AmberTools executor. Supports specifications whose protocol software
 * starts with "AmberTools" or "resp" (case-insensitive).
 *
 * <p>Explicit unsupported state: no calculation logic is implemented and no
 * installation is probed. {@link #execute} always throws
 * {@link EvidenceExecutionException}; running anything requires explicit
 * authorization and a configured engine.
 */
public final class AmberToolsExecutor implements EvidenceExecutor {

    private final String configuredPath;

    /** @param configuredPath optional engine path; may be null (not configured) */
    public AmberToolsExecutor(String configuredPath) {
        this.configuredPath = configuredPath;
    }

    @Override
    public String executorId() {
        return "ambertools";
    }

    @Override
    public boolean supports(CalculationSpecification spec) {
        Objects.requireNonNull(spec, "spec");
        String software = spec.protocol().software().toLowerCase(Locale.ROOT);
        return software.startsWith("ambertools") || software.startsWith("resp");
    }

    @Override
    public RawCalculationResult execute(CalculationSpecification spec) throws EvidenceExecutionException {
        Objects.requireNonNull(spec, "spec");
        throw new EvidenceExecutionException(
                "AmberTools is not installed/configured in this environment; execution requires"
                        + " explicit authorization and a configured engine"
                        + (configuredPath == null ? "" : " (configured path: " + configuredPath + ")"));
    }
}
