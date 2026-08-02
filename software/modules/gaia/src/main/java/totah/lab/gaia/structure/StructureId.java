package totah.lab.gaia.structure;

import java.util.Objects;

/** Opaque identity of one structure instance. */
public record StructureId(String value) {

    public StructureId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Structure ID must not be blank.");
        }
    }
}
