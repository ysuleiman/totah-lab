package totah.lab.report.model;

import java.util.List;
import java.util.Objects;

public record NarrativeFinding(
        String statement,
        FindingType type,
        FindingConfidence confidence,
        List<String> evidenceIds
) {
    public NarrativeFinding {
        Objects.requireNonNull(statement, "statement");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(confidence, "confidence");
        evidenceIds = List.copyOf(evidenceIds);
    }

    public enum FindingType {
        OBSERVATION,
        INTERPRETATION,
        LIMITATION,
        RECOMMENDATION
    }

    public enum FindingConfidence {
        LOW,
        MODERATE,
        HIGH
    }
}
