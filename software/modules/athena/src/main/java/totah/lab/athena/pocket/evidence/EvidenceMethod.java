package totah.lab.athena.pocket.evidence;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Reproducible identity and parameters of a source or derivation method. */
public record EvidenceMethod(
        String name,
        String version,
        Map<String, String> parameters
) {
    public EvidenceMethod {
        name = requireText(name, "name");
        version = requireText(version, "version");
        parameters = Map.copyOf(new TreeMap<>(
                Objects.requireNonNull(parameters, "parameters")));
    }

    public EvidenceMethod(String name, String version) {
        this(name, version, Map.of());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
