package totah.lab.hermes.rcsb.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.hermes.rcsb.RcsbException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RcsbJsonParserTest {

    private final RcsbJsonParser parser = new RcsbJsonParser(new ObjectMapper());

    @Test
    void parsesCoreEntryMetadata() throws Exception {
        var entry = parser.parse("""
                {
                  "rcsb_id": "1ABC",
                  "struct": {"title": "Example protein"},
                  "exptl": [{"method": "X-RAY DIFFRACTION"}],
                  "struct_keywords": {"pdbx_keywords": "TRANSFERASE"},
                  "audit_author": [{"name": "Doe, J."}, {"name": "Smith, A."}],
                  "rcsb_accession_info": {
                    "initial_release_date": "2024-01-02T00:00:00Z"
                  },
                  "rcsb_entry_info": {
                    "resolution_combined": [1.8],
                    "polymer_entity_count_protein": 2,
                    "deposited_atom_count": 1234
                  }
                }
                """);

        assertEquals("1ABC", entry.pdbId());
        assertEquals("Example protein", entry.title());
        assertEquals(List.of("X-RAY DIFFRACTION"), entry.experimentalMethods());
        assertEquals(List.of(1.8), entry.resolutions());
        assertEquals("TRANSFERASE", entry.keywords());
        assertEquals(List.of("Doe, J.", "Smith, A."), entry.authors());
        assertEquals(Instant.parse("2024-01-02T00:00:00Z"), entry.initialReleaseDate());
        assertEquals(2, entry.proteinEntityCount());
        assertEquals(1234, entry.depositedAtomCount());
    }

    @Test
    void usesImmutableEmptyCollectionsForMissingOptionalArrays() throws Exception {
        var entry = parser.parse("{\"rcsb_id\":\"1ABC\"}");

        assertEquals(List.of(), entry.experimentalMethods());
        assertEquals(List.of(), entry.resolutions());
        assertEquals(List.of(), entry.authors());
        assertThrows(UnsupportedOperationException.class,
                () -> entry.authors().add("Other"));
    }

    @Test
    void rejectsResponsesWithoutAnEntryId() {
        assertThrows(RcsbException.class, () -> parser.parse("{}"));
    }

    @Test
    void parsesEntrySummary() throws Exception {
        var summary = parser.parseSummary("""
                {
                  "rcsb_id": "1EH6",
                  "struct": {"title": "HUMAN O6-ALKYLGUANINE-DNA ALKYLTRANSFERASE"},
                  "rcsb_entry_info": {
                    "experimental_method": "X-ray",
                    "resolution_combined": [2.0],
                    "nonpolymer_bound_components": ["ZN"],
                    "polymer_entity_count": 1,
                    "deposited_polymer_entity_instance_count": 2,
                    "assembly_count": 1
                  }
                }
                """);

        assertEquals("1EH6", summary.pdbId());
        assertEquals("HUMAN O6-ALKYLGUANINE-DNA ALKYLTRANSFERASE",
                summary.title());
        assertEquals("X-ray", summary.experimentalMethod());
        assertEquals(List.of(2.0), summary.resolutions());
        assertEquals(List.of("ZN"), summary.ligandComponentIds());
        assertEquals(1, summary.polymerEntityCount());
        assertEquals(2, summary.chainCount());
        assertEquals(1, summary.assemblyCount());
    }

    @Test
    void parsesSummaryWithMissingOptionalFields() throws Exception {
        var summary = parser.parseSummary("{\"rcsb_id\":\"12YI\"}");

        assertEquals("12YI", summary.pdbId());
        assertEquals(List.of(), summary.resolutions());
        assertEquals(List.of(), summary.ligandComponentIds());
        assertEquals(0, summary.chainCount());
        assertThrows(UnsupportedOperationException.class,
                () -> summary.ligandComponentIds().add("ATP"));
        assertThrows(RcsbException.class, () -> parser.parseSummary("{}"));
    }
}
