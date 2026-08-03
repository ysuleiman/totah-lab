package totah.lab.hermes.uniprot;

import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.hermes.http.HttpClientFactory;
import totah.lab.hermes.http.HttpRequestBuilder;
import totah.lab.hermes.uniprot.internal.UniProtJsonParser;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
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

    public static final Duration DEFAULT_TIMEOUT =
            Duration.ofSeconds(30);

    private static final String USER_AGENT =
            "Totah-Lab-Hermes/1.0";

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MILLIS = 250L;

    private final HttpClient httpClient;
    private final UniProtJsonParser parser;
    private final URI baseUri;
    private final Duration requestTimeout;

    public RestUniProtClient() {
        this(
                HttpClientFactory.create(DEFAULT_TIMEOUT),
                new ObjectMapper(),
                DEFAULT_BASE_URI,
                DEFAULT_TIMEOUT
        );
    }

    public RestUniProtClient(
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this(
                httpClient,
                objectMapper,
                DEFAULT_BASE_URI,
                DEFAULT_TIMEOUT
        );
    }

    public RestUniProtClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI baseUri,
            Duration requestTimeout
    ) {
        this.httpClient =
                Objects.requireNonNull(httpClient, "httpClient");

        this.parser = new UniProtJsonParser(
                Objects.requireNonNull(objectMapper, "objectMapper")
        );

        this.baseUri = normalizeBaseUri(baseUri);

        this.requestTimeout =
                Objects.requireNonNull(requestTimeout, "requestTimeout");

        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "requestTimeout must be positive"
            );
        }
    }

    @Override
    public Optional<UniProtEntry> fetch(String accession)
            throws UniProtException, InterruptedException {

        String normalized = normalizeAccession(accession);

        HttpRequest request = HttpRequestBuilder.forUri(
                        requestUri(normalized)
                )
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .buildGet();

        final HttpResponse<String> response;

        try {
            response = sendWithRetry(request);
        } catch (IOException e) {
            throw new UniProtException(
                    "Unable to retrieve UniProt accession " + normalized,
                    e
            );
        }

        return switch (response.statusCode()) {
            case 200 -> Optional.of(parser.parse(response.body()));
            case 404 -> Optional.empty();
            default -> throw new UniProtException(
                    "UniProt request failed for accession "
                            + normalized
                            + ": HTTP "
                            + response.statusCode()
                            + responseMessage(response.body())
            );
        };
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request)
            throws IOException, InterruptedException {

        IOException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                );
            } catch (IOException e) {
                if (!isRetryableTransportFailure(e)) {
                    throw e;
                }

                lastFailure = e;

                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }

        throw Objects.requireNonNull(
                lastFailure,
                "Retry loop completed without a recorded failure"
        );
    }

    private static void sleepBeforeRetry(int attempt)
            throws InterruptedException {

        long delayMillis =
                INITIAL_RETRY_DELAY_MILLIS * attempt;

        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private static boolean isRetryableTransportFailure(IOException exception) {
        if (exception instanceof EOFException
                || exception instanceof SocketException) {
            return true;
        }

        Throwable cause = exception.getCause();

        while (cause != null) {
            if (cause instanceof EOFException
                    || cause instanceof SocketException) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    private URI requestUri(String accession) {
        return baseUri.resolve(
                URLEncoder.encode(
                        accession,
                        StandardCharsets.UTF_8
                ) + ".json"
        );
    }

    private static URI normalizeBaseUri(URI baseUri) {
        Objects.requireNonNull(baseUri, "baseUri");

        String value = baseUri.toString();

        return value.endsWith("/")
                ? baseUri
                : URI.create(value + "/");
    }

    private static String normalizeAccession(String accession) {
        Objects.requireNonNull(accession, "accession");

        String normalized =
                accession.trim().toUpperCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "accession must not be blank"
            );
        }

        if (!normalized.matches("[A-Z0-9]+(?:-[0-9]+)?")) {
            throw new IllegalArgumentException(
                    "Invalid UniProt accession: " + accession
            );
        }

        return normalized;
    }

    private static String responseMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        String normalized = body
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();

        return ": " + normalized.substring(
                0,
                Math.min(normalized.length(), 500)
        );
    }
}