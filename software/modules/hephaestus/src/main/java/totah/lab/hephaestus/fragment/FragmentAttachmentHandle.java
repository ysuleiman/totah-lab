package totah.lab.hephaestus.fragment;

import java.util.Objects;
import java.util.Set;

/** A chemically legal attachment atom and the permitted ways to grow it. */
public record FragmentAttachmentHandle(
        int atomIndex,
        String element,
        int availableValence,
        boolean aromatic,
        Set<FragmentGrowthMode> permittedGrowthModes,
        String rationale
) {
    public FragmentAttachmentHandle {
        if (atomIndex < 0 || availableValence < 1) throw new IllegalArgumentException("Invalid attachment handle");
        element = requireText(element, "element");
        permittedGrowthModes = Set.copyOf(Objects.requireNonNull(permittedGrowthModes, "permittedGrowthModes"));
        if (permittedGrowthModes.isEmpty()) throw new IllegalArgumentException("At least one growth mode is required");
        rationale = requireText(rationale, "rationale");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
