package totah.lab.pocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.protein.Point3D;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class P2RankJsonParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Pocket> parse(Path path) throws IOException {

        Path prediction = Objects.requireNonNull(path).
                resolve("prank").resolve("prediction.json");
        JsonNode root = mapper.readTree(prediction.toFile());
        List<Pocket> pockets = new ArrayList<>();

        JsonNode pocketsNode = root.get("pockets");
        if (pocketsNode == null) {
            return pockets;
        }

        long index = 0;
        for (JsonNode node : pocketsNode) {
            index++;
            Pocket.PocketBuilder builder = Pocket.builder()
                    .source(PocketSource.P2RANK);
            builder.name(node.get("name").asText());

            // Prefer the JSON rank field; fall back to the array order
            JsonNode rank = node.get("rank");
            builder.id(rank != null && !rank.asText().isBlank()
                    ? Long.parseLong(rank.asText().trim())
                    : index);

            // Explicitly handles the string-wrapped float tokens in prediction.json safely
            builder.score(Double.parseDouble(node.get("score").asText()));

            JsonNode center = node.get("center");
            builder.center(
                    new Point3D(
                            Double.parseDouble(center.get(0).asText()),
                            Double.parseDouble(center.get(1).asText()),
                            Double.parseDouble(center.get(2).asText())
                    )
            );

            List<ResidueRef> residues = new ArrayList<>();
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
                    residues.add(new ResidueRef(chain, number, null));
                }
            }
            builder.residueRefs(residues);
            pockets.add(builder.build());
        }
        return pockets;
    }
}
