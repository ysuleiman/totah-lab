package totah.lab.hermes.rcsb.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.hermes.rcsb.RcsbException;
import totah.lab.hermes.rcsb.RcsbResidue;
import totah.lab.hermes.rcsb.RcsbSearchCriteria;
import totah.lab.hermes.rcsb.RcsbSearchHit;
import totah.lab.hermes.rcsb.RcsbSequenceSearch;
import totah.lab.hermes.rcsb.RcsbStructureMotifSearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RcsbSearchJson {

    private final ObjectMapper objectMapper;

    public RcsbSearchJson(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String request(RcsbSearchCriteria criteria) throws RcsbException {
        Objects.requireNonNull(criteria, "criteria");
        ObjectNode root = objectMapper.createObjectNode();
        if (criteria instanceof RcsbSequenceSearch sequence) {
            root.set("query", sequenceQuery(sequence));
            root.put("return_type", "polymer_entity");
        } else if (criteria instanceof RcsbStructureMotifSearch motif) {
            root.set("query", motifQuery(motif));
            root.put("return_type", "assembly");
        } else {
            throw new IllegalArgumentException("Unsupported search criteria: "
                    + criteria.getClass().getName());
        }
        root.putObject("request_options").put("return_all_hits", true);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new RcsbException("Unable to encode RCSB search request", e);
        }
    }

    public List<RcsbSearchHit> response(String json) throws RcsbException {
        try {
            JsonNode resultSet = objectMapper.readTree(json).path("result_set");
            if (!resultSet.isArray()) {
                throw new RcsbException("RCSB search response is missing result_set");
            }
            List<RcsbSearchHit> hits = new ArrayList<>();
            for (JsonNode result : resultSet) {
                String identifier = result.path("identifier").textValue();
                if (identifier == null || identifier.isBlank()) {
                    throw new RcsbException("RCSB search result is missing identifier");
                }
                hits.add(new RcsbSearchHit(identifier, result.path("score").asDouble(0.0)));
            }
            return List.copyOf(hits);
        } catch (RcsbException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new RcsbException("Unable to parse RCSB search response", e);
        }
    }

    private ObjectNode sequenceQuery(RcsbSequenceSearch search) {
        ObjectNode query = terminal("sequence");
        ObjectNode parameters = query.putObject("parameters");
        parameters.put("value", search.sequence());
        parameters.put("sequence_type", "protein");
        parameters.put("identity_cutoff", search.identityCutoff());
        parameters.put("evalue_cutoff", search.eValueCutoff());
        return query;
    }

    private ObjectNode motifQuery(RcsbStructureMotifSearch search) {
        ObjectNode query = terminal("strucmotif");
        ObjectNode parameters = query.putObject("parameters");
        ObjectNode value = parameters.putObject("value");
        value.put("entry_id", search.referencePdbId());
        ArrayNode residueIds = value.putArray("residue_ids");
        for (RcsbResidue residue : search.residues()) {
            ObjectNode residueId = residueIds.addObject();
            residueId.put("label_asym_id", residue.chainId());
            residueId.put("label_seq_id", residue.sequencePosition());
        }
        parameters.put("rmsd_cutoff", search.rmsdCutoff());
        ArrayNode exchanges = parameters.putArray("exchanges");
        for (RcsbResidue residue : search.residues()) {
            if (residue.allowedExchanges().isEmpty()) {
                continue;
            }
            ObjectNode exchange = exchanges.addObject();
            ObjectNode residueId = exchange.putObject("residue_id");
            residueId.put("label_asym_id", residue.chainId());
            residueId.put("label_seq_id", residue.sequencePosition());
            ArrayNode allowed = exchange.putArray("allowed");
            residue.allowedExchanges().forEach(allowed::add);
        }
        if (exchanges.isEmpty()) {
            parameters.remove("exchanges");
        }
        return query;
    }

    private ObjectNode terminal(String service) {
        ObjectNode query = objectMapper.createObjectNode();
        query.put("type", "terminal");
        query.put("service", service);
        return query;
    }
}
