package totah.lab.hephaestus.model;

import java.util.Set;

/** Explicit reasons that prepared state became invalid. */
public record StructureChange(Set<StructureChangeType> types, String description) {
    public StructureChange {
        types = Set.copyOf(types);
        if (types.isEmpty()) {
            throw new IllegalArgumentException("At least one structure change type is required");
        }
        description = description == null ? "" : description.trim();
    }

    public static StructureChange of(StructureChangeType type) {
        return new StructureChange(Set.of(type), "");
    }
}
