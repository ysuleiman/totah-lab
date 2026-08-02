package totah.lab.gaia.pocket;

import java.util.Objects;

public record PocketId(String value) {
    public PocketId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Pocket ID must not be blank.");
        }
    }

    public static PocketId of(long value) {
        return new PocketId(Long.toString(value));
    }
}
