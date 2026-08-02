package totah.lab.hermes.uniprot.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UniProtJsonParserTest {

    private final UniProtJsonParser parser = new UniProtJsonParser(new ObjectMapper());

    @Test
    void mapsApiJsonToImmutablePublicEntry() throws Exception {
        String json = """
                {
                  "primaryAccession": "Q9H8H3",
                  "uniProtkbId": "METTL7A_HUMAN",
                  "proteinDescription": {"recommendedName": {"fullName": {"value": "Protein-lysine methyltransferase"}}},
                  "genes": [{"geneName": {"value": "METTL7A"}}],
                  "organism": {"scientificName": "Homo sapiens", "taxonId": 9606},
                  "sequence": {"value": "MABC", "length": 4},
                  "comments": [{"commentType": "FUNCTION", "texts": [{"value": "Catalyzes methylation."}]}],
                  "keywords": [{"name": "Methyltransferase"}],
                  "uniProtKBCrossReferences": [
                    {"database": "PDB", "id": "8ABC"},
                    {"database": "AlphaFoldDB", "id": "AF-Q9H8H3-F1"},
                    {"database": "GO", "id": "GO:0008168"}
                  ]
                }
                """;

        var entry = parser.parse(json);

        assertEquals("Q9H8H3", entry.accession());
        assertEquals("METTL7A", entry.geneName());
        assertEquals(9606, entry.taxonomyId());
        assertEquals("Catalyzes methylation.", entry.function());
        assertEquals(java.util.List.of("8ABC"), entry.pdbIds());
        assertThrows(UnsupportedOperationException.class, () -> entry.pdbIds().add("1XYZ"));
    }

    @Test
    void rejectsResponseWithoutAccession() {
        assertThrows(Exception.class, () -> parser.parse("{}"));
    }
}
