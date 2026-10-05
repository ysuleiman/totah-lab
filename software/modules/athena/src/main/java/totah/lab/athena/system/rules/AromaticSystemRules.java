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

/** Read-side attribution of reviewed B00 cycle occurrences; never a second ring perceiver. */
final class AromaticSystemRules {
    private AromaticSystemRules() { }
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> GROUPS = List.of("AROMATIC.RING5", "AROMATIC.RING6",
            "HETEROAROMATIC.RING5", "HETEROAROMATIC.RING6");
    private static final List<String> NEGATIVE = List.of("VERIFIED_SOURCE_REPORTS", "COMPLETE_GENERAL_5_6_ENUMERATION",
            "COMPLETE_SOURCE_CORRESPONDENCE", "WITHIN_REQUEST_BUDGET");
    private static final String SCOPE = "SUPPLIED_COMPONENT_B00_AROMATIC_5_6_CYCLES";

    static void validate(RuleManifest m) {
        if (!m.schema().equals("athena-rule/2") || !m.ruleId().equals("ATHENA.PERCEPTION.AROMATIC.SYSTEM")
                || !m.implementationVersion().equals("1") || !m.profile().equals("ATHENA_AROMATIC_SYSTEMS_V1")
                || m.family() != RuleManifest.Family.MOTIF || !m.requiredCapabilities().isEmpty()
                || !m.parameters().keySet().equals(Set.of("definitionReference", "sourceManifests"))
                || !m.negativeCoverage().requirements().equals(NEGATIVE))
            throw new IllegalArgumentException("aromatic system manifest contract");
        try { definition(m); sources(m); }
        catch (Exception e) { throw new IllegalArgumentException("invalid aromatic system pins", e); }
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
        if (!array.isArray() || array.size() != GROUPS.size()) throw new IllegalArgumentException("four pinned cycle definitions required");
        for (var value : array) {
            var source = RuleRegistry.decode(SystemStateView.bytes(value));
            if (!source.implementationId().equals("athena.group") || !source.implementationVersion().equals("2")
                    || source.retired() || !GROUPS.contains(source.ruleId().replace("ATHENA.GROUP.", ""))
                    || result.put(source.ruleId(), source) != null)
                throw new IllegalArgumentException("distinct reviewed cycle sources required");
        }
        return result;
    }

    static SystemGraphAnalyzer analyzer(RuleManifest m, RuleRequest r, boolean evaluate) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method() { return methodReference(m, evaluate); }
            public Set<SystemGraphCertificate.Capability> requires() { return Set.of(); }
            public Set<SystemGraphCertificate.Capability> qualifies() { return Set.of(); }
            public Set<String> evidenceTypes() { return Set.of("athena:group-identities", "athena:aromatic-systems", "athena:system-state"); }
            public List<Finding> analyze(SystemStateView state, List<EvidenceEnvelope> inputs, Map<String,String> config) throws Exception {
                if (!state.binding().equals(r.state()) || !m.key().equals(r.manifestKey())
                        || !RuleRegistry.digest(m).equals(r.manifestSha256()) || !r.atoms().isEmpty()
                        || !r.first().isEmpty() || !r.second().isEmpty())
                    throw new IllegalArgumentException("aromatic request requires explicit whole source component scope");
                // Input envelopes must already be preserved by the pipeline. Parsing never mutates them.
                var report = build(state, m, r, inputs);
                if (evaluate) {
                    var supplied = inputs.stream().filter(e -> e.evidenceType().equals("athena:aromatic-systems")).toList();
                    if (supplied.size() != 1 || !supplied.getFirst().method().equals(methodReference(m, false))
                            || !report.equals(JSON.readTree(supplied.getFirst().readPayload())))
                        throw new IllegalArgumentException("aromatic report provenance/replay mismatch");
                }
                var assessment = EvidenceInterpretation.Status.valueOf(report.path("coverage").path("assessment").asText());
                var status = evaluate ? assessment : SUPPORTED_PRESENT;
                if (evaluate && (m.retired() || m.qualification() != SystemGraphCertificate.Status.QUALIFIED)) status = NOT_EVALUATED;
                return List.of(new Finding(evaluate ? "evaluate" : "collect", List.of(state.subject()), status,
                        Map.of("payload", report.toString(), "reportDigest", SystemStateView.digest(report), "assessment", assessment.name()),
                        List.of("bounded cycle/system identity only"), m.limitations()));
            }
        };
    }

    private static ScientificReference methodReference(RuleManifest m, boolean evaluate) {
        return new ScientificReference(ScientificReference.Kind.METHOD, "athena.aromatic-systems",
                m.key() + (evaluate ? "/evaluate" : "/collect"), RuleRegistry.digest(m));
    }

    private static ObjectNode build(SystemStateView state, RuleManifest m, RuleRequest request,
                                    List<EvidenceEnvelope> inputs) throws Exception {
        var manifests = sources(m);
        var reports = inputs.stream().filter(e -> e.evidenceType().equals("athena:group-identities"))
                .sorted(Comparator.comparing(e -> SystemStateView.digest(e.reference()))).toList();
        var reportPins = new ArrayList<Object>();
        var seenReferences = new HashSet<ScientificReference>();
        var seenDefinitions = new HashSet<String>();
        var reasons = new TreeSet<String>();
        var completeGeneral = new HashSet<String>();
        var cycles = new TreeMap<String, Cycle>();
        SystemStateView.Component component = null;
        for (var envelope : reports) {
            if (!seenReferences.add(envelope.reference())) throw new IllegalArgumentException("duplicate/conflicting source report reference");
            var bytes = envelope.readPayload(); // checks external-artifact hash as well
            if (!EvidenceExchange.sha256(bytes).equals(envelope.payloadSha256())) throw new IllegalArgumentException("source hash mismatch");
            var report = JSON.readTree(bytes);
            var source = manifests.get(report.path("definition").path("groupId").asText());
            if (source == null || !seenDefinitions.add(source.ruleId())) throw new IllegalArgumentException("unreviewed/duplicate cycle definition");
            var sourceRequest = new RuleRequest(state.binding(), source.key(), RuleRegistry.digest(source), List.of(), List.of(), List.of(),
                    request.radiusAngstrom(), request.maximumHops(), request.maximumNodes(), request.maximumCandidates());
            // Existing evaluator replays B00 receipts and validates the exact definition, state, source chemistry and method.
            FunctionalGroupRules.analyzer(source, sourceRequest, null, true).analyze(state, List.of(envelope), Map.of());
            var current = state.components().stream().filter(c -> node(c.identity()).equals(report.path("componentReference")))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("source component unavailable"));
            if (component != null && !component.identity().equals(current.identity()))
                throw new IllegalArgumentException("source reports disagree on component");
            component = current;
            reportPins.add(Map.of("reference", envelope.reference(), "payloadSha256", envelope.payloadSha256()));
            boolean complete = true;
            for (var value : report.path("negativeCoverage")) complete &= value.isBoolean() && value.booleanValue();
            if (complete && source.ruleId().startsWith("ATHENA.GROUP.AROMATIC.")) completeGeneral.add(source.ruleId());
            if (!complete) reasons.add("incomplete source coverage: " + source.ruleId());
            for (var occurrence : report.path("occurrences")) {
                for (var roles : occurrence.path("roleCorrespondenceAlternatives")) {
                    int size = source.ruleId().endsWith("5") ? 5 : 6;
                    var ordered = new ArrayList<String>();
                    for (int i = 0; i < size; i++) {
                        var role = roles.path("vertex" + i);
                        if (!role.isArray() || role.size() != 1 || !role.get(0).isTextual()) throw new IllegalArgumentException("cycle vertex role missing");
                        ordered.add(role.get(0).textValue());
                    }
                    if (new HashSet<>(ordered).size() != size) throw new IllegalArgumentException("cycle is not simple");
                    var bonds = new TreeSet<String>();
                    for (int i = 0; i < size; i++) {
                        String a = ordered.get(i), b = ordered.get((i + 1) % size);
                        var edges = current.chemistry().bonds().stream().filter(edge -> joins(edge, a, b)).toList();
                        if (edges.size() != 1) throw new IllegalArgumentException("cycle edge is absent/ambiguous");
                        bonds.add(edges.getFirst().id());
                    }
                    // Whole source maps, never a Cartesian product of individual atom alternatives.
                    for (var map : current.correspondenceAlternatives()) {
                        var atoms = new TreeMap<String, AtomReference>();
                        for (var id : ordered) {
                            var atom = map.get(id);
                            if (atom == null || !state.atoms().containsKey(atom)) throw new IllegalArgumentException("cycle correspondence unavailable");
                            atoms.put(id, atom);
                        }
                        String key = SystemStateView.digest(Map.of("state", state.binding(), "component", current.identity(),
                                "bonds", bonds, "mapping", atoms));
                        var cycle = cycles.computeIfAbsent(key, ignored -> new Cycle(key, current.identity(), bonds, atoms));
                        cycle.origins.put(SystemStateView.digest(Map.of("reportReference", envelope.reference(), "occurrenceId", occurrence.path("occurrenceId").asText())),
                                Map.of("reportReference", envelope.reference(), "occurrenceId", occurrence.path("occurrenceId").asText()));
                    }
                }
            }
        }
        boolean complete = completeGeneral.size() == 2 && component != null && component.correspondenceAlternatives().size() == 1;
        if (completeGeneral.size() != 2) reasons.add("complete general RING5 and RING6 reports required for absence");
        if (component == null || component.correspondenceAlternatives().size() != 1) reasons.add("complete unambiguous source correspondence unavailable");
        if (cycles.size() > request.maximumCandidates() || (component != null && component.chemistry().atoms().size() > request.maximumNodes())) {
            // Do not silently drop positive identities. Do not claim negative completeness outside requested bounds.
            complete = false; reasons.add("request budget exceeded; source occurrences retained");
        }
        var relations = new ArrayList<Object>();
        var adjacency = new TreeMap<String, SortedSet<String>>();
        cycles.keySet().forEach(id -> adjacency.put(id, new TreeSet<>()));
        var orderedCycles = new ArrayList<>(cycles.values());
        for (int i = 0; i < orderedCycles.size(); i++) for (int j = i + 1; j < orderedCycles.size(); j++) {
            var a = orderedCycles.get(i); var b = orderedCycles.get(j);
            // Only simultaneous whole-map correspondence may establish a relation.
            if (!jointlyMapped(component, a, b)) continue;
            var sharedAtoms = new TreeSet<String>(a.atoms.keySet()); sharedAtoms.retainAll(b.atoms.keySet());
            var sharedBonds = new TreeSet<>(a.bonds); sharedBonds.retainAll(b.bonds);
            var kind = relationKind(sharedAtoms, sharedBonds);
            if (kind.isEmpty()) continue;
            relations.add(Map.of("firstCycleId", a.id, "secondCycleId", b.id,
                    "correspondenceAlternativeIndices", List.of(0, 0), "kind", kind.orElseThrow(),
                    "sharedAtoms", sortedAtoms(sharedAtoms.stream().map(a.atoms::get).toList()), "sharedSourceBondIds", sharedBonds));
            if (!sharedBonds.isEmpty()) { adjacency.get(a.id).add(b.id); adjacency.get(b.id).add(a.id); }
        }
        var systems = new TreeMap<String, Object>();
        // Existing group reports suppress positives with ambiguous source correspondence. Keep that fail-closed contract.
        // The guard also prevents pairwise compatibility from fabricating globally incompatible systems.
        if (component != null && component.correspondenceAlternatives().size() == 1) {
            var unseen = new TreeSet<>(cycles.keySet());
            while (!unseen.isEmpty()) {
                var members = new TreeSet<String>(); var queue = new ArrayDeque<String>(); queue.add(unseen.first());
                while (!queue.isEmpty()) { var id = queue.remove(); if (!members.add(id)) continue; unseen.remove(id); queue.addAll(adjacency.get(id)); }
                var atoms = new HashSet<AtomReference>(); members.forEach(id -> atoms.addAll(cycles.get(id).atoms.values()));
                var indices = new ArrayList<Integer>();
                for (int i = 0; i < relations.size(); i++) {
                    var relation = (Map<?, ?>) relations.get(i);
                    if (members.contains(relation.get("firstCycleId")) && members.contains(relation.get("secondCycleId"))) indices.add(i);
                }
                var atomList = sortedAtoms(atoms);
                var id = SystemStateView.digest(Map.of("state", state.binding(), "component", component.identity(), "cycles", members, "atoms", atomList));
                systems.put(id, Map.of("systemId", id, "cycleIds", members, "memberAtoms", atomList, "relationIndices", indices));
            }
        }
        var out = JSON.createObjectNode(); out.put("schema", "athena-aromatic-systems/1");
        out.set("stateBinding", node(state.binding())); out.set("definitionReference", node(definition(m)));
        out.set("sourceReports", node(reportPins)); out.set("cycles", node(cycles.values().stream().map(Cycle::payload).toList()));
        out.set("relations", node(relations)); out.set("systems", node(systems.values()));
        out.set("coverage", node(Map.of("assessment", !cycles.isEmpty() ? SUPPORTED_PRESENT : complete ? ABSENT_FALSE : UNKNOWN_INCONCLUSIVE,
                "completeEligibleCycleEnumeration", complete, "scope", SCOPE, "reasons", reasons)));
        out.set("limitations", node(m.limitations())); return out;
    }

    /** Pure structural relation; no aromaticity is inferred from an intersection. */
    static Optional<String> relationKind(Set<String> sharedAtoms, Set<String> sharedBonds) {
        if (sharedAtoms.isEmpty()) {
            if (!sharedBonds.isEmpty()) throw new IllegalArgumentException("bond intersection without atom endpoints");
            return Optional.empty();
        }
        if (!sharedBonds.isEmpty() && sharedAtoms.size() < 2) throw new IllegalArgumentException("shared bond requires endpoints");
        return Optional.of(sharedBonds.isEmpty() ? "SHARED_ATOM_ONLY" : "SHARED_BOND");
    }

    private static boolean jointlyMapped(SystemStateView.Component c, Cycle a, Cycle b) {
        return c != null && c.correspondenceAlternatives().stream().anyMatch(map -> a.atoms.entrySet().stream().allMatch(e -> e.getValue().equals(map.get(e.getKey())))
                && b.atoms.entrySet().stream().allMatch(e -> e.getValue().equals(map.get(e.getKey()))));
    }
    private static boolean joins(MolecularGraph.Bond b, String a, String c) {
        return b.firstAtomId().equals(a) && b.secondAtomId().equals(c) || b.firstAtomId().equals(c) && b.secondAtomId().equals(a);
    }
    private static List<AtomReference> sortedAtoms(Collection<AtomReference> atoms) {
        return atoms.stream().distinct().sorted(Comparator.comparing(a -> new String(SystemStateView.bytes(a), java.nio.charset.StandardCharsets.UTF_8))).toList();
    }
    private static JsonNode node(Object value) {
        try { return JSON.readTree(SystemStateView.bytes(value)); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("cannot encode aromatic attribution", e); }
    }
    private static final class Cycle {
        final String id; final ScientificReference component; final SortedSet<String> bonds;
        final SortedMap<String, AtomReference> atoms;
        final SortedMap<String, Object> origins = new TreeMap<>();
        Cycle(String id, ScientificReference component, SortedSet<String> bonds, SortedMap<String, AtomReference> atoms) {
            this.id = id; this.component = component; this.bonds = bonds; this.atoms = atoms;
        }
        Object payload() { return Map.of("cycleId", id, "componentReference", component, "sourceOccurrences", origins.values(),
                "correspondenceAlternatives", List.of(Map.of("memberAtoms", sortedAtoms(atoms.values()), "sourceBondIds", bonds))); }
    }
}
