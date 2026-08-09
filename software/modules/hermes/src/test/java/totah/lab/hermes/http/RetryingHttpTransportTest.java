package totah.lab.hermes.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryingHttpTransportTest {

    private static final HttpRequest REQUEST = HttpRequest.newBuilder(
            URI.create("https://example.test")).build();

    @Test
    void retriesTransientSocketFailures() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        HttpResponse<String> expected = new StubResponse<>("ok");
        HttpTransport delegate = new HttpTransport() {
            @Override
            public <T> HttpResponse<T> send(
                    HttpRequest request, HttpResponse.BodyHandler<T> handler)
                    throws IOException {
                if (attempts.incrementAndGet() < 3) {
                    throw new SocketException("connection reset");
                }
                @SuppressWarnings("unchecked")
                HttpResponse<T> response = (HttpResponse<T>) expected;
                return response;
            }
        };

        HttpResponse<String> actual = new RetryingHttpTransport(
                delegate, 3, Duration.ZERO).send(
                REQUEST, HttpResponse.BodyHandlers.ofString());

        assertSame(expected, actual);
        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryOtherIoFailures() {
        AtomicInteger attempts = new AtomicInteger();
        HttpTransport delegate = new HttpTransport() {
            @Override
            public <T> HttpResponse<T> send(
                    HttpRequest request, HttpResponse.BodyHandler<T> handler)
                    throws IOException {
                attempts.incrementAndGet();
                throw new IOException("invalid response");
            }
        };

        assertThrows(IOException.class, () -> new RetryingHttpTransport(
                delegate, 3, Duration.ZERO).send(
                REQUEST, HttpResponse.BodyHandlers.ofString()));
        assertEquals(1, attempts.get());
    }

    private record StubResponse<T>(T body) implements HttpResponse<T> {
        @Override public int statusCode() { return 200; }
        @Override public HttpRequest request() { return REQUEST; }
        @Override public java.util.Optional<HttpResponse<T>> previousResponse() {
            return java.util.Optional.empty();
        }
        @Override public java.net.http.HttpHeaders headers() {
            return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        }
        @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
            return java.util.Optional.empty();
        }
        @Override public URI uri() { return REQUEST.uri(); }
        @Override public java.net.http.HttpClient.Version version() {
            return java.net.http.HttpClient.Version.HTTP_1_1;
        }
    }
}
