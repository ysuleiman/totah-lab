package totah.lab.prometheus.completeness;

import java.util.Map;
import java.util.Objects;

/** Immutable logical manifest; artifact paths are resolved against a supplied bundle root. */
public record ScientificResultManifest(
        String resultId,
        ScientificResultType type,
        Map<String, ScientificArtifactReference> artifacts) {
    public ScientificResultManifest {
        Objects.requireNonNull(resultId, "resultId");
        Objects.requireNonNull(type, "type");
        artifacts = Map.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        if (resultId.isBlank()) throw new IllegalArgumentException("blank resultId");
    }
}
