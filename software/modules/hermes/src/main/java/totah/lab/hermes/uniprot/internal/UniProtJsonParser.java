package totah.lab.hermes.uniprot.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.hermes.uniprot.UniProtEntry;
import totah.lab.hermes.uniprot.UniProtException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UniProtJsonParser {

    private final ObjectMapper objectMapper;

    public UniProtJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public UniProtEntry parse(String json) throws UniProtException {
        try {
            UniProtJsonModels.Entry entry =
                    new UniProtJsonModels.Entry(objectMapper.readTree(json));
            String accession = required(entry.text("primaryAccession"), "primaryAccession");
            String sequence = entry.nestedText("sequence", "value");
            return new UniProtEntry(
                    accession,
                    entry.text("uniProtkbId"),
                    proteinName(entry.root()),
                    geneName(entry.root()),
                    entry.nestedText("organism", "scientificName"),
                    integer(entry.longValue("organism", "taxonId")),
                    sequence,
                    entry.intValue("sequence", "length", sequence == null ? 0 : sequence.length()),
                    function(entry.root()),
                    values(entry.array("keywords"), "name"),
                    crossReferenceIds(entry.root(), "PDB"),
                    crossReferenceIds(entry.root(), "AlphaFoldDB"),
                    crossReferenceIds(entry.root(), "GO")
            );
        } catch (UniProtException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new UniProtException("Unable to parse UniProt response", e);
        }
    }

    private static String proteinName(JsonNode root) {
        JsonNode description = root.path("proteinDescription");
        String recommended = fullName(description.path("recommendedName"));
        if (recommended != null) {
            return recommended;
        }
        for (String collection : List.of("submissionNames", "alternativeNames")) {
            for (JsonNode name : iterable(description.path(collection))) {
                String value = fullName(name);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String fullName(JsonNode name) {
        return UniProtJsonModels.text(name.path("fullName"));
    }

    private static String geneName(JsonNode root) {
        for (JsonNode gene : iterable(root.path("genes"))) {
            String value = UniProtJsonModels.text(gene.path("geneName"));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String function(JsonNode root) {
        for (JsonNode comment : iterable(root.path("comments"))) {
            if (!"FUNCTION".equalsIgnoreCase(UniProtJsonModels.text(comment.path("commentType")))) {
                continue;
            }
            List<String> texts = values(nodes(comment.path("texts")), "value");
            if (!texts.isEmpty()) {
                return String.join(" ", texts);
            }
        }
        return null;
    }

    private static List<String> crossReferenceIds(JsonNode root, String database) {
        List<String> ids = new ArrayList<>();
        for (JsonNode reference : iterable(root.path("uniProtKBCrossReferences"))) {
            if (database.equalsIgnoreCase(UniProtJsonModels.text(reference.path("database")))) {
                String id = UniProtJsonModels.text(reference.path("id"));
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return List.copyOf(ids);
    }

    private static List<String> values(List<JsonNode> nodes, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : nodes) {
            String value = UniProtJsonModels.text(node.path(field));
            if (value != null && !values.contains(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static List<JsonNode> nodes(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        iterable(node).forEach(result::add);
        return result;
    }

    private static Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    private static Integer integer(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }

    private static String required(String value, String field) throws UniProtException {
        if (value == null) {
            throw new UniProtException("UniProt response is missing required field: " + field);
        }
        return value;
    }
}
