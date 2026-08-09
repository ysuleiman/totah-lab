package totah.lab.hephaestus.receptor.assembly;

import java.util.Locale;
import java.util.Objects;

/** A prepared ligand pose retained as a rigid receptor component. */
public record FixedCofactor(
        String id,
        String componentCode,
        LigandPose pose) {

    public FixedCofactor {
        id = requireNonBlank(id, "id");
        componentCode = requireNonBlank(
                componentCode,
                "componentCode").toUpperCase(Locale.ROOT);
        Objects.requireNonNull(pose, "pose");
    }

    private static String requireNonBlank(
            String value,
            String fieldName) {

        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }
        return normalized;
    }
}
