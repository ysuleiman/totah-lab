package totah.lab.hermes.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import java.util.Properties;

/** Default remote endpoints loaded from the Hermes classpath resource. */
public final class RemoteEndpoints {

    private static final String RESOURCE = "/hermes-endpoints.properties";
    private static final Properties VALUES = load();

    private RemoteEndpoints() {
    }

    public static URI uri(String key) {
        String value = VALUES.getProperty(Objects.requireNonNull(key, "key"));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing endpoint '" + key + "' in " + RESOURCE);
        }
        try {
            return URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Invalid endpoint '" + key + "' in " + RESOURCE, exception);
        }
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input = RemoteEndpoints.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing Hermes resource " + RESOURCE);
            }
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load " + RESOURCE, exception);
        }
    }
}
