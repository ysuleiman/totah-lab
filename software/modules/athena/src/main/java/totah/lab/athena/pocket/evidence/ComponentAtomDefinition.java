package totah.lab.athena.pocket.evidence;

import java.util.Objects;

/** CCD-reported chemical definition of one component atom. */
public record ComponentAtomDefinition(
        String atomId,
        String element,
        int formalCharge,
        boolean aromatic
) {
    public ComponentAtomDefinition {
        atomId = requireText(atomId, "atomId");
        element = requireText(element, "element");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
