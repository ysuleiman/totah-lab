package totah.lab.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.http.config.EsmHttpClientConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class EsmFold2Client {

    private final HttpClient httpClient;
    private final EsmHttpClientConfig config;
    private final ObjectMapper objectMapper; // Added for robust JSON parsing

    public EsmFold2Client(EsmHttpClientConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Submits an unbound sequence, parses the JSON payload, extracts pocket metrics,
     * and returns the raw PDB string content.
     */
    public CompletableFuture<String> predictApoStructureAsync(String sequence) {
        String jsonPayload = String.format("""
            {
                "sequence": "%s",
                "model_variant": "esmfold2-fast",
                "compute_budget": "balanced",
                "return_pocket_features": true
            }
            """, sequence);

        String fullEndpointUrl = config.getApiUrl() + "/api/v1/esmfold2/predict";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullEndpointUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("ESMFold2 API Error [" + response.statusCode() + "]: " + response.body());
                    }

                    try {
                        // Read the response text as a JSON tree structure
                        JsonNode root = objectMapper.readTree(response.body());

                        // 1. Process pocket metrics if returned by the platform layer
                        if (root.has("pocket_metrics")) {
                            JsonNode pocket = root.get("pocket_metrics");
                            double volume = pocket.path("estimated_volume_angstroms").asDouble();
                            double drugScore = pocket.path("druggability_score").asDouble();

                            System.out.println("\n--- Local Pocket Diagnostics ---");
                            System.out.printf("Estimated Pocket Volume: %.2f Å³\n", volume);
                            System.out.printf("Druggability Score: %.2f%%\n", drugScore * 100);
                        }

                        // 2. Extract and return the underlying 3D coordinate text block
                        if (root.has("pdb_string")) {
                            return root.get("pdb_string").asText();
                        } else {
                            // Fallback if the endpoint dumps the unwrapped PDB directly
                            return response.body();
                        }

                    } catch (IOException e) {
                        throw new RuntimeException("Failed to decode response JSON structure: " + e.getMessage(), e);
                    }
                });
    }

    public static void main(String[] args) {
        EsmHttpClientConfig mockConfig = new EsmHttpClientConfig() {
            @Override
            public String getApiUrl() { return "https://biohub.ai"; }
            @Override
            public String getApiKey() { return "6h1XwS5OSMhjDDNHZFNER7"; }
        };

        EsmFold2Client client = new EsmFold2Client(mockConfig);

        // Target: Human Carbonic Anhydrase II active fragment (known drug pocket)
        String carbonicAnhydraseSequence = "MSHHWGYGKHNGPEHWHKDFPIAKGERQSPVDIDTHTAKYDPSLKPLSVSYDQATSLRILNNGHAFNVEFDDSQDKAVLKGGPLDGTYRLIQFHFHWGSLDGQGSEHTVDKKKYAAELHLVHWNTKYGDFGKAVQQPDGLAVLGIFLKVGSAKPGLQKVVDVLDSIKTKGKSADFTNFDPRGLLPESLDYWTYPGSLTTPPLLECVTWIVLKEPISVSSEQVLKFRKLNFNGEGEPEELMVDNWRPAQPLKNRQIKASFK";

        System.out.println("Submitting Carbonic Anhydrase sequence to folding engine...");

        client.predictApoStructureAsync(carbonicAnhydraseSequence)
                .thenAccept(pdbContent -> {
                    try {
                        Path outputPath = Path.of("carbonic_anhydrase_pocket.pdb");
                        Files.writeString(outputPath, pdbContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        System.out.println("\n[Success] Atomic coordinates stored cleanly: " + outputPath.toAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("Failed to write coordinates: " + e.getMessage());
                    }
                })
                .exceptionally(ex -> {
                    System.err.println("Pipeline Execution Failed: " + ex.getMessage());
                    return null;
                })
                .join();
    }
}
