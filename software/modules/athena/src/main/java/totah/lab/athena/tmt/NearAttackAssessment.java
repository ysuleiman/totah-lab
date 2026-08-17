package totah.lab.athena.tmt;

import java.util.Objects;

/** Separate geometry and chemistry evidence; no combined score is produced. */
public record NearAttackAssessment(
        NearAttackClassification classification,
        boolean geometryWithinCandidateRange,
        boolean clashCompatible,
        boolean sulfurStateEvaluated,
        boolean protonNetworkEvaluated,
        String reason,
        String thresholdProvenance) {
    public NearAttackAssessment {
        classification = Objects.requireNonNull(classification, "classification");
        reason = requireText(reason, "reason");
        thresholdProvenance = requireText(thresholdProvenance, "thresholdProvenance");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
