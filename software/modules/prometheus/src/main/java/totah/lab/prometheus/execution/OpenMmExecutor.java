package totah.lab.prometheus.execution;

import java.util.Locale;
import java.util.Objects;

import totah.lab.prometheus.planning.CalculationSpecification;

/**
 * Skeleton OpenMM executor. Supports specifications whose protocol software
 * starts with "OpenMM" (case-insensitive).
 *
 * <p>Explicit unsupported state: no calculation logic is implemented and no
 * installation is probed. {@link #execute} always throws
 * {@link EvidenceExecutionException}; running anything requires explicit
 * authorization and a configured engine.
 */
public final class OpenMmExecutor implements EvidenceExecutor {

    private final String configuredPath;

    /** @param configuredPath optional engine path; may be null (not configured) */
    public OpenMmExecutor(String configuredPath) {
        this.configuredPath = configuredPath;
    }

    @Override
    public String executorId() {
        return "openmm";
    }

    @Override
    public boolean supports(CalculationSpecification spec) {
        Objects.requireNonNull(spec, "spec");
        return spec.protocol().software().toLowerCase(Locale.ROOT).startsWith("openmm");
    }

    @Override
    public RawCalculationResult execute(CalculationSpecification spec) throws EvidenceExecutionException {
        Objects.requireNonNull(spec, "spec");
        throw new EvidenceExecutionException(
                "OpenMM is not installed/configured in this environment; execution requires"
                        + " explicit authorization and a configured engine"
                        + (configuredPath == null ? "" : " (configured path: " + configuredPath + ")"));
    }
}
