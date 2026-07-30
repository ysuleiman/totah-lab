package totah.lab.report.narrative;

import java.time.Instant;
import java.util.Objects;

public record NarrativeProvenance(
        String provider,
        String model,
        Instant generatedAt,
        String evidenceDigest
) {
    public NarrativeProvenance {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(evidenceDigest, "evidenceDigest");
    }
}
