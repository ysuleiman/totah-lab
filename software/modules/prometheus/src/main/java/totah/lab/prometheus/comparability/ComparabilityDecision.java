package totah.lab.prometheus.comparability;

import java.util.Objects;

/**
 * Outcome of a comparability check. Following the Athena convention, every
 * verdict carries a non-blank human-readable reason.
 */
public record ComparabilityDecision(
        ComparabilityVerdict verdict,
        String reason) {

    public ComparabilityDecision {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must be non-blank");
        }
    }
}
