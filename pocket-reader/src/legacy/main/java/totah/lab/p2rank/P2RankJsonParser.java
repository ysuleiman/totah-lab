package totah.lab.p2rank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import totah.lab.pocket.Pocket;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class P2RankJsonParser {

    private final ObjectMapper mapper = new ObjectMapper();


    public P2RankResult parse(Path jsonFile)
            throws IOException {
        JsonNode root = mapper.readTree(jsonFile.toFile());
        Map<String, P2RankResidueScore> residues = parseResidues(root);

        List<Pocket> pockets = parsePockets(root);

        P2RankResult result =
                new P2RankResult();

        result.setPockets(pockets);
        result.setResidues(residues);
        return result;
    }


    private Map<String, P2RankResidueScore> parseResidues(JsonNode root) {
        Map<String, P2RankResidueScore> result =
                new LinkedHashMap<>();
        JsonNode structure =
                root.get("structure");
        ArrayNode indices =
                (ArrayNode) structure.get("indices");


        ArrayNode sequence =
                (ArrayNode) structure.get("sequence");
        ArrayNode conservation =
                (ArrayNode)
                        structure
                                .get("scores")
                                .get("conservation");
        for (int i = 0; i < indices.size(); i++) {
            String id = indices.get(i).asText();
            String aa = sequence.get(i).asText();
            double cons = conservation.get(i).asDouble();
            int position =
                    Integer.parseInt(
                            id.substring(
                                    id.indexOf("_") + 1
                            )
                    );
            result.put(
                    id,
                    new P2RankResidueScore(
                            id.substring(0, id.indexOf("_")),
                            position,
                            aa,
                            cons,
                            0.0,
                            0.0,
                            0));
        }
        return result;
    }

    private List<Pocket> parsePockets(JsonNode root) {
        List<Pocket> result = new ArrayList<>();

        for (JsonNode node : root.get("pockets")) {
            P2RankPocket pocket =
                    new P2RankPocket();
            pocket.setPocketName(
                    node.get("name").asText()
            );
            pocket.setId(
                    node.get("rank").asInt()
            );
            pocket.setScore(
                    node.get("score").asDouble()
            );
            pocket.setDruggabilityScore(
                    node.get("probability").asDouble()
            );

            ArrayNode center = (ArrayNode) node.get("center");
            pocket.setCenter(
                    new double[]{
                            center.get(0).asDouble(),
                            center.get(1).asDouble(),
                            center.get(2).asDouble()
                    }
            );

            List<String> residues = new ArrayList<>();
            node.get("residues")
                    .forEach(r ->
                            residues.add(r.asText())
                    );
            pocket.setResidueIds(residues);
            List<Integer> surface = new ArrayList<>();
            node.get("surface")
                    .forEach(a ->
                            surface.add(a.asInt())
                    );
            pocket.setSurfaceAtomIds(surface);
            result.add(pocket);
        }
        return result;
    }
}
