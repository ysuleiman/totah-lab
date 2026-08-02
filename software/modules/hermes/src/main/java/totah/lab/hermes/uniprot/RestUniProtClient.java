package totah.lab.hermes.uniprot;

import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.hermes.http.HttpClientFactory;
import totah.lab.hermes.http.HttpRequestBuilder;
import totah.lab.hermes.uniprot.internal.UniProtJsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class RestUniProtClient implements UniProtClient {

    public static final URI DEFAULT_BASE_URI =
            URI.create("https://rest.uniprot.org/uniprotkb/");
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT = "Totah-Lab-Hermes/1.0";

    private final HttpClient httpClient;
    private final UniProtJsonParser parser;
    private final URI baseUri;
    private final Duration requestTimeout;

    public RestUniProtClient() {
        this(HttpClientFactory.create(DEFAULT_TIMEOUT), new ObjectMapper(),
                DEFAULT_BASE_URI, DEFAULT_TIMEOUT);
    }

    public RestUniProtClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this(httpClient, objectMapper, DEFAULT_BASE_URI, DEFAULT_TIMEOUT);
    }

    public RestUniProtClient(HttpClient httpClient, ObjectMapper objectMapper,
                             URI baseUri, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.parser = new UniProtJsonParser(
                Objects.requireNonNull(objectMapper, "objectMapper"));
        this.baseUri = normalizeBaseUri(baseUri);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
    }

    @Override
    public Optional<UniProtEntry> fetch(String accession)
            throws UniProtException, InterruptedException {
        String normalized = normalizeAccession(accession);
        HttpRequest request = HttpRequestBuilder.forUri(requestUri(normalized))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .buildGet();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return switch (response.statusCode()) {
                case 200 -> Optional.of(parser.parse(response.body()));
                case 404 -> Optional.empty();
                default -> throw new UniProtException("UniProt request failed for accession "
                        + normalized + ": HTTP " + response.statusCode()
                        + responseMessage(response.body()));
            };
        } catch (IOException e) {
            if (e instanceof UniProtException uniProtException) {
                throw uniProtException;
            }
            throw new UniProtException(
                    "Unable to retrieve UniProt accession " + normalized, e);
        }
    }

    private URI requestUri(String accession) {
        return baseUri.resolve(URLEncoder.encode(accession, StandardCharsets.UTF_8) + ".json");
    }

    private static URI normalizeBaseUri(URI baseUri) {
        Objects.requireNonNull(baseUri, "baseUri");
        String value = baseUri.toString();
        return value.endsWith("/") ? baseUri : URI.create(value + "/");
    }

    private static String normalizeAccession(String accession) {
        Objects.requireNonNull(accession, "accession");
        String normalized = accession.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("accession must not be blank");
        }
        if (!normalized.matches("[A-Z0-9]+(?:-[0-9]+)?")) {
            throw new IllegalArgumentException("Invalid UniProt accession: " + accession);
        }
        return normalized;
    }

    private static String responseMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String normalized = body.replace('\n', ' ').replace('\r', ' ').trim();
        return ": " + normalized.substring(0, Math.min(normalized.length(), 500));
    }
}
