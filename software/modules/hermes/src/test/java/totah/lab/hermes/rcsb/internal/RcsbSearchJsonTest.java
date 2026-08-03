package totah.lab.hermes.rcsb.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.hermes.rcsb.RcsbResidue;
import totah.lab.hermes.rcsb.RcsbSequenceSearch;
import totah.lab.hermes.rcsb.RcsbStructureMotifSearch;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RcsbSearchJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RcsbSearchJson codec = new RcsbSearchJson(objectMapper);

    @Test
    void encodesSequenceSearchWithoutDomainSpecificDefaults() throws Exception {
        var json = objectMapper.readTree(codec.request(
                new RcsbSequenceSearch(" acd ef ", 0.35, 0.001)));

        assertEquals("ACDEF", json.at("/query/parameters/value").textValue());
        assertEquals(0.35, json.at("/query/parameters/identity_cutoff").doubleValue());
        assertEquals(0.001, json.at("/query/parameters/evalue_cutoff").doubleValue());
        assertEquals("polymer_entity", json.path("return_type").textValue());
        assertTrue(json.at("/request_options/return_all_hits").booleanValue());
    }

    @Test
    void encodesReferenceResiduesForStructureMotifSearch() throws Exception {
        var json = objectMapper.readTree(codec.request(new RcsbStructureMotifSearch(
                "1abc", List.of(new RcsbResidue("A", 10, List.of("lys", "HIS")),
                new RcsbResidue("A", 25)), 2.0)));

        assertEquals("1ABC", json.at("/query/parameters/value/entry_id").textValue());
        assertEquals(10, json.at("/query/parameters/value/residue_ids/0/label_seq_id").intValue());
        assertEquals("A", json.at(
                "/query/parameters/exchanges/0/residue_id/label_asym_id").textValue());
        assertEquals("LYS", json.at(
                "/query/parameters/exchanges/0/allowed/0").textValue());
        assertEquals("HIS", json.at(
                "/query/parameters/exchanges/0/allowed/1").textValue());
        assertEquals(2.0, json.at("/query/parameters/rmsd_cutoff").doubleValue());
        assertEquals("assembly", json.path("return_type").textValue());
    }

    @Test
    void parsesSearchHitsInServerOrder() throws Exception {
        var hits = codec.response("""
                {"result_set":[
                  {"identifier":"1ABC_1","score":0.9},
                  {"identifier":"2DEF_1","score":0.7}
                ]}
                """);

        assertEquals(List.of("1ABC_1", "2DEF_1"),
                hits.stream().map(hit -> hit.identifier()).toList());
        assertEquals(0.9, hits.getFirst().score());
        assertEquals("1ABC", hits.getFirst().pdbId().orElseThrow());
    }
}
