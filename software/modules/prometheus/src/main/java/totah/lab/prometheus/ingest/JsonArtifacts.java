package totah.lab.prometheus.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Small Jackson helpers for tolerant reading of archive JSON artifacts.
 * Missing or mistyped fields yield {@code null} instead of throwing, so a
 * partially populated artifact degrades to a note rather than a crash.
 */
public final class JsonArtifacts {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonArtifacts() {
    }

    /** Reads a JSON document from {@code file} using a streaming parser. */
    public static JsonNode readTree(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try (InputStream in = Files.newInputStream(file)) {
            return MAPPER.readTree(in);
        }
    }

    /** Text value of {@code field}, or null when absent/null/non-textual. */
    public static String asTextOrNull(JsonNode node, String field) {
        JsonNode value = field(node, field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    /** Double value of {@code field}, or null when absent/null/non-numeric. */
    public static Double asDoubleOrNull(JsonNode node, String field) {
        JsonNode value = field(node, field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    /** Int value of {@code field}, or null when absent/null/non-numeric. */
    public static Integer asIntOrNull(JsonNode node, String field) {
        JsonNode value = field(node, field);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    /** Boolean value of {@code field}, or null when absent/null/non-boolean. */
    public static Boolean asBooleanOrNull(JsonNode node, String field) {
        JsonNode value = field(node, field);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    /** Numeric array of {@code field} as a list of doubles; null when absent or not an array. */
    public static List<Double> asDoubleListOrNull(JsonNode node, String field) {
        JsonNode value = field(node, field);
        if (value == null || !value.isArray()) {
            return null;
        }
        List<Double> result = new ArrayList<>(value.size());
        for (JsonNode element : value) {
            if (!element.isNumber()) {
                return null;
            }
            result.add(element.asDouble());
        }
        return result;
    }

    private static JsonNode field(JsonNode node, String field) {
        if (node == null || node.isNull() || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value;
    }
}
