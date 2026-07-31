package totah.lab.http.biohub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Client for reproducible sequence and cluster retrieval from ESM Atlas. */
public final class BiohubAtlasClient {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_BATCH_SIZE = 500;
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BiohubAtlasClient() {
        this(URI.create("https://biohub.ai"));
    }

    public BiohubAtlasClient(URI baseUri) {
        this(baseUri, HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), new ObjectMapper());
    }

    BiohubAtlasClient(
            URI baseUri,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public AtlasCluster getCluster(String representativeHash)
            throws IOException, InterruptedException {
        String hash = requireHash(representativeHash);
        JsonNode payload = getJson("/esm/protein/api/v1alpha1/clusters/" + hash);
        List<String> members = objectMapper.convertValue(
                payload.path("member_protein_hashes"),
                objectMapper.getTypeFactory().constructCollectionType(
                        List.class, String.class
                )
        );
        return new AtlasCluster(
                hash,
                payload.path("protein_name").asText(),
                payload.path("cluster_size").asInt(),
                List.copyOf(members),
                payload.deepCopy()
        );
    }

    public AtlasProtein getProtein(String proteinHash)
            throws IOException, InterruptedException {
        String hash = requireHash(proteinHash);
        JsonNode payload = getJson(
                "/esm/protein/api/v1alpha1/proteins/" + hash
                        + "?topk_features=1&fold_on_miss=false"
        );
        String sequence = requiredText(payload, "sequence");
        String sequenceHash = md5(sequence);
        if (!hash.equals(sequenceHash)) {
            throw new IOException("BioHub Atlas sequence hash mismatch: expected "
                    + hash + ", calculated " + sequenceHash);
        }
        return new AtlasProtein(
                hash,
                payload.path("accession").asText(),
                payload.path("source").asText(),
                sequence
        );
    }

    public byte[] downloadSequenceArchive(List<String> proteinHashes)
            throws IOException, InterruptedException {
        Objects.requireNonNull(proteinHashes, "proteinHashes");
        if (proteinHashes.isEmpty() || proteinHashes.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "proteinHashes must contain 1 to " + MAX_BATCH_SIZE + " entries"
            );
        }
        ObjectNode request = objectMapper.createObjectNode();
        request.putArray("protein_hashes").addAll(proteinHashes.stream()
                .map(this::requireHash)
                .map(TextNode::valueOf)
                .toList());
        request.put("topk_features", 1);
        request.put("include_structure", false);
        request.put("include_cluster_info", true);
        request.put("include_sequence", true);
        ObjectNode features = request.putObject("include_features");
        features.put("protein_level", false);
        features.put("per_residue", false);

        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(endpoint(
                        "/esm/protein/api/v1alpha1/proteins/batch"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        objectMapper.writeValueAsBytes(request)
                )).build());
        if (response.statusCode() == 200) {
            return response.body();
        }
        if (response.statusCode() != 202) {
            throw failure("batch submission", response);
        }
        JsonNode job = objectMapper.readTree(response.body());
        String pollUrl = requiredText(job, "poll_url");
        for (int attempt = 0; attempt < 900; attempt++) {
            Thread.sleep(2_000);
            HttpResponse<byte[]> poll = send(HttpRequest.newBuilder(
                            endpoint(pollUrl))
                    .timeout(TIMEOUT).GET().build());
            if (poll.statusCode() == 202) {
                continue;
            }
            if (poll.statusCode() != 200) {
                throw failure("batch polling", poll);
            }
            JsonNode completed = objectMapper.readTree(poll.body());
            String downloadUrl = requiredText(completed, "download_url");
            HttpResponse<byte[]> archive = send(HttpRequest.newBuilder(
                            URI.create(downloadUrl))
                    .timeout(TIMEOUT).GET().build());
            if (archive.statusCode() < 200 || archive.statusCode() >= 300) {
                throw failure("batch download", archive);
            }
            return archive.body();
        }
        throw new IOException("BioHub Atlas batch did not complete within 30 minutes");
    }

    private JsonNode getJson(String path) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(endpoint(path))
                .timeout(TIMEOUT).GET().build());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("GET " + path, response);
        }
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<byte[]> send(HttpRequest request)
            throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI endpoint(String path) {
        return baseUri.resolve(path);
    }

    private IOException failure(String operation, HttpResponse<byte[]> response) {
        String body = new String(response.body());
        return new IOException("BioHub Atlas " + operation + " failed with HTTP "
                + response.statusCode() + ": " + body.substring(0, Math.min(500, body.length())));
    }

    private String requiredText(JsonNode node, String field) throws IOException {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new IOException("BioHub Atlas response has no " + field);
        }
        return value;
    }

    private String requireHash(String value) {
        String hash = Objects.requireNonNull(value, "proteinHash").trim();
        if (!hash.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("Invalid protein MD5 hash: " + hash);
        }
        return hash.toLowerCase();
    }

    private String md5(String sequence) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(sequence.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 is unavailable", exception);
        }
    }

    public record AtlasCluster(
            String representativeHash,
            String representativeName,
            int memberCount,
            List<String> memberHashes,
            JsonNode rawMetadata
    ) {
    }

    public record AtlasProtein(
            String proteinHash,
            String accession,
            String source,
            String sequence
    ) {
    }
}
