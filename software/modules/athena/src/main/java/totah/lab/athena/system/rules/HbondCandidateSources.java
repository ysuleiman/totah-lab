package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;

/** Verifies existing group evidence; does not perceive chemistry or infer hydrogen state. */
final class HbondCandidateSources {
    record Report(SystemStateView.Component component, JsonNode payload, EvidenceEnvelope envelope) {
        boolean complete() {
            var coverage = payload.path("negativeCoverage");
            if (!coverage.isObject() || coverage.isEmpty()) return false;
            for (var value : coverage) if (!value.isBoolean() || !value.booleanValue()) return false;
            return true;
        }
    }
    record Anchor(Report report, String atomId, AtomReference atom, JsonNode occurrence) { }
    private final Map<String, Report> reports = new TreeMap<>();
    private final List<Object> pins = new ArrayList<>();

    HbondCandidateSources(SystemStateView state, RuleManifest manifest, RuleRequest request,
                          List<EvidenceEnvelope> inputs) throws Exception {
        var sources = HbondCandidateRules.parameter(manifest, "sources");
        var references = new HashSet<ScientificReference>();
        for (var envelope : inputs.stream().filter(e -> e.evidenceType().equals("athena:group-identities"))
                .sorted(Comparator.comparing(e -> SystemStateView.digest(e.reference()))).toList()) {
            if (!references.add(envelope.reference())) throw new IllegalArgumentException("duplicate/conflicting group reference");
            byte[] bytes = envelope.readPayload();
            if (!EvidenceExchange.sha256(bytes).equals(envelope.payloadSha256())) throw new IllegalArgumentException("group hash mismatch");
            var payload = HbondCandidateRules.JSON.readTree(bytes);
            String id = payload.path("definition").path("groupId").asText();
            if (!sources.has(id)) throw new IllegalArgumentException("unselected source definition: " + id);
            var source = RuleRegistry.decode(SystemStateView.bytes(sources.get(id)));
            var sr = new RuleRequest(state.binding(), source.key(), RuleRegistry.digest(source), List.of(), List.of(), List.of(),
                    request.radiusAngstrom(), request.maximumHops(), request.maximumNodes(), request.maximumCandidates());
            FunctionalGroupRules.analyzer(source, sr, null, true).analyze(state, List.of(envelope), Map.of());
            var component = state.components().stream().filter(c -> HbondCandidateRules.node(c.identity()).equals(payload.get("componentReference")))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("source component missing"));
            if (reports.put(key(component, id), new Report(component, payload, envelope)) != null)
                throw new IllegalArgumentException("conflicting/duplicate component definition evidence");
            pins.add(Map.of("reference", envelope.reference(), "payloadSha256", envelope.payloadSha256(),
                    "method", envelope.method(), "provenance", envelope.provenance()));
        }
    }
    List<Object> pins() { return List.copyOf(pins); }
    Report report(SystemStateView.Component c, String id) { return reports.get(key(c, id)); }
    boolean complete(SystemStateView.Component c, JsonNode descriptors) {
        if (descriptors == null || descriptors.isEmpty()) return false;
        for (var descriptor : descriptors) {
            var report = report(c, descriptor.path("id").asText());
            if (report == null || !report.complete()) return false;
        }
        return true;
    }
    List<Anchor> anchors(SystemStateView.Component c, JsonNode descriptors) {
        var found = new TreeMap<String, Anchor>();
        if (descriptors == null || c.correspondenceAlternatives().size() != 1) return List.of();
        var mapping = c.correspondenceAlternatives().getFirst();
        for (var descriptor : descriptors) {
            var report = report(c, descriptor.path("id").asText());
            if (report == null) continue;
            for (var occurrence : report.payload().path("occurrences")) {
                for (var alternative : occurrence.path("roleCorrespondenceAlternatives")) {
                    for (var atom : alternative.path(descriptor.path("role").asText())) {
                        String id = atom.asText();
                        if (!mapping.containsKey(id)) throw new IllegalArgumentException("source anchor mapping missing");
                        // Full occurrence retains every correspondence alternative, even when the anchor is identical.
                        String key = id + ":" + occurrence.path("occurrenceId").asText();
                        found.put(key, new Anchor(report, id, mapping.get(id), occurrence));
                    }
                }
            }
        }
        return List.copyOf(found.values());
    }
    private static String key(SystemStateView.Component c, String definition) {
        return SystemStateView.digest(c.identity()) + ":" + definition;
    }
}
