package totah.lab.athena.pocket.evidence;

import java.util.Objects;

/** Stable identity of one bound component occurrence. */
public record LigandOccurrenceId(
        String pdbId,
        String assemblyId,
        int modelNumber,
        String chainId,
        String componentId,
        String residueId,
        String insertionCode,
        String alternateLocation
) {
    public LigandOccurrenceId {
        pdbId = requireText(pdbId, "pdbId");
        chainId = requireText(chainId, "chainId");
        componentId = requireText(componentId, "componentId");
        residueId = requireText(residueId, "residueId");
        if (modelNumber < 1) {
            throw new IllegalArgumentException("modelNumber must be positive");
        }
        assemblyId = optionalText(assemblyId);
        insertionCode = optionalText(insertionCode);
        alternateLocation = optionalText(alternateLocation);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
