package totah.lab.hermes.pubchem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** One offline PUG-View endpoint; emits a versioned receipt, never a reviewed reasoning object. */
public final class PubChemMolecularWeightImporter {
    public static final String SCHEMA = "pubchem-molecular-weight-source/1";
    public static final String VERSION = "1";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final Pattern METHOD = Pattern.compile("Computed by PubChem ([0-9.]+) \\(PubChem release ([0-9.]+)\\)");
    private static final String CLAIM = "PubChem-reported computed molecular weight only; not experimental mass, affinity, potency or biological function";

    /** Raw bytes and parsed projection are stored together; no overwrite and no live network dependency. */
    public void importRecord(Path raw, Path destination, Instant importedAt) throws IOException {
        byte[] bytes = Files.readAllBytes(raw);
        var receipt = parse(bytes, importedAt);
        byte[] encoded = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(receipt);
        try (var channel = FileChannel.open(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(encoded);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    /** Reparse the preserved bytes before handing the projection to a reviewer. */
    public ObjectNode readVerified(Path path) throws IOException {
        JsonNode stored;
        try (var input = Files.newInputStream(path)) { stored = JSON.readTree(input); }
        return verifyReceipt(stored);
    }

    public ObjectNode verifyReceipt(JsonNode stored) throws IOException {
        if (stored == null || !stored.isObject()) throw new IOException("source receipt object required");
        try {
            var reconstructed = parse(Base64.getDecoder().decode(stored.path("rawBase64").asText()),
                    Instant.parse(stored.path("importedAt").asText()));
            if (!reconstructed.equals(stored)) throw new IOException("source receipt does not match original bytes and importer semantics");
            return reconstructed;
        } catch (IllegalArgumentException error) { throw new IOException("invalid source receipt", error); }
    }

    /** Dedicated structural accompaniment to the same CID property; no general endpoint importer. */
    public ObjectNode structure(Path path, String expectedIdentifier) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        return verifyStructure(raw, expectedIdentifier);
    }
    public ObjectNode verifyStructure(byte[] raw, String expectedIdentifier) throws IOException {
        var document = JSON.readTree(raw);
        if (document == null || !document.isObject()) throw new IOException("source structure object required");
        var values = document.path("PropertyTable").path("Properties");
        if (!values.isArray() || values.size() != 1 || !values.get(0).path("CID").isIntegralNumber()
                || !values.get(0).path("CID").canConvertToLong() || values.get(0).path("CID").asLong() <= 0
                || !("CID:" + values.get(0).path("CID").asLong()).equals(expectedIdentifier)
                || !values.get(0).path("SMILES").isTextual() || values.get(0).path("SMILES").asText().isBlank())
            throw new IOException("one matching CID with source isomeric SMILES required");
        var result = JSON.createObjectNode(); result.put("identifier", expectedIdentifier);
        result.put("smiles", values.get(0).path("SMILES").asText());
        result.put("rawBase64", Base64.getEncoder().encodeToString(raw)); result.put("sha256", hash(raw));
        result.put("parser", "PubChemMolecularWeightImporter/" + VERSION + ":structure/1");
        result.put("uri", "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + values.get(0).path("CID").asLong() + "/property/IsomericSMILES/JSON");
        return result;
    }

    private ObjectNode parse(byte[] raw, Instant importedAt) throws IOException {
        var receipt = JSON.createObjectNode();
        receipt.put("schema", SCHEMA); receipt.put("provider", "PubChem");
        receipt.put("importedAt", Objects.requireNonNull(importedAt).toString());
        receipt.put("rawBase64", Base64.getEncoder().encodeToString(raw)); receipt.put("sha256", hash(raw));
        receipt.set("importer", reference("PubChemMolecularWeightImporter", VERSION));
        receipt.put("identifier", "UNAVAILABLE"); receipt.put("sourceVersion", ""); receipt.put("uri", "");
        receipt.put("title", ""); receipt.put("context", "PubChem computed molecular weight");
        receipt.set("endpoint", reference("pubchem:MolecularWeight", "1"));
        receipt.putNull("method"); receipt.putNull("system"); receipt.put("unit", ""); receipt.put("valueText", "");
        receipt.put("valueKind", "QUANTITATIVE"); receipt.putObject("conditions");
        receipt.put("uncertaintyKind", "UNAVAILABLE"); receipt.put("uncertaintyText", "");
        receipt.put("claimBoundary", CLAIM);
        var use = receipt.putObject("sourceUse");
        use.put("policy", "https://www.ncbi.nlm.nih.gov/home/about/policies/");
        use.put("scope", "PubChem-computed data; acknowledge NCBI. Third-party annotations are outside this importer.");
        var warnings = receipt.putArray("warnings");
        warnings.add("No conditions reported; empty conditions do not assert equivalence to any specified assay conditions");
        warnings.add("No uncertainty inferred; a point representation or displayed precision is not an error estimate");
        var errors = receipt.putArray("errors");
        try {
            JsonNode doc = JSON.readTree(raw);
            var record = doc.path("Record");
            if (!record.path("RecordType").asText().equals("CID") || !record.path("RecordNumber").canConvertToLong()
                    || record.path("RecordNumber").asLong() <= 0) throw new IllegalArgumentException("one positive CID record required");
            String cid = Long.toString(record.path("RecordNumber").asLong());
            receipt.put("identifier", "CID:" + cid); receipt.put("title", record.path("RecordTitle").asText());
            receipt.put("uri", "https://pubchem.ncbi.nlm.nih.gov/rest/pug_view/data/compound/" + cid + "/JSON?heading=Molecular%20Weight");
            var physical = section(record, "Chemical and Physical Properties");
            var computed = section(physical, "Computed Properties");
            var weight = section(computed, "Molecular Weight");
            if (weight.path("Information").size() != 1) throw new IllegalArgumentException("exactly one molecular weight observation required");
            var info = weight.path("Information").get(0);
            var infoKeys = new HashSet<String>(); info.fieldNames().forEachRemaining(infoKeys::add);
            if (!infoKeys.equals(Set.of("ReferenceNumber", "Reference", "Value")))
                errors.add("unsupported information metadata; additional conditions require importer review");
            var sources = new ArrayList<JsonNode>();
            for (var ref : record.path("Reference")) if (ref.path("ReferenceNumber").equals(info.path("ReferenceNumber"))) sources.add(ref);
            if (sources.size() != 1 || !sources.getFirst().path("SourceName").asText().equals("PubChem")
                    || !sources.getFirst().path("SourceID").asText().equals("PubChem"))
                throw new IllegalArgumentException("only PubChem's own computed property is supported");
            if (info.path("Reference").size() != 1) throw new IllegalArgumentException("one explicit computation attribution required");
            var method = METHOD.matcher(info.path("Reference").get(0).asText());
            if (!method.matches()) throw new IllegalArgumentException("missing or unsupported computation method/release");
            receipt.put("sourceVersion", method.group(2));
            var m = receipt.putObject("method"); m.put("name", "PubChem"); m.put("version", method.group(1));
            m.putObject("parameters").put("release", method.group(2));
            receipt.set("system", reference("PubChem:CID:" + cid, method.group(2)));
            var value = info.path("Value");
            receipt.put("unit", value.path("Unit").asText());
            if (!receipt.path("unit").asText().equals("g/mol")) errors.add("missing or unsupported source unit; no conversion allowed");
            if (value.path("StringWithMarkup").size() != 1) throw new IllegalArgumentException("one literal point value required");
            var point = value.path("StringWithMarkup").get(0);
            if (point.size() != 1 || !point.has("String")) errors.add("unsupported point annotation");
            String number = point.path("String").asText();
            receipt.put("valueText", number);
            if (!number.matches("[0-9]+(?:\\.[0-9]+)?") || !Double.isFinite(Double.parseDouble(number)))
                throw new IllegalArgumentException("unsupported non-point or nonfinite molecular weight");
            // Unknown fields could encode conditions/error bars. Do not silently treat a new source shape as a known point.
            var keys = new HashSet<String>(); value.fieldNames().forEachRemaining(keys::add);
            if (!keys.equals(Set.of("StringWithMarkup", "Unit"))) errors.add("unsupported value metadata; additional semantics require review of importer");
            if (errors.isEmpty()) receipt.put("uncertaintyKind", "POINT_UNKNOWN");
        } catch (IllegalArgumentException | NullPointerException error) {
            errors.add("parse rejected: " + error.getMessage());
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            errors.add("malformed source JSON");
        }
        return receipt;
    }
    private static JsonNode section(JsonNode parent, String heading) {
        var matches = new ArrayList<JsonNode>();
        for (var item : parent.path("Section")) if (item.path("TOCHeading").asText().equals(heading)) matches.add(item);
        if (matches.size() != 1) throw new IllegalArgumentException("one " + heading + " section required");
        return matches.getFirst();
    }
    private static ObjectNode reference(String id, String version) {
        var ref = JSON.createObjectNode(); ref.put("id", id); ref.put("version", version); return ref;
    }
    private static String hash(byte[] raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
