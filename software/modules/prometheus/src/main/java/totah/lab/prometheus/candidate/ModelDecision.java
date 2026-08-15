package totah.lab.prometheus.candidate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An explicit decision about a model. Following the Athena convention, every
 * decision carries at least one explicit reason.
 */
public record ModelDecision(
        DecisionState state,
        List<String> reasons,
        Instant decidedAt) {

    public ModelDecision {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reasons, "reasons");
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("at least one reason is required");
        }
        for (String reason : reasons) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reasons must be non-blank");
            }
        }
        reasons = List.copyOf(reasons);
        Objects.requireNonNull(decidedAt, "decidedAt");
    }
}
