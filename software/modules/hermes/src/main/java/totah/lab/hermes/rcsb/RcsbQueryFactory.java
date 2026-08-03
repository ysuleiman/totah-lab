package totah.lab.hermes.rcsb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class RcsbQueryFactory {

    private final ObjectMapper mapper;

    public RcsbQueryFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 1. WHOLE STRUCTURE SEARCH (3D Shape Similarity)
     * Matches the overall fold of a whole human chain against the entire database.
     */
    public ObjectNode createWholeStructureQuery(String targetPdbId, String assemblyId) {
        ObjectNode service = mapper.createObjectNode();
        service.put("type", "terminal");
        service.put("service", "structure"); // Global structure service

        ObjectNode params = service.putObject("parameters");
        ObjectNode value = params.putObject("value");
        value.put("entry_id", targetPdbId);
        value.put("assembly_id", assemblyId);
        params.put("operator", "relaxed_shape_match");
        return service;
    }

    /**
     * 2. SEQUENCE SIMILARITY SEARCH (FASTA/BLAST)
     * Performs sequence alignments across protein polymer chains.
     */
    public ObjectNode createSequenceQuery(String fastaSequence, double identityCutoff) {
        ObjectNode service = mapper.createObjectNode();
        service.put("type", "terminal");
        service.put("service", "sequence"); // Sequence matching service

        ObjectNode params = service.putObject("parameters");
        params.put("value", fastaSequence);
        params.put("sequence_type", "protein");
        params.put("identity_cutoff", identityCutoff);
        params.put("evalue_cutoff", 0.1);
        return service;
    }

    /**
     * 3. POCKET MOTIF SEARCH (Local Environment Match)
     * Matches local residue configurations (your previous implementation).
     */
    public ObjectNode createPocketQuery(String targetPdbId, String chainId, Iterable<Integer> residues) {
        ObjectNode service = mapper.createObjectNode();
        service.put("type", "terminal");
        service.put("service", "structure_motif");

        ObjectNode params = service.putObject("parameters");
        params.put("entry_id", targetPdbId);
        params.put("similarity_cutoff", 0.8);

        ArrayNode residueIds = params.putArray("residue_ids");
        for (int resNum : residues) {
            ObjectNode res = residueIds.addObject();
            res.put("chain_id", chainId);
            res.put("sequence_number", resNum);
        }
        return service;
    }
}
