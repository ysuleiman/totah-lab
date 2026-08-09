package totah.lab.hermes.uniprot;

import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.hermes.http.HttpClientFactory;
import totah.lab.hermes.http.HttpRequestBuilder;
import totah.lab.hermes.http.HttpTransport;
import totah.lab.hermes.http.JdkHttpTransport;
import totah.lab.hermes.http.RetryingHttpTransport;
import totah.lab.hermes.http.RemoteEndpoints;
import totah.lab.hermes.uniprot.internal.UniProtJsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * UniProt REST client with three retrieval strategies behind one
 * abstraction: full single-entry JSON via {@link #fetch(String)},
 * compact bulk annotations via the TSV stream endpoint through
 * {@link #fetchAnnotations(Collection)}, and query-based search via
 * {@link #search(String)} against the same TSV stream endpoint.
 */
public final class RestUniProtClient implements UniProtClient {

    public static final URI DEFAULT_BASE_URI =
            RemoteEndpoints.uri("uniprot.entry");

    public static final URI DEFAULT_STREAM_URI =
            RemoteEndpoints.uri("uniprot.stream");

    public static final Duration DEFAULT_TIMEOUT =
            Duration.ofSeconds(30);

    // UniProt rejects queries with more than 100 OR conditions.
    public static final int DEFAULT_ANNOTATION_CHUNK_SIZE = 100;

    private static final String USER_AGENT =
            "Totah-Lab-Hermes/1.0";

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MILLIS = 250L;

    // Column order of the TSV response; keep in sync with the fields
    // request parameter.
    private static final String ANNOTATION_FIELDS =
            "accession,reviewed,protein_name,ec,keyword,"
                    + "cc_catalytic_activity,ft_act_site,ft_binding,"
                    + "cc_cofactor,xref_pfam,xref_interpro,xref_pdb";

    private static final int ANNOTATION_COLUMN_COUNT = 12;

    private final HttpTransport transport;
    private final UniProtJsonParser parser;
    private final URI baseUri;
    private final URI streamUri;
    private final Duration requestTimeout;
    private final int annotationChunkSize;

    public RestUniProtClient() {
        this(
                HttpClientFactory.create(DEFAULT_TIMEOUT),
                new ObjectMapper()
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
        this(
                httpClient,
                objectMapper,
                baseUri,
                DEFAULT_STREAM_URI,
                requestTimeout,
                DEFAULT_ANNOTATION_CHUNK_SIZE
        );
    }

    public RestUniProtClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI baseUri,
            URI streamUri,
            Duration requestTimeout,
            int annotationChunkSize
    ) {
        Objects.requireNonNull(httpClient, "httpClient");
        this.transport = new RetryingHttpTransport(
                new JdkHttpTransport(httpClient), MAX_ATTEMPTS,
                Duration.ofMillis(INITIAL_RETRY_DELAY_MILLIS));

        this.parser = new UniProtJsonParser(
                Objects.requireNonNull(objectMapper, "objectMapper")
        );

        this.baseUri = normalizeBaseUri(baseUri);

        this.streamUri = Objects.requireNonNull(streamUri, "streamUri");

        this.requestTimeout =
                Objects.requireNonNull(requestTimeout, "requestTimeout");

        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "requestTimeout must be positive"
            );
        }
        if (annotationChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "annotationChunkSize must be positive"
            );
        }
        this.annotationChunkSize = annotationChunkSize;
    }

    @Override
    public Optional<UniProtEntry> fetch(String accession)
            throws UniProtException, InterruptedException {

        String normalized = normalizeAccession(accession);

        HttpRequest request = HttpRequestBuilder.forUri(
                        entryUri(normalized)
                )
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .buildGet();

        final HttpResponse<String> response;

        try {
            response = transport.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

    @Override
    public List<UniProtAnnotation> fetchAnnotations(
            Collection<String> accessions
    ) throws UniProtException, InterruptedException {

        Objects.requireNonNull(accessions, "accessions");

        List<String> normalized = accessions.stream()
                .filter(Objects::nonNull)
                .map(accession ->
                        accession.trim().toUpperCase(Locale.ROOT))
                .filter(accession -> !accession.isEmpty())
                .distinct()
                .toList();

        if (normalized.isEmpty()) {
            return List.of();
        }

        List<UniProtAnnotation> annotations = new ArrayList<>();

        for (int from = 0;
             from < normalized.size();
             from += annotationChunkSize) {

            List<String> chunk = normalized.subList(
                    from,
                    Math.min(
                            from + annotationChunkSize,
                            normalized.size()
                    )
            );

            annotations.addAll(parseTsv(fetchChunk(chunk)));
        }

        return List.copyOf(annotations);
    }

    @Override
    public List<UniProtAnnotation> search(String query)
            throws UniProtException, InterruptedException {

        Objects.requireNonNull(query, "query");

        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }

        return parseTsv(streamTsv(query.trim()));
    }

    private String fetchChunk(List<String> chunk)
            throws UniProtException, InterruptedException {

        return streamTsv(
                "accession:(" + String.join(" OR ", chunk) + ")"
        );
    }

    private String streamTsv(String query)
            throws UniProtException, InterruptedException {

        URI uri = URI.create(
                streamUri
                        + "?query="
                        + URLEncoder.encode(
                                query,
                                StandardCharsets.UTF_8
                        )
                        + "&format=tsv&fields="
                        + ANNOTATION_FIELDS
        );

        HttpRequest request = HttpRequestBuilder.forUri(uri)
                .timeout(requestTimeout)
                .header("Accept", "text/tab-separated-values")
                .header("User-Agent", USER_AGENT)
                .buildGet();

        final HttpResponse<String> response;

        try {
            response = transport.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UniProtException(
                    "Unable to retrieve UniProt annotations", e
            );
        }

        if (response.statusCode() != 200) {
            throw new UniProtException(
                    "UniProt annotation request failed: HTTP "
                            + response.statusCode()
                            + responseMessage(response.body())
            );
        }

        return response.body();
    }

    static List<UniProtAnnotation> parseTsv(String tsv)
            throws UniProtException {

        List<UniProtAnnotation> annotations = new ArrayList<>();

        String[] lines = tsv.split("\n");

        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];

            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }

            if (line.isBlank()) {
                continue;
            }

            String[] columns = line.split("\t", -1);

            if (columns.length < ANNOTATION_COLUMN_COUNT) {
                throw new UniProtException(
                        "Malformed UniProt TSV row: "
                                + line.substring(
                                        0,
                                        Math.min(line.length(), 200)
                                )
                );
            }

            annotations.add(new UniProtAnnotation(
                    columns[0],
                    "reviewed".equalsIgnoreCase(columns[1].trim()),
                    blankToNull(columns[2]),
                    splitValues(columns[3]),
                    splitValues(columns[4]),
                    blankToNull(columns[5]),
                    blankToNull(columns[6]),
                    blankToNull(columns[7]),
                    blankToNull(columns[8]),
                    blankToNull(columns[9]),
                    blankToNull(columns[10]),
                    blankToNull(columns[11])
            ));
        }

        return List.copyOf(annotations);
    }

    private static List<String> splitValues(String cell) {
        List<String> values = new ArrayList<>();

        for (String value : cell.split(";")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty() && !values.contains(trimmed)) {
                values.add(trimmed);
            }
        }

        return List.copyOf(values);
    }

    private static String blankToNull(String cell) {
        String trimmed = cell.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private URI entryUri(String accession) {
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
