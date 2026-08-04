package totah.lab.hermes.uniprot.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.hermes.uniprot.UniProtCrossReference;
import totah.lab.hermes.uniprot.UniProtEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniProtJsonParserAnnotationTest {

    private final UniProtJsonParser parser =
            new UniProtJsonParser(new ObjectMapper());

    @Test
    void mapsAnnotationFields() throws Exception {
        String json = """
                {
                  "primaryAccession": "Q9H8H3",
                  "uniProtkbId": "METTL7A_HUMAN",
                  "entryType": "UniProtKB reviewed (Swiss-Prot)",
                  "proteinDescription": {
                    "recommendedName": {
                      "fullName": {"value": "Protein-lysine methyltransferase"},
                      "ecNumbers": [{"value": "2.1.1.43"}]
                    }
                  },
                  "genes": [{"geneName": {"value": "METTL7A"}}],
                  "organism": {"scientificName": "Homo sapiens", "taxonId": 9606},
                  "sequence": {"value": "MABC", "length": 4},
                  "comments": [
                    {"commentType": "FUNCTION", "texts": [{"value": "Catalyzes methylation."}]},
                    {"commentType": "CATALYTIC ACTIVITY", "reaction": {"name": "S-adenosyl-L-methionine + L-lysine = S-adenosyl-L-homocysteine + N(6)-methyl-L-lysine", "ecNumber": "2.1.1.43"}},
                    {"commentType": "COFACTOR", "cofactors": [{"name": "Zn(2+)"}]}
                  ],
                  "features": [
                    {"type": "Binding site", "location": {"start": {"value": 10}, "end": {"value": 10}}, "ligand": {"name": "S-adenosyl-L-methionine"}},
                    {"type": "Active site", "location": {"start": {"value": 20}, "end": {"value": 20}}, "description": "Proton acceptor"},
                    {"type": "Binding site", "location": {"start": {"value": 30}, "end": {"value": 30}}, "description": "substrate"}
                  ],
                  "keywords": [{"name": "Methyltransferase"}, {"name": "Transferase"}],
                  "uniProtKBCrossReferences": [
                    {"database": "PDB", "id": "8ABC"},
                    {"database": "AlphaFoldDB", "id": "AF-Q9H8H3-F1"},
                    {"database": "GO", "id": "GO:0008168", "properties": [{"key": "GoTerm", "value": "F:methyltransferase activity"}]},
                    {"database": "GO", "id": "GO:0008270", "properties": [{"key": "GoTerm", "value": "F:zinc ion binding"}]},
                    {"database": "Pfam", "id": "PF08241", "properties": [{"key": "EntryName", "value": "Methyltransf_12"}]},
                    {"database": "InterPro", "id": "IPR029063", "properties": [{"key": "EntryName", "value": "SAM-dependent_MeTrfase"}]}
                  ]
                }
                """;

        UniProtEntry entry = parser.parse(json);

        assertTrue(entry.reviewed());
        assertEquals(List.of("2.1.1.43"), entry.ecNumbers());
        assertEquals(
                List.of("methyltransferase activity", "zinc ion binding"),
                entry.goMolecularFunctions()
        );
        assertEquals(
                List.of("S-adenosyl-L-methionine + L-lysine ="
                        + " S-adenosyl-L-homocysteine"
                        + " + N(6)-methyl-L-lysine"),
                entry.catalyticActivities()
        );
        assertEquals(
                List.of("S-adenosyl-L-methionine", "substrate"),
                entry.bindingLigands()
        );
        assertEquals(List.of("Proton acceptor"), entry.activeSites());
        assertEquals(List.of("Zn(2+)"), entry.cofactors());
        assertEquals(
                List.of(new UniProtCrossReference(
                        "PF08241",
                        "Methyltransf_12"
                )),
                entry.pfam()
        );
        assertEquals(
                List.of(new UniProtCrossReference(
                        "IPR029063",
                        "SAM-dependent_MeTrfase"
                )),
                entry.interPro()
        );
    }

    @Test
    void defaultsAnnotationFieldsWhenAbsent() throws Exception {
        String json = """
                {
                  "primaryAccession": "P00000",
                  "entryType": "UniProtKB unreviewed (TrEMBL)",
                  "sequence": {"value": "MABC", "length": 4}
                }
                """;

        UniProtEntry entry = parser.parse(json);

        assertFalse(entry.reviewed());
        assertEquals(List.of(), entry.ecNumbers());
        assertEquals(List.of(), entry.goMolecularFunctions());
        assertEquals(List.of(), entry.catalyticActivities());
        assertEquals(List.of(), entry.bindingLigands());
        assertEquals(List.of(), entry.activeSites());
        assertEquals(List.of(), entry.cofactors());
        assertEquals(List.of(), entry.pfam());
        assertEquals(List.of(), entry.interPro());
    }
}
