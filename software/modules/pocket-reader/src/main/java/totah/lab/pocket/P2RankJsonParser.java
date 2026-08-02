package totah.lab.pocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketMetric;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.ResidueId;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class P2RankJsonParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Pocket> parse(Path path) throws IOException {

        Path prediction = predictionPath(
                Objects.requireNonNull(path, "path"));
        JsonNode root = mapper.readTree(prediction.toFile());
        List<Pocket> pockets = new ArrayList<>();

        JsonNode pocketsNode = root.get("pockets");
        if (pocketsNode == null) {
            return pockets;
        }

        long index = 0;
        for (JsonNode node : pocketsNode) {
            index++;
            String name = node.get("name").asText();

            // Prefer the JSON rank field; fall back to the array order
            JsonNode rank = node.get("rank");
            PocketId id = PocketId.of(rank != null && !rank.asText().isBlank()
                    ? Long.parseLong(rank.asText().trim())
                    : index);

            // Explicitly handles the string-wrapped float tokens in prediction.json safely
            double score = Double.parseDouble(node.get("score").asText());

            JsonNode center = node.get("center");
            Point3D pocketCenter = new Point3D(
                            Double.parseDouble(center.get(0).asText()),
                            Double.parseDouble(center.get(1).asText()),
                            Double.parseDouble(center.get(2).asText())
                    );

            List<ResidueId> residues = new ArrayList<>();
            for (JsonNode r : node.get("residues")) {
                String rawText = r.asText();
                String[] parts = rawText.split("_");

                // Boundaries guard protects against potential formatting breaks
                if (parts.length >= 2) {
                    String chain = parts[0];

                    // Regex strips extraneous text characters to insulate number parsing
                    String cleanNumber = parts[1].replaceAll("[^0-9-]", "");
                    if (cleanNumber.isEmpty()) {
                        continue;
                    }
                    int number = Integer.parseInt(cleanNumber);

                    // Correctly preserves reference-only pattern by supplying null for name
                    residues.add(new ResidueId(chain, number, null));
                }
            }
            pockets.add(new Pocket(
                    id,
                    name,
                    PocketSource.P2RANK,
                    pocketCenter,
                    residues,
                    List.of(new PocketMetric(
                            PocketMetricType.P2RANK_PROBABILITY,
                            score)),
                    Optional.empty(),
                    Optional.empty(),
                    Map.of()));
        }
        return pockets;
    }

    private Path predictionPath(Path path) throws IOException {
        if (java.nio.file.Files.isRegularFile(path)) {
            return path;
        }
        Path direct = path.resolve("prediction.json");
        if (java.nio.file.Files.isRegularFile(direct)) {
            return direct;
        }
        Path nested = path.resolve("prank").resolve("prediction.json");
        if (java.nio.file.Files.isRegularFile(nested)) {
            return nested;
        }
        throw new IOException("P2Rank prediction.json not found under " + path);
    }
}
