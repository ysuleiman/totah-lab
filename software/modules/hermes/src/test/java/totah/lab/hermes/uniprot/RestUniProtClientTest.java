package totah.lab.hermes.uniprot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestUniProtClientTest {

    private static final URI BASE_URI =
            URI.create("http://localhost/uniprotkb/");
    private static final URI STREAM_URI =
            URI.create("http://localhost/uniprotkb/stream");

    private static final String TSV_HEADER =
            "Entry\tReviewed\tProtein names\tEC number\tKeywords"
                    + "\tCatalytic activity\tActive site\tBinding site"
                    + "\tCofactor\tPfam\tInterPro\tPDB";

    private static final String ENTRY_JSON = """
            {
              "primaryAccession": "Q9H8H3",
              "entryType": "UniProtKB reviewed (Swiss-Prot)",
              "proteinDescription": {"recommendedName": {"fullName": {"value": "Test protein"}}},
              "organism": {"scientificName": "Homo sapiens", "taxonId": 9606},
              "sequence": {"value": "MABC", "length": 4}
            }
            """;

    private final FakeHttpClient httpClient = new FakeHttpClient();

    private final RestUniProtClient client = new RestUniProtClient(
            httpClient,
            new ObjectMapper(),
            BASE_URI,
            STREAM_URI,
            Duration.ofSeconds(5),
            100
    );

    // ----- single-entry JSON fetch -----

    @Test
    void normalizesAccessionAndParsesJsonEntry() throws Exception {
        httpClient.respond(200, ENTRY_JSON);

        UniProtEntry entry = client.fetch(" q9h8h3 ").orElseThrow();

        assertEquals("Q9H8H3", entry.accession());
        assertEquals("Test protein", entry.proteinName());
        assertTrue(entry.reviewed());
        assertEquals(1, httpClient.requests.size());
        assertTrue(httpClient.requests.get(0).uri().toString()
                .endsWith("/Q9H8H3.json"));
    }

    @Test
    void returnsEmptyForUnknownAccession() throws Exception {
        httpClient.respond(404, "");

        assertEquals(Optional.empty(), client.fetch("Q9H8H3"));
    }

    @Test
    void failsForNonSuccessfulResponses() {
        httpClient.respond(500, "boom");

        assertThrows(
                UniProtException.class,
                () -> client.fetch("Q9H8H3")
        );
    }

    @Test
    void retriesRetryableTransportFailures() throws Exception {
        httpClient.fail(new SocketException("connection reset"));
        httpClient.respond(200, ENTRY_JSON);

        UniProtEntry entry = client.fetch("Q9H8H3").orElseThrow();

        assertEquals("Q9H8H3", entry.accession());
        assertEquals(2, httpClient.requests.size());
    }

    @Test
    void rejectsInvalidAccessionsWithoutHttp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.fetch("not an accession!")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> client.fetch("  ")
        );
        assertEquals(0, httpClient.requests.size());
    }

    @Test
    void validatesTimeoutAndChunkSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RestUniProtClient(
                        httpClient,
                        new ObjectMapper(),
                        BASE_URI,
                        STREAM_URI,
                        Duration.ZERO,
                        100
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RestUniProtClient(
                        httpClient,
                        new ObjectMapper(),
                        BASE_URI,
                        STREAM_URI,
                        Duration.ofSeconds(5),
                        0
                )
        );
    }

    // ----- bulk TSV annotations -----

    @Test
    void emptyAnnotationInputSkipsHttp() throws Exception {
        assertEquals(List.of(), client.fetchAnnotations(List.of()));

        assertEquals(0, httpClient.requests.size());
    }

    @Test
    void normalizesDeduplicatesAndFiltersAnnotationInput()
            throws Exception {
        httpClient.respond(200, TSV_HEADER + "\n");

        List<UniProtAnnotation> result = client.fetchAnnotations(
                Arrays.asList(" p11111 ", "P11111", null, "", "p22222")
        );

        assertEquals(List.of(), result);
        assertEquals(1, httpClient.requests.size());
        assertTrue(httpClient.requests.get(0).uri().toString()
                .contains("P11111+OR+P22222"));
    }

    @Test
    void chunksAnnotationRequests() throws Exception {
        httpClient.respond(200, TSV_HEADER + "\n");
        httpClient.respond(200, TSV_HEADER + "\n");

        List<String> accessions = new ArrayList<>();
        for (int index = 1; index <= 150; index++) {
            accessions.add("P" + index);
        }

        client.fetchAnnotations(accessions);

        assertEquals(2, httpClient.requests.size());
        assertEquals(
                99,
                countOccurrences(
                        httpClient.requests.get(0).uri().toString(),
                        "+OR+"
                )
        );
        assertEquals(
                49,
                countOccurrences(
                        httpClient.requests.get(1).uri().toString(),
                        "+OR+"
                )
        );
    }

    @Test
    void omitsAccessionsMissingFromTheTsvResponse() throws Exception {
        httpClient.respond(200, TSV_HEADER + "\n"
                + "P11111\treviewed\tKinase\t2.7.11.1;\tKinase"
                + "\t\t\t\t\t\t\t\n");

        List<UniProtAnnotation> result =
                client.fetchAnnotations(List.of("P11111", "P22222"));

        assertEquals(1, result.size());
        assertEquals("P11111", result.get(0).accession());
    }

    @Test
    void retriesAnnotationTransportFailures() throws Exception {
        httpClient.fail(new SocketException("connection reset"));
        httpClient.respond(200, TSV_HEADER + "\n");

        assertEquals(List.of(), client.fetchAnnotations(List.of("P1")));
        assertEquals(2, httpClient.requests.size());
    }

    @Test
    void parsesTsvColumnsIntoAnnotations() throws Exception {
        String tsv = TSV_HEADER + "\n"
                + "Q9H8H3\treviewed\tProtein-lysine methyltransferase"
                + "\t2.1.1.43;\tMethyltransferase;Transferase"
                + "\tCATALYTIC ACTIVITY: Reaction=methylation;"
                + "\tACT_SITE 20; /note=\"Proton acceptor\""
                + "\tBINDING 10; /ligand=\"S-adenosyl-L-methionine\""
                + "\tZn(2+)\tPF08241; Methyltransf_12.\tIPR029063.\t8ABC;\n"
                + "P00001\tunreviewed\tHypothetical protein"
                + "\t\t\t\t\t\t\t\t\t\n";

        List<UniProtAnnotation> annotations =
                RestUniProtClient.parseTsv(tsv);

        assertEquals(2, annotations.size());

        UniProtAnnotation first = annotations.get(0);
        assertEquals("Q9H8H3", first.accession());
        assertTrue(first.reviewed());
        assertEquals(
                "Protein-lysine methyltransferase",
                first.proteinName()
        );
        assertEquals(List.of("2.1.1.43"), first.ecNumbers());
        assertEquals(
                List.of("Methyltransferase", "Transferase"),
                first.keywords()
        );
        assertEquals(
                "BINDING 10; /ligand=\"S-adenosyl-L-methionine\"",
                first.bindingSites()
        );
        assertEquals("Zn(2+)", first.cofactors());
        assertEquals("PF08241; Methyltransf_12.", first.pfam());
        assertEquals("IPR029063.", first.interPro());
        assertEquals("8ABC;", first.pdbIds());
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.ecNumbers().add("1.1.1.1")
        );

        UniProtAnnotation second = annotations.get(1);
        assertTrue(!second.reviewed());
        assertEquals(List.of(), second.ecNumbers());
        assertEquals(List.of(), second.keywords());
        assertNull(second.catalyticActivity());
        assertNull(second.bindingSites());
        assertNull(second.pdbIds());
    }

    @Test
    void rejectsMalformedTsvRows() {
        assertThrows(
                UniProtException.class,
                () -> RestUniProtClient.parseTsv(
                        TSV_HEADER + "\nQ9H8H3\treviewed\n"
                )
        );
    }

    // ----- query search -----

    @Test
    void searchPassesQueryThroughAndParsesRows() throws Exception {
        httpClient.respond(200, TSV_HEADER + "\n"
                + "Q9H8H3\treviewed\tProtein-lysine methyltransferase"
                + "\t2.1.1.43;\tMethyltransferase\t\t\t\t\t\t\t8ABC;\n");

        List<UniProtAnnotation> result = client.search(
                "protein_name:methyltransferase AND organism_id:9606"
                        + " AND reviewed:true"
        );

        assertEquals(1, result.size());
        assertEquals("Q9H8H3", result.get(0).accession());
        assertEquals("8ABC;", result.get(0).pdbIds());

        assertEquals(1, httpClient.requests.size());
        String uri = httpClient.requests.get(0).uri().toString();
        assertTrue(uri.startsWith(STREAM_URI + "?query="));
        assertTrue(uri.contains(
                "protein_name%3Amethyltransferase+AND+organism_id%3A9606"
                        + "+AND+reviewed%3Atrue"
        ));
        assertTrue(uri.contains("format=tsv"));
    }

    @Test
    void searchRejectsBlankQueryWithoutHttp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.search("  ")
        );
        assertEquals(0, httpClient.requests.size());
    }

    @Test
    void searchFailsForNonSuccessfulResponses() {
        httpClient.respond(400, "Invalid query");

        assertThrows(
                UniProtException.class,
                () -> client.search("not:a:valid:query")
        );
    }

    @Test
    void searchRetriesTransportFailures() throws Exception {
        httpClient.fail(new SocketException("connection reset"));
        httpClient.respond(200, TSV_HEADER + "\n");

        assertEquals(List.of(), client.search("organism_id:9606"));
        assertEquals(2, httpClient.requests.size());
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int from = 0;

        while ((from = value.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }

        return count;
    }

    private static final class FakeHttpClient extends HttpClient {

        private final List<Object> outcomes = new ArrayList<>();
        private final List<HttpRequest> requests = new ArrayList<>();

        void respond(int status, String body) {
            outcomes.add(new FakeHttpResponse(status, body));
        }

        void fail(IOException exception) {
            outcomes.add(exception);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler
        ) throws IOException {
            requests.add(request);

            Object outcome = outcomes.remove(0);

            if (outcome instanceof IOException exception) {
                throw exception;
            }

            return (HttpResponse<T>) outcome;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
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
            return HttpHeaders.of(
                    java.util.Map.of(),
                    (first, second) -> true
            );
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
