package totah.lab.athena.system.rules;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Read-side charge attribution over existing group occurrences; no new chemical perception. */
final class ChargeGroupRules {
    private ChargeGroupRules() { }
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> GROUPS = List.of("CARBOXYLATE", "CARBOXYLATE.FORMYL",
            "AMMONIUM.PRIMARY_CARBON_BOUND", "AMMONIUM.SECONDARY_CARBON_BOUND",
            "AMMONIUM.TERTIARY_CARBON_BOUND", "AMMONIUM.QUATERNARY_CARBON_BOUND");
    private static final List<String> NEGATIVE = List.of("VERIFIED_SOURCE_REPORTS", "COMPLETE_SIX_GROUP_ENUMERATION",
            "COMPLETE_SOURCE_CORRESPONDENCE", "WITHIN_REQUEST_BUDGET");
    private static final String SCOPE = "SUPPLIED_COMPONENT_REVIEWED_CARBOXYLATE_AMMONIUM";

    static void validate(RuleManifest m) {
        if (!m.schema().equals("athena-rule/2") || !m.ruleId().equals("ATHENA.PERCEPTION.CHARGED_GROUP")
                || !m.implementationVersion().equals("1") || !m.profile().equals("ATHENA_CHARGE_GROUPS_V1")
                || m.family() != RuleManifest.Family.MOTIF || !m.requiredCapabilities().isEmpty()
                || !m.parameters().keySet().equals(Set.of("definitionReference", "sourceManifests"))
                || !m.negativeCoverage().requirements().equals(NEGATIVE))
            throw new IllegalArgumentException("charge group manifest contract");
        try { definition(m); sources(m); }
        catch (Exception e) { throw new IllegalArgumentException("invalid charge group pins", e); }
    }

    private static ScientificReference definition(RuleManifest m) throws Exception {
        var ref = JSON.readValue(m.parameters().get("definitionReference").value(), ScientificReference.class);
        ref.require(ScientificReference.Kind.SOURCE);
        if (!ref.version().matches("[0-9a-f]{64}")) throw new IllegalArgumentException("definition digest required");
        return ref;
    }

    private static Map<String, RuleManifest> sources(RuleManifest m) throws Exception {
        var result = new TreeMap<String, RuleManifest>();
        var array = JSON.readTree(m.parameters().get("sourceManifests").value());
        if (!array.isArray() || array.size() != GROUPS.size()) throw new IllegalArgumentException("six pinned group definitions required");
        for (var value : array) {
            var source = RuleRegistry.decode(SystemStateView.bytes(value));
            if (!source.implementationId().equals("athena.group") || !source.implementationVersion().equals(source.ruleId().startsWith("ATHENA.GROUP.CARBOXYLATE") ? "1" : "2")
                    || source.retired() || !GROUPS.contains(source.ruleId().replace("ATHENA.GROUP.", ""))
                    || result.put(source.ruleId(), source) != null)
                throw new IllegalArgumentException("distinct reviewed group sources required");
        }
        return result;
    }

    static SystemGraphAnalyzer analyzer(RuleManifest m, RuleRequest r, boolean evaluate) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method() { return methodReference(m, evaluate); }
            public Set<SystemGraphCertificate.Capability> requires() { return Set.of(); }
            public Set<SystemGraphCertificate.Capability> qualifies() { return Set.of(); }
            public Set<String> evidenceTypes() { return Set.of("athena:group-identities", "athena:charge-groups", "athena:system-state"); }
            public List<Finding> analyze(SystemStateView state, List<EvidenceEnvelope> inputs, Map<String,String> config) throws Exception {
                if (!state.binding().equals(r.state()) || !m.key().equals(r.manifestKey())
                        || !RuleRegistry.digest(m).equals(r.manifestSha256()) || !r.atoms().isEmpty()
                        || !r.first().isEmpty() || !r.second().isEmpty())
                    throw new IllegalArgumentException("charge request requires explicit whole source component scope");
                // Input envelopes must already be preserved by the pipeline. Parsing never mutates them.
                var report = build(state, m, r, inputs);
                if (evaluate) {
                    var supplied = inputs.stream().filter(e -> e.evidenceType().equals("athena:charge-groups")).toList();
                    if (supplied.size() != 1 || !supplied.getFirst().method().equals(methodReference(m, false))
                            || !report.equals(JSON.readTree(supplied.getFirst().readPayload())))
                        throw new IllegalArgumentException("charge report provenance/replay mismatch");
                }
                var assessment = EvidenceInterpretation.Status.valueOf(report.path("coverage").path("assessment").asText());
                var status = evaluate ? assessment : SUPPORTED_PRESENT;
                if (evaluate && (m.retired() || m.qualification() != SystemGraphCertificate.Status.QUALIFIED)) status = NOT_EVALUATED;
                return List.of(new Finding(evaluate ? "evaluate" : "collect", List.of(state.subject()), status,
                        Map.of("payload", report.toString(), "reportDigest", SystemStateView.digest(report), "assessment", assessment.name()),
                        List.of("supplied group formal charge only"), m.limitations()));
            }
        };
    }

    private static ScientificReference methodReference(RuleManifest m, boolean evaluate) {
        return new ScientificReference(ScientificReference.Kind.METHOD, "athena.charge-groups",
                m.key() + (evaluate ? "/evaluate" : "/collect"), RuleRegistry.digest(m));
    }

    private static ObjectNode build(SystemStateView state, RuleManifest m, RuleRequest request,
                                    List<EvidenceEnvelope> inputs) throws Exception {
        var manifests = sources(m);
        var reports = inputs.stream().filter(e -> e.evidenceType().equals("athena:group-identities"))
                .sorted(Comparator.comparing(e -> SystemStateView.digest(e.reference()))).toList();
        var pins = new ArrayList<Object>();
        var references = new HashSet<ScientificReference>();
        var definitions = new HashSet<String>();
        var completeDefinitions = new HashSet<String>();
        var reasons = new TreeSet<String>();
        var groups = new TreeMap<String,Object>();
        SystemStateView.Component component = null;
        for (var envelope : reports) {
            if (!references.add(envelope.reference())) throw new IllegalArgumentException("duplicate/conflicting source reference");
            var bytes = envelope.readPayload();
            if (!EvidenceExchange.sha256(bytes).equals(envelope.payloadSha256())) throw new IllegalArgumentException("source hash mismatch");
            var report = JSON.readTree(bytes);
            var source = manifests.get(report.path("definition").path("groupId").asText());
            if (source == null || !definitions.add(source.ruleId())) throw new IllegalArgumentException("unreviewed/duplicate source definition");
            var sourceRequest = new RuleRequest(state.binding(), source.key(), RuleRegistry.digest(source), List.of(), List.of(), List.of(),
                    request.radiusAngstrom(), request.maximumHops(), request.maximumNodes(), request.maximumCandidates());
            FunctionalGroupRules.analyzer(source, sourceRequest, null, true).analyze(state, List.of(envelope), Map.of());
            var current = state.components().stream().filter(c -> node(c.identity()).equals(report.path("componentReference")))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("source component unavailable"));
            if (component != null && !component.identity().equals(current.identity())) throw new IllegalArgumentException("different source components");
            component = current;
            pins.add(Map.of("reference", envelope.reference(), "payloadSha256", envelope.payloadSha256()));
            boolean complete = true;
            for (var value : report.path("negativeCoverage")) complete &= value.isBoolean() && value.booleanValue();
            if (complete) completeDefinitions.add(source.ruleId()); else reasons.add("incomplete source coverage: " + source.ruleId());
            for (var occurrence : report.path("occurrences")) {
                // The verified upstream domain forbids positive incomplete/ambiguous structural mapping.
                if (current.correspondenceAlternatives().size() != 1) throw new IllegalArgumentException("positive source lacks complete correspondence");
                var ids = new TreeSet<String>();
                for (var id : occurrence.path("memberAtomIds")) {
                    if (!id.isTextual() || !ids.add(id.textValue())) throw new IllegalArgumentException("invalid/duplicate source member");
                }
                if (ids.isEmpty()) throw new IllegalArgumentException("empty source occurrence");
                var alternatives = new ArrayList<Object>();
                for (var map : current.correspondenceAlternatives()) {
                    var members = new ArrayList<Object>();
                    for (var id : ids) {
                        var atom = current.chemistry().atom(id).orElseThrow(); var ref = map.get(id);
                        if (ref == null || !state.atoms().containsKey(ref) || !state.charges().charges().containsKey(ref)
                                || state.charges().charge(ref) != atom.formalCharge()) throw new IllegalArgumentException("source charge/mapping mismatch");
                        members.add(Map.of("chemicalAtomId", id, "atomReference", ref, "formalCharge", atom.formalCharge()));
                    }
                    // Never read a supplied total or a legacy ChargedGroup. Compute from authoritative source atoms.
                    alternatives.add(Map.of("members", members, "totalFormalCharge", total(current.chemistry(), ids)));
                }
                var id = SystemStateView.digest(Map.of("state", state.binding(), "component", current.identity(),
                        "definition", report.path("definitionDigest").asText(), "members", ids, "mapping", alternatives));
                if (groups.put(id, Map.of("groupId", id, "componentReference", current.identity(),
                        "sourceOccurrences", List.of(Map.of("reportReference", envelope.reference(), "occurrenceId", occurrence.path("occurrenceId").asText())),
                        "correspondenceAlternatives", alternatives)) != null) throw new IllegalArgumentException("duplicate source occurrence");
            }
        }
        boolean complete = completeDefinitions.size() == GROUPS.size() && component != null && component.correspondenceAlternatives().size() == 1;
        if (completeDefinitions.size() != GROUPS.size()) reasons.add("exhaustive verified enumeration of all six source predicates required for absence");
        if (component == null || component.correspondenceAlternatives().size() != 1) reasons.add("complete source correspondence unavailable");
        if (groups.size() > request.maximumCandidates() || component != null && component.chemistry().atoms().size() > request.maximumNodes()) {
            complete = false; reasons.add("request budget exceeded; positive source identities retained");
        }
        var out = JSON.createObjectNode(); out.put("schema", "athena-charge-groups/1");
        out.set("stateBinding", node(state.binding())); out.set("definitionReference", node(definition(m)));
        out.set("sourceReports", node(pins)); out.set("groups", node(groups.values()));
        out.set("coverage", node(Map.of("assessment", !groups.isEmpty() ? SUPPORTED_PRESENT : complete ? ABSENT_FALSE : UNKNOWN_INCONCLUSIVE,
                "completeEligibleGroupEnumeration", complete, "scope", SCOPE, "reasons", reasons)));
        out.set("limitations", node(m.limitations())); return out;
    }

    static int total(MolecularGraph graph, Collection<String> memberIds) {
        int total = 0;
        for (var id : new TreeSet<>(memberIds)) total = Math.addExact(total, graph.atom(id).orElseThrow().formalCharge());
        return total;
    }
    private static JsonNode node(Object value) {
        try { return JSON.readTree(SystemStateView.bytes(value)); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("cannot encode charge attribution", e); }
    }
}
