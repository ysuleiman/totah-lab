package totah.lab.hermes.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
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
}
