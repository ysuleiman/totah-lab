package totah.lab.hermes.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestBuilderTest {

    @Test
    void buildsConfiguredGetRequest() {
        var request = HttpRequestBuilder.forUri(URI.create("https://example.test/value"))
                .timeout(Duration.ofSeconds(3))
                .header("Accept", "application/json")
                .buildGet();

        assertEquals("GET", request.method());
        assertEquals(Duration.ofSeconds(3), request.timeout().orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
    }

    @Test
    void buildsPostRequestsWithSharedConfiguration() {
        var request = HttpRequestBuilder.forUri(URI.create("https://example.test/search"))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json")
                .buildPost(HttpRequest.BodyPublishers.ofString("{}"));

        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers()
                .firstValue("Content-Type").orElseThrow());
        assertEquals(Duration.ofSeconds(4), request.timeout().orElseThrow());
    }
}
