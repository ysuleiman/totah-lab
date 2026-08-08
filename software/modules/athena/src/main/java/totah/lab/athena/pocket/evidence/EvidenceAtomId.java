package totah.lab.athena.pocket.evidence;

import java.util.Objects;

/** Atom identity as reported by its source. */
public record EvidenceAtomId(String name, String alternateLocation) {
    public EvidenceAtomId {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        name = name.trim();
        alternateLocation = alternateLocation == null || alternateLocation.isBlank()
                ? null : alternateLocation.trim();
    }
}
