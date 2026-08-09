package totah.lab.hermes.rcsb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.hermes.rcsb.internal.RcsbJsonParser;
import totah.lab.hermes.http.RemoteEndpoints;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RcsbGeneralSearcher {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RcsbJsonParser parser;

    public RcsbGeneralSearcher(ObjectMapper objectMapper, RcsbJsonParser parser) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.parser = parser;
    }

    /**
     * Executes any structural, sequence, or metadata similarity query configuration,
     * resolves matching entry data, and feeds it into your parser.
     */
    public List<Object> searchAndParse(ObjectNode searchServiceNode) throws Exception {
        // Step 1: Wrap service node into unified envelope payload
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("query", searchServiceNode);
        payload.put("return_type", "entry");

        // Step 2: Query the universal search service
        List<String> matchingPdbIds = executeSearchQuery(payload);

        List<Object> results = new ArrayList<>();

        // Step 3: Fetch entries metadata and process with RcsbJsonParser
        for (String matchId : matchingPdbIds) {
            String metadataJson = fetchEntryMetadata(matchId);
            results.add(parser.parse(metadataJson));
        }

        return results;
    }

    private List<String> executeSearchQuery(ObjectNode payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(RemoteEndpoints.uri("rcsb.search"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

        return parseSearchResultIds(response.statusCode(), response.body());
    }

    List<String> parseSearchResultIds(int statusCode, String body) throws IOException {
        if (statusCode != 200) {
            throw new RcsbException("RCSB search query failed with HTTP status " + statusCode);
        }
        List<String> foundIds = new ArrayList<>();
        var root = objectMapper.readTree(body);
        var resultList = root.get("result_set");
        if (resultList != null && resultList.isArray()) {
            for (var element : resultList) {
                var identifier = element.get("identifier");
                if (identifier != null && identifier.isTextual()) {
                    foundIds.add(identifier.asText());
                }
            }
        }
        return foundIds;
    }

    private String fetchEntryMetadata(String pdbId) throws Exception {
        URI uri = RemoteEndpoints.uri("rcsb.entry").resolve(pdbId);
        HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();
        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch metadata for: " + pdbId);
        }
        return response.body();
    }
}
