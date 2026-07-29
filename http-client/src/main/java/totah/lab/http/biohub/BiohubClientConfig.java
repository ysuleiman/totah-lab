package totah.lab.http.biohub;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record BiohubClientConfig(
        URI baseUri,
        String apiToken,
        String esmcModel,
        Duration requestTimeout
) {

    public BiohubClientConfig {
        baseUri = Objects.requireNonNull(baseUri, "baseUri");
        apiToken = requireText(apiToken, "apiToken");
        esmcModel = requireText(esmcModel, "esmcModel");
        requestTimeout = Objects.requireNonNull(
                requestTimeout,
                "requestTimeout"
        );
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "requestTimeout must be positive"
            );
        }
    }

    public static BiohubClientConfig fromEnvironment() {
        String token = System.getenv("BIOHUB_API_TOKEN");
        if (token == null || token.isBlank()) {
            token = System.getenv("FORGE_API_TOKEN");
        }
        if (token == null || token.isBlank()) {
            token = System.getenv("ESM_API_KEY");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "BIOHUB_API_TOKEN, FORGE_API_TOKEN, or ESM_API_KEY "
                            + "must be configured"
            );
        }
        return new BiohubClientConfig(
                URI.create("https://biohub.ai"),
                token,
                "esmc-300m-2024-12",
                Duration.ofMinutes(3)
        );
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
