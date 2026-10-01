package totah.lab.hermes.pubchem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PubChemMolecularWeightImporterTest {
    @TempDir Path temporary;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant TIME = Instant.parse("2026-10-01T16:00:00Z");
    private final PubChemMolecularWeightImporter importer = new PubChemMolecularWeightImporter();
    private static Path fixture() { return Path.of(System.getProperty("basedir"),"src/test/resources/pubchem/cid-702-molecular-weight.json"); }
    private ObjectNode importBytes(byte[] bytes) throws Exception {
        var raw=temporary.resolve(UUID.randomUUID()+".raw"); var receipt=temporary.resolve(UUID.randomUUID()+".json");
        Files.write(raw,bytes); importer.importRecord(raw,receipt,TIME); return importer.readVerified(receipt);
    }
    private static ObjectNode information(ObjectNode root) {
        return (ObjectNode)root.path("Record").path("Section").get(0).path("Section").get(0).path("Section").get(0).path("Information").get(0);
    }
    @Test void exactRealSourceBytesMetadataAndUnknownUncertaintySurviveImport() throws Exception {
        byte[] raw=Files.readAllBytes(fixture()); var receipt=importBytes(raw);
        assertArrayEquals(raw,Base64.getDecoder().decode(receipt.path("rawBase64").asText()));
        assertEquals("216e71490909a95ed03acb2728412128fb778f276c7f429e30eb6b858fdea5ad",receipt.path("sha256").asText());
        assertEquals("CID:702",receipt.path("identifier").asText()); assertEquals("2025.04.14",receipt.path("sourceVersion").asText());
        assertEquals("46.07",receipt.path("valueText").asText()); assertEquals("g/mol",receipt.path("unit").asText());
        assertEquals("2.2",receipt.path("method").path("version").asText()); assertEquals("POINT_UNKNOWN",receipt.path("uncertaintyKind").asText());
        assertEquals("",receipt.path("uncertaintyText").asText()); assertTrue(receipt.path("conditions").isEmpty());
        assertTrue(receipt.path("errors").isEmpty()); assertFalse(receipt.path("sourceUse").isEmpty());
        assertEquals(receipt,importBytes(raw));
    }
    @Test void receiptsCannotBeOverwrittenOrSilentlyReprojected() throws Exception {
        var path=temporary.resolve("receipt.json"); importer.importRecord(fixture(),path,TIME);
        assertThrows(FileAlreadyExistsException.class,()->importer.importRecord(fixture(),path,TIME));
        ObjectNode changed=JSON.readValue(Files.readAllBytes(path),ObjectNode.class); changed.put("valueText","999");
        var corrupt=temporary.resolve("corrupt.json"); Files.write(corrupt,JSON.writeValueAsBytes(changed));
        assertThrows(java.io.IOException.class,()->importer.readVerified(corrupt));
    }
    @Test void missingMethodUnitAndAdditionalConditionsRemainParseErrors() throws Exception {
        for(String field:List.of("method","unit","conditions","interval")) {
            ObjectNode doc=JSON.readValue(Files.readAllBytes(fixture()),ObjectNode.class);var info=information(doc);
            switch(field) {
                case "method" -> info.putArray("Reference");
                case "unit" -> ((ObjectNode)info.path("Value")).remove("Unit");
                case "conditions" -> info.put("Conditions","temperature=300 K");
                default -> ((ObjectNode)info.path("Value")).put("Error",0.1);
            }
            var receipt=importBytes(JSON.writeValueAsBytes(doc)); assertFalse(receipt.path("errors").isEmpty(),field);
        }
    }
    @Test void unsupportedUnitIsPreservedWithoutConversion() throws Exception {
        ObjectNode doc=JSON.readValue(Files.readAllBytes(fixture()),ObjectNode.class);
        ((ObjectNode)information(doc).path("Value")).put("Unit","kg/mol");
        var receipt=importBytes(JSON.writeValueAsBytes(doc)); assertEquals("kg/mol",receipt.path("unit").asText());
        assertEquals("46.07",receipt.path("valueText").asText()); assertFalse(receipt.path("errors").isEmpty());
    }
    @Test void malformedAndDuplicateJsonRemainPreservableRejectedRawInputs() throws Exception {
        for(String raw:List.of("not json","{\"Record\":{},\"Record\":{}}", "{} {}")) {
            var receipt=importBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)); assertFalse(receipt.path("errors").isEmpty());
            assertEquals("UNAVAILABLE",receipt.path("uncertaintyKind").asText());
            assertEquals(raw,new String(Base64.getDecoder().decode(receipt.path("rawBase64").asText()),java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    @Test void thirdPartyAndAmbiguousValuesCannotMasqueradeAsComputedPubChemEvidence() throws Exception {
        for(String variant:List.of("third-party","multiple")) {
            ObjectNode doc=JSON.readValue(Files.readAllBytes(fixture()),ObjectNode.class);
            if(variant.equals("third-party")) ((ObjectNode)doc.path("Record").path("Reference").get(0)).put("SourceName","Other provider");
            else ((ObjectNode)information(doc).path("Value")).withArray("StringWithMarkup").addObject().put("String","47");
            assertFalse(importBytes(JSON.writeValueAsBytes(doc)).path("errors").isEmpty());
        }
    }
}
