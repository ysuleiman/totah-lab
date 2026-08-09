package totah.lab.hermes.rcsb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestRcsbClientTest {

    private static final URI BASE_URI =
            URI.create("http://localhost/core/entry/");
    private static final URI SEARCH_URI =
            URI.create("http://localhost/search");
    private static final URI DOWNLOAD_URI =
            URI.create("http://localhost/download/");

    private static final String ENTRY_JSON_TEMPLATE = """
            {
              "rcsb_id": "%s",
              "struct": {"title": "Methyltransferase %s"},
              "rcsb_entry_info": {
                "experimental_method": "X-ray",
                "resolution_combined": [1.9],
                "nonpolymer_bound_components": ["SAM"],
                "polymer_entity_count": 1,
                "deposited_polymer_entity_instance_count": 2,
                "assembly_count": 1
              }
            }
            """;

    private final FakeHttpClient httpClient = new FakeHttpClient();

    private final RestRcsbClient client = new RestRcsbClient(
            httpClient, new ObjectMapper(),
            BASE_URI, SEARCH_URI, DOWNLOAD_URI, Duration.ofSeconds(5));

    @Test
    void searchEntriesFetchesASummaryPerHit() throws Exception {
        httpClient.respond(200, """
                {"result_set":[
                  {"identifier":"1ABC","score":1.0},
                  {"identifier":"2DEF","score":1.0}
                ]}
                """);
        httpClient.respond(200, ENTRY_JSON_TEMPLATE.formatted("1ABC", "1ABC"));
        httpClient.respond(200, ENTRY_JSON_TEMPLATE.formatted("2DEF", "2DEF"));

        List<RcsbEntrySummary> summaries = client.searchEntries(
                new RcsbAttributeSearch(
                        RcsbAttributeCondition.organismTaxonomy("9606"),
                        RcsbAttributeCondition.enzymeClass("2.1.1")));

        assertEquals(2, summaries.size());
        assertEquals("1ABC", summaries.get(0).pdbId());
        assertEquals("Methyltransferase 1ABC", summaries.get(0).title());
        assertEquals(List.of("SAM"), summaries.get(0).ligandComponentIds());
        assertEquals(2, summaries.get(0).chainCount());
        assertEquals("2DEF", summaries.get(1).pdbId());

        // one search POST + one entry GET per hit
        assertEquals(3, httpClient.requests.size());
        assertTrue(httpClient.requests.get(0).uri().equals(SEARCH_URI));
        assertTrue(httpClient.requests.get(1).uri().toString()
                .endsWith("/1ABC"));
        assertTrue(httpClient.requests.get(2).uri().toString()
                .endsWith("/2DEF"));
    }

    @Test
    void searchEntriesSkipsHitsWithoutEntries() throws Exception {
        httpClient.respond(200, """
                {"result_set":[{"identifier":"1ABC","score":1.0}]}
                """);
        httpClient.respond(404, "");

        assertEquals(List.of(), client.searchEntries(new RcsbAttributeSearch(
                RcsbAttributeCondition.enzymeClass("2.1.1"))));
    }

    @Test
    void fetchSummaryReturnsEmptyForUnknownEntry() throws Exception {
        httpClient.respond(404, "");

        assertEquals(Optional.empty(), client.fetchSummary("1abc"));
        assertTrue(httpClient.requests.get(0).uri().toString()
                .endsWith("/1ABC"));
    }

    private static final class FakeHttpClient extends HttpClient {

        private final List<HttpResponse<String>> responses = new ArrayList<>();
        private final List<HttpRequest> requests = new ArrayList<>();

        void respond(int status, String body) {
            responses.add(new FakeHttpResponse(status, body));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler
        ) {
            requests.add(request);
            return (HttpResponse<T>) responses.remove(0);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SSLParameters sslParameters() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    private record FakeHttpResponse(
            int statusCode,
            String body
    ) implements HttpResponse<String> {

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return BASE_URI;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public HttpRequest request() {
            throw new UnsupportedOperationException();
        }
    }
}
