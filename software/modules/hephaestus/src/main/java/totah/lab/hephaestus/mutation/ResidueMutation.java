package totah.lab.hephaestus.mutation;

import totah.lab.gaia.structure.ResidueId;

import java.util.Locale;
import java.util.Objects;

public record ResidueMutation(
        ResidueId target,
        String expectedResidueName,
        String replacementResidueName) {

    public ResidueMutation {
        Objects.requireNonNull(target, "target");
        expectedResidueName = residueName(expectedResidueName, "expectedResidueName");
        replacementResidueName = residueName(replacementResidueName, "replacementResidueName");
        if (expectedResidueName.equals(replacementResidueName)) {
            throw new IllegalArgumentException("Mutation must change residue identity");
        }
    }

    private static String residueName(String value, String field) {
        String normalized = Objects.requireNonNull(value, field)
                .trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException(field + " must be a three-letter residue name");
        }
        return normalized;
    }
}
