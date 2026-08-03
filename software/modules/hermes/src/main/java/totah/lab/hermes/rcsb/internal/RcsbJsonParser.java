package totah.lab.hermes.rcsb.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.hermes.rcsb.RcsbEntry;
import totah.lab.hermes.rcsb.RcsbException;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RcsbJsonParser {

    private final ObjectMapper objectMapper;

    public RcsbJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public RcsbEntry parse(String json) throws RcsbException {
        try {
            JsonNode root = objectMapper.readTree(json);
            String pdbId = required(text(root.path("rcsb_id")), "rcsb_id");
            JsonNode entryInfo = root.path("rcsb_entry_info");
            return new RcsbEntry(
                    pdbId,
                    text(root.path("struct").path("title")),
                    texts(root.path("exptl"), "method"),
                    doubles(entryInfo.path("resolution_combined")),
                    text(root.path("struct_keywords").path("pdbx_keywords")),
                    texts(root.path("audit_author"), "name"),
                    instant(root.path("rcsb_accession_info").path("initial_release_date")),
                    entryInfo.path("polymer_entity_count_protein").asInt(0),
                    entryInfo.path("deposited_atom_count").asInt(0));
        } catch (RcsbException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new RcsbException("Unable to parse RCSB response", e);
        }
    }

    private static List<String> texts(JsonNode array, String field) {
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            String value = text(node.path(field));
            if (value != null && !values.contains(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static List<Double> doubles(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<Double> values = new ArrayList<>();
        for (JsonNode node : array) {
            if (node.isNumber()) {
                values.add(node.doubleValue());
            }
        }
        return List.copyOf(values);
    }

    private static Instant instant(JsonNode node) throws RcsbException {
        String value = text(node);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new RcsbException("Invalid RCSB initial_release_date: " + value, e);
        }
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() && !node.textValue().isBlank()
                ? node.textValue() : null;
    }

    private static String required(String value, String field) throws RcsbException {
        if (value == null) {
            throw new RcsbException("RCSB response is missing required field: " + field);
        }
        return value;
    }
}
