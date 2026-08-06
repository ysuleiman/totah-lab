package totah.lab.athena.pocket.evidence;

import java.util.Objects;

/**
 * The verdict of a pocket comparison together with the reason the
 * matched rule fired: which dimensions passed or failed, with their
 * values. The verdict alone says WHAT the rules concluded; the reason
 * says WHY, so a report reader can audit the decision without
 * re-running the rules.
 *
 * @param verdict the classification (one of the six
 *                {@link PocketComparisonAssessment} values)
 * @param reason  human-readable explanation naming the deciding
 *                dimensions and their values
 */
public record PocketAssessmentVerdict(
        PocketComparisonAssessment verdict,
        String reason
) {

    public PocketAssessmentVerdict {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reason, "reason");

        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "reason must not be blank"
            );
        }
    }
}
