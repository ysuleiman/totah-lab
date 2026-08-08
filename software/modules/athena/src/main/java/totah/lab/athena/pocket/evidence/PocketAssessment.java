package totah.lab.athena.pocket.evidence;

import java.util.List;
import java.util.Objects;

/**
 * Versioned judgment derived from evidence. The evidence remains a separate
 * object and this record deliberately contains no combined score.
 */
public record PocketAssessment<V extends Enum<V>>(
        V verdict,
        List<AssessmentReason> reasons,
        String rulesetVersion
) {
    public PocketAssessment {
        Objects.requireNonNull(verdict, "verdict");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException(
                    "An assessment must carry at least one explicit reason");
        }
        Objects.requireNonNull(rulesetVersion, "rulesetVersion");
        rulesetVersion = rulesetVersion.trim();
        if (rulesetVersion.isEmpty()) {
            throw new IllegalArgumentException("rulesetVersion must not be blank");
        }
    }
}
