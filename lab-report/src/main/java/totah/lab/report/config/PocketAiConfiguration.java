package totah.lab.report.config;

import java.util.Objects;

public record PocketAiConfiguration(
        boolean enabled,
        String provider,
        String model
) {
    public PocketAiConfiguration {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
    }

    public static PocketAiConfiguration disabled() {
        return new PocketAiConfiguration(false, "none", "none");
    }
}
