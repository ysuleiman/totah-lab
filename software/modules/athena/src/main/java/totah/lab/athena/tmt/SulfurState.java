package totah.lab.athena.tmt;

import java.util.Objects;

/** One explicit chemical species of a substrate, rather than a protonation annotation. */
public record SulfurState(String substrateId, SulfurSpecies species, String stateId) {
    public SulfurState {
        substrateId = requireText(substrateId, "substrateId");
        species = Objects.requireNonNull(species, "species");
        stateId = requireText(stateId, "stateId");
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
