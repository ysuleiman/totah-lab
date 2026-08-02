package totah.lab.hermes.uniprot.internal;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Internal, API-specific JSON projection kept separate from the public model. */
final class UniProtJsonModels {

    private UniProtJsonModels() {
    }

    record Entry(JsonNode root) {
        String text(String field) {
            return UniProtJsonModels.text(root.path(field));
        }

        Long longValue(String object, String field) {
            JsonNode node = root.path(object).path(field);
            return node.canConvertToLong() ? node.asLong() : null;
        }

        int intValue(String object, String field, int fallback) {
            JsonNode node = root.path(object).path(field);
            return node.canConvertToInt() ? node.asInt() : fallback;
        }

        String nestedText(String object, String field) {
            return UniProtJsonModels.text(root.path(object).path(field));
        }

        List<JsonNode> array(String field) {
            JsonNode node = root.path(field);
            if (!node.isArray()) {
                return List.of();
            }
            List<JsonNode> values = new ArrayList<>();
            node.forEach(values::add);
            return List.copyOf(values);
        }
    }

    static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            return text(node.path("value"));
        }
        if (!node.isValueNode()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }
}
