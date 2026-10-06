package totah.lab.athena.system.rules;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import totah.lab.athena.system.SystemStateView;
import totah.lab.mnemosyne.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Internal codecs for the three approved event payloads; never a new graph authority. */
final class EventPayload {
    static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static final String PLAN = "athena:event-analysis-plan", SET = "athena:typed-event-set";
    private EventPayload() { }
    static JsonNode read(EvidenceEnvelope e) throws IOException {
        byte[] bytes = e.readPayload();
        require(EvidenceExchange.sha256(bytes).equals(e.payloadSha256()), "artifact hash mismatch");
        var n = JSON.readTree(bytes); require(n != null && n.isObject(), "object payload required"); return n;
    }
    static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
    static String text(JsonNode n, String field) {
        var v = n.get(field); require(v != null && v.isTextual() && !v.asText().isBlank(), "missing text: " + field); return v.asText();
    }
    static String digest(JsonNode n, String field) {
        String s = text(n, field); require(s.matches("[0-9a-f]{64}"), "invalid digest: " + field); return s;
    }
    static List<JsonNode> array(JsonNode n, String field) {
        var v = n.get(field); require(v != null && v.isArray(), "array required: " + field);
        var out = new ArrayList<JsonNode>(); v.forEach(out::add); return List.copyOf(out);
    }
    static String canonical(Object n) { return new String(SystemStateView.bytes(n instanceof JsonNode ? JSON.convertValue(n,Object.class) : n), StandardCharsets.UTF_8); }
    static String hash(Object n) { return EvidenceExchange.sha256(canonical(n).getBytes(StandardCharsets.UTF_8)); }
    static ScientificReference reference(JsonNode n) throws IOException { return JSON.treeToValue(n, ScientificReference.class); }
    static void fields(JsonNode n, String... names) {
        require(n != null && n.isObject(), "object required"); var actual = new HashSet<String>(); n.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(Set.of(names)), "unknown or missing payload fields: " + actual);
    }
    static SystemStateView.Binding binding(JsonNode n) throws IOException {
        fields(n,"state","stateSha256","chemicalSha256","coordinateSha256","correspondenceSha256");
        for (String f : List.of("stateSha256","chemicalSha256","coordinateSha256","correspondenceSha256")) digest(n,f);
        reference(n.get("state")); return JSON.treeToValue(n, SystemStateView.Binding.class);
    }
    static String eventKey(JsonNode n) throws IOException {
        fields(n,"definitionSha256","direction","roles","correspondenceAlternative"); digest(n,"definitionSha256");
        require(Set.of("DIRECTED","UNDIRECTED").contains(text(n,"direction")), "event direction");
        text(n,"correspondenceAlternative"); var roles = array(n,"roles"); require(!roles.isEmpty(), "empty event roles");
        var names = new HashSet<String>();
        for(var role:roles) {
            fields(role,"role","entity"); require(names.add(text(role,"role")), "duplicate role name");
            JSON.treeToValue(role.get("entity"), EvidenceSubject.class);
        }
        return hash(n); // Ordered roles and explicit alternatives are part of identity.
    }
    static Map<String,EvidenceEnvelope> index(List<EvidenceEnvelope> inputs) throws IOException {
        var result = new TreeMap<String,EvidenceEnvelope>();
        for (var e:inputs) {
            e.verifyArtifact(); String key = canonical(e.reference()); var old = result.putIfAbsent(key,e);
            require(old == null || old.equals(e), "conflicting selected artifact identity");
        }
        return Collections.unmodifiableMap(result);
    }
    static EvidenceEnvelope pinned(JsonNode pin, Map<String,EvidenceEnvelope> inputs) throws IOException {
        fields(pin,"reference","sha256"); reference(pin.get("reference")).require(ScientificReference.Kind.EVIDENCE_ENVELOPE);
        var found=inputs.get(canonical(pin.get("reference")));
        require(found != null && found.payloadSha256().equals(digest(pin,"sha256")), "missing or changed explicitly selected artifact");
        found.verifyArtifact(); return found;
    }
    static Map<String,Object> pin(EvidenceEnvelope e) { return Map.of("reference",e.reference(),"sha256",e.payloadSha256()); }
}
