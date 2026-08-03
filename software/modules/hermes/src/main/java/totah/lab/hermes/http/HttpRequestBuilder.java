package totah.lab.hermes.http;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Small reusable builder for the common HTTP request configuration. */
public final class HttpRequestBuilder {

    private final URI uri;
    private Duration timeout;
    private final Map<String, String> headers = new LinkedHashMap<>();

    private HttpRequestBuilder(URI uri) {
        this.uri = Objects.requireNonNull(uri, "uri");
    }

    public static HttpRequestBuilder forUri(URI uri) {
        return new HttpRequestBuilder(uri);
    }

    public HttpRequestBuilder timeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    public HttpRequestBuilder header(String name, String value) {
        headers.put(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(value, "value")
        );
        return this;
    }

    public HttpRequest buildGet() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();
        configure(builder);
        return builder.build();
    }

    public HttpRequest buildPost(HttpRequest.BodyPublisher body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .POST(Objects.requireNonNull(body, "body"));
        configure(builder);
        return builder.build();
    }

    private void configure(HttpRequest.Builder builder) {
        if (timeout != null) {
            builder.timeout(timeout);
        }
        headers.forEach(builder::header);
    }
}
