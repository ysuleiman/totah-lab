package totah.lab.hermes.uniprot.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.hermes.uniprot.UniProtCrossReference;
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
                    crossReferenceIds(entry.root(), "GO"),
                    reviewed(entry.root()),
                    ecNumbers(entry.root()),
                    goMolecularFunctions(entry.root()),
                    catalyticActivities(entry.root()),
                    bindingLigands(entry.root()),
                    activeSites(entry.root()),
                    cofactors(entry.root()),
                    crossReferences(entry.root(), "Pfam"),
                    crossReferences(entry.root(), "InterPro")
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

    private static boolean reviewed(JsonNode root) {
        String entryType = UniProtJsonModels.text(root.path("entryType"));
        return entryType != null && entryType.contains("Swiss-Prot");
    }

    private static List<String> ecNumbers(JsonNode root) {
        JsonNode recommendedName =
                root.path("proteinDescription").path("recommendedName");
        return values(nodes(recommendedName.path("ecNumbers")), "value");
    }

    private static List<String> goMolecularFunctions(JsonNode root) {
        List<String> terms = new ArrayList<>();
        for (JsonNode reference : iterable(root.path("uniProtKBCrossReferences"))) {
            if (!"GO".equalsIgnoreCase(
                    UniProtJsonModels.text(reference.path("database")))) {
                continue;
            }
            for (JsonNode property : iterable(reference.path("properties"))) {
                if (!"GoTerm".equals(
                        UniProtJsonModels.text(property.path("key")))) {
                    continue;
                }
                String value = UniProtJsonModels.text(property.path("value"));
                if (value != null && value.startsWith("F:")) {
                    String term = value.substring(2).trim();
                    if (!term.isEmpty() && !terms.contains(term)) {
                        terms.add(term);
                    }
                }
            }
        }
        return List.copyOf(terms);
    }

    private static List<String> catalyticActivities(JsonNode root) {
        List<String> activities = new ArrayList<>();
        for (JsonNode comment : commentsOfType(root, "CATALYTIC ACTIVITY")) {
            String activity = UniProtJsonModels.text(
                    comment.path("reaction").path("name"));
            if (activity == null) {
                activity = UniProtJsonModels.text(
                        comment.path("reaction").path("ecNumber"));
            }
            if (activity != null && !activities.contains(activity)) {
                activities.add(activity);
            }
        }
        return List.copyOf(activities);
    }

    private static List<String> bindingLigands(JsonNode root) {
        List<String> ligands = new ArrayList<>();
        for (JsonNode feature : featuresOfType(root, "Binding site")) {
            String ligand = UniProtJsonModels.text(
                    feature.path("ligand").path("name"));
            if (ligand == null) {
                ligand = UniProtJsonModels.text(feature.path("description"));
            }
            if (ligand != null && !ligands.contains(ligand)) {
                ligands.add(ligand);
            }
        }
        return List.copyOf(ligands);
    }

    private static List<String> activeSites(JsonNode root) {
        List<String> sites = new ArrayList<>();
        for (JsonNode feature : featuresOfType(root, "Active site")) {
            String site = UniProtJsonModels.text(feature.path("description"));
            sites.add(site == null ? "Active site" : site);
        }
        return List.copyOf(sites);
    }

    private static List<String> cofactors(JsonNode root) {
        List<String> cofactors = new ArrayList<>();
        for (JsonNode comment : commentsOfType(root, "COFACTOR")) {
            for (JsonNode cofactor : iterable(comment.path("cofactors"))) {
                String name = UniProtJsonModels.text(cofactor.path("name"));
                if (name != null && !cofactors.contains(name)) {
                    cofactors.add(name);
                }
            }
        }
        return List.copyOf(cofactors);
    }

    private static List<UniProtCrossReference> crossReferences(
            JsonNode root,
            String database
    ) {
        List<UniProtCrossReference> references = new ArrayList<>();
        for (JsonNode reference : iterable(root.path("uniProtKBCrossReferences"))) {
            if (!database.equalsIgnoreCase(
                    UniProtJsonModels.text(reference.path("database")))) {
                continue;
            }
            String id = UniProtJsonModels.text(reference.path("id"));
            if (id == null) {
                continue;
            }
            String name = null;
            for (JsonNode property : iterable(reference.path("properties"))) {
                if ("EntryName".equals(
                        UniProtJsonModels.text(property.path("key")))) {
                    name = UniProtJsonModels.text(property.path("value"));
                    break;
                }
            }
            String entryName = name;
            if (references.stream().noneMatch(existing ->
                    existing.id().equals(id))) {
                references.add(new UniProtCrossReference(id, entryName));
            }
        }
        return List.copyOf(references);
    }

    private static List<JsonNode> commentsOfType(JsonNode root, String type) {
        List<JsonNode> comments = new ArrayList<>();
        for (JsonNode comment : iterable(root.path("comments"))) {
            if (type.equalsIgnoreCase(
                    UniProtJsonModels.text(comment.path("commentType")))) {
                comments.add(comment);
            }
        }
        return comments;
    }

    private static List<JsonNode> featuresOfType(JsonNode root, String type) {
        List<JsonNode> features = new ArrayList<>();
        for (JsonNode feature : iterable(root.path("features"))) {
            if (type.equalsIgnoreCase(
                    UniProtJsonModels.text(feature.path("type")))) {
                features.add(feature);
            }
        }
        return features;
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
