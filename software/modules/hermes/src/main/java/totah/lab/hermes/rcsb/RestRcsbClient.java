package totah.lab.hermes.rcsb;

import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.hermes.http.HttpClientFactory;
import totah.lab.hermes.http.HttpRequestBuilder;
import totah.lab.hermes.http.HttpTransport;
import totah.lab.hermes.http.JdkHttpTransport;
import totah.lab.hermes.http.RemoteEndpoints;
import totah.lab.hermes.rcsb.internal.RcsbJsonParser;
import totah.lab.hermes.rcsb.internal.RcsbSearchJson;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

public final class RestRcsbClient implements RcsbClient {

    public static final URI DEFAULT_BASE_URI =
            RemoteEndpoints.uri("rcsb.entry");
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final URI DEFAULT_SEARCH_URI =
            RemoteEndpoints.uri("rcsb.search");
    public static final URI DEFAULT_DOWNLOAD_BASE_URI =
            RemoteEndpoints.uri("rcsb.download");
    private static final String USER_AGENT = "Totah-Lab-Hermes/1.0";

    private final HttpTransport transport;
    private final RcsbJsonParser parser;
    private final RcsbSearchJson searchJson;
    private final URI baseUri;
    private final URI searchUri;
    private final URI downloadBaseUri;
    private final Duration requestTimeout;

    public RestRcsbClient() {
        this(HttpClientFactory.create(DEFAULT_TIMEOUT), new ObjectMapper(),
                DEFAULT_BASE_URI, DEFAULT_SEARCH_URI, DEFAULT_DOWNLOAD_BASE_URI,
                DEFAULT_TIMEOUT);
    }

    public RestRcsbClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this(httpClient, objectMapper, DEFAULT_BASE_URI, DEFAULT_SEARCH_URI,
                DEFAULT_DOWNLOAD_BASE_URI, DEFAULT_TIMEOUT);
    }

    public RestRcsbClient(HttpClient httpClient, ObjectMapper objectMapper,
                             URI baseUri, Duration requestTimeout) {
        this(httpClient, objectMapper, baseUri, DEFAULT_SEARCH_URI,
                DEFAULT_DOWNLOAD_BASE_URI, requestTimeout);
    }

    public RestRcsbClient(HttpClient httpClient, ObjectMapper objectMapper,
                          URI baseUri, URI searchUri, URI downloadBaseUri,
                          Duration requestTimeout) {
        this.transport = new JdkHttpTransport(
                Objects.requireNonNull(httpClient, "httpClient"));
        this.parser = new RcsbJsonParser(
                Objects.requireNonNull(objectMapper, "objectMapper"));
        this.searchJson = new RcsbSearchJson(objectMapper);
        this.baseUri = normalizeBaseUri(baseUri);
        this.searchUri = Objects.requireNonNull(searchUri, "searchUri");
        this.downloadBaseUri = normalizeBaseUri(downloadBaseUri);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
    }

    @Override
    public List<RcsbSearchHit> search(RcsbSearchCriteria criteria)
            throws RcsbException, InterruptedException {
        String requestBody = searchJson.request(criteria);
        HttpRequest request = HttpRequestBuilder.forUri(searchUri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .buildPost(HttpRequest.BodyPublishers.ofString(
                        requestBody, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = transport.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                return searchJson.response(response.body());
            }
            if (response.statusCode() == 204) {
                return List.of();
            }
            throw new RcsbException("RCSB search failed: HTTP " + response.statusCode()
                    + responseMessage(response.body()));
        } catch (IOException e) {
            if (e instanceof RcsbException rcsbException) {
                throw rcsbException;
            }
            throw new RcsbException("Unable to search RCSB", e);
        }
    }

    @Override
    public Path downloadCif(String pdbId, Path destination)
            throws RcsbException, InterruptedException {
        String normalized = normalizePdbId(pdbId);
        Objects.requireNonNull(destination, "destination");
        HttpRequest request = HttpRequestBuilder.forUri(downloadBaseUri.resolve(
                        URLEncoder.encode(normalized, StandardCharsets.UTF_8) + ".cif"))
                .timeout(requestTimeout)
                .header("Accept", "chemical/x-mmcif, text/plain")
                .header("User-Agent", USER_AGENT)
                .buildGet();
        try {
            HttpResponse<byte[]> response = transport.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new RcsbException("RCSB coordinate download failed for PDB ID "
                        + normalized + ": HTTP " + response.statusCode());
            }
            Path parent = destination.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(destination, response.body(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return destination;
        } catch (IOException e) {
            if (e instanceof RcsbException rcsbException) {
                throw rcsbException;
            }
            throw new RcsbException("Unable to download RCSB PDB ID " + normalized, e);
        }
    }

    @Override
    public Optional<RcsbEntry> fetch(String pdbId)
            throws RcsbException, InterruptedException {
        String body = fetchEntryBody(normalizePdbId(pdbId));
        return body == null ? Optional.empty() : Optional.of(parser.parse(body));
    }

    @Override
    public Optional<RcsbEntrySummary> fetchSummary(String pdbId)
            throws RcsbException, InterruptedException {
        String body = fetchEntryBody(normalizePdbId(pdbId));
        return body == null
                ? Optional.empty() : Optional.of(parser.parseSummary(body));
    }

    @Override
    public List<RcsbEntrySummary> searchEntries(RcsbAttributeSearch criteria)
            throws RcsbException, InterruptedException {
        List<RcsbEntrySummary> summaries = new ArrayList<>();
        for (RcsbSearchHit hit : search(criteria)) {
            String pdbId = hit.pdbId().orElse(hit.identifier());
            fetchSummary(pdbId).ifPresent(summaries::add);
        }
        return List.copyOf(summaries);
    }

    /** Entry core JSON body, or null when the entry does not exist. */
    private String fetchEntryBody(String normalized)
            throws RcsbException, InterruptedException {
        HttpRequest request = HttpRequestBuilder.forUri(requestUri(normalized))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .buildGet();
        try {
            HttpResponse<String> response = transport.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return switch (response.statusCode()) {
                case 200 -> response.body();
                case 404 -> null;
                default -> throw new RcsbException("RCSB request failed for PDB ID "
                        + normalized + ": HTTP " + response.statusCode()
                        + responseMessage(response.body()));
            };
        } catch (IOException e) {
            if (e instanceof RcsbException rcsbException) {
                throw rcsbException;
            }
            throw new RcsbException("Unable to retrieve RCSB PDB ID " + normalized, e);
        }
    }

    private URI requestUri(String pdbId) {
        return baseUri.resolve(URLEncoder.encode(pdbId, StandardCharsets.UTF_8));
    }

    private static URI normalizeBaseUri(URI baseUri) {
        Objects.requireNonNull(baseUri, "baseUri");
        String value = baseUri.toString();
        return value.endsWith("/") ? baseUri : URI.create(value + "/");
    }

    private static String normalizePdbId(String pdbId) {
        Objects.requireNonNull(pdbId, "pdbId");
        String normalized = pdbId.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9][A-Z0-9]{3}")) {
            throw new IllegalArgumentException("Invalid PDB ID: " + pdbId);
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
