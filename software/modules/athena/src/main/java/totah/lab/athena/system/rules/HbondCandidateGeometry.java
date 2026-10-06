package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;

/** Composes existing continuous geometry; no independent distance/angle implementation. */
final class HbondCandidateGeometry {
    private HbondCandidateGeometry() { }
    static JsonNode measure(SystemStateView state, RuleManifest rule, RuleRequest request,
                            HbondCandidateSources.Anchor donor, AtomReference hydrogen,
                            HbondCandidateSources.Anchor acceptor) throws Exception {
        var geometry = RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(rule, "geometry")));
        var plan = HbondCandidateRules.JSON.createObjectNode();
        plan.put("schema", "athena-continuous-geometry-plan/1");
        plan.set("stateBinding", HbondCandidateRules.node(state.binding()));
        plan.put("coordinateUnit", "ANGSTROM"); plan.putArray("groups"); plan.putNull("radiusAssignmentReference");
        plan.set("coordinateSourceReferences", HbondCandidateRules.node(state.sources()));
        plan.set("sourceReferences", HbondCandidateRules.node(List.of(donor.report().envelope().reference(), acceptor.report().envelope().reference())));
        plan.putArray("limitations").add("Explicit source D/H/A attribution; raw geometry independent of candidate classification");
        var operations = plan.putArray("operations");
        operation(operations.addObject(), "DA", "DISTANCE", donor.atom(), acceptor.atom());
        operation(operations.addObject(), "HA", "DISTANCE", hydrogen, acceptor.atom());
        operation(operations.addObject(), "DHA", "ANGLE", donor.atom(), hydrogen, acceptor.atom());
        var component = acceptor.report().component();
        var graph = component.chemistry(); var mapping = component.correspondenceAlternatives().getFirst();
        var antecedents = new TreeSet<String>();
        for (var bond : graph.bonds()) {
            String other = bond.firstAtomId().equals(acceptor.atomId()) ? bond.secondAtomId()
                    : bond.secondAtomId().equals(acceptor.atomId()) ? bond.firstAtomId() : null;
            if (other != null && !graph.atom(other).orElseThrow().element().equals("H")) antecedents.add(other);
        }
        for (var id : antecedents) {
            operation(operations.addObject(), "HAX:" + id, "ANGLE", hydrogen, acceptor.atom(), mapping.get(id));
            operation(operations.addObject(), "DAX:" + id, "ANGLE", donor.atom(), acceptor.atom(), mapping.get(id));
        }
        var selected = new TreeSet<AtomReference>();
        for (var operation : operations) for (var atom : operation.get("atoms")) selected.add(HbondCandidateRules.JSON.treeToValue(atom, AtomReference.class));
        var gr = new RuleRequest(state.binding(), geometry.key(), RuleRegistry.digest(geometry), List.copyOf(selected), List.of(), List.of(),
                request.radiusAngstrom(), 0, request.maximumNodes(), request.maximumCandidates());
        byte[] bytes = SystemStateView.bytes(plan); String hash = EvidenceExchange.sha256(bytes);
        var parent = donor.report().envelope();
        var envelope = new EvidenceEnvelope(new ScientificReference(ScientificReference.Kind.EVIDENCE_ENVELOPE,
                "athena.hbond-candidate.plan", hash, "1"), "athena:continuous-geometry-plan", "application/json", "1",
                Optional.of(Base64.getEncoder().encodeToString(bytes)), Optional.empty(), hash, parent.provenance(),
                HbondCandidateRules.method(rule, false), parent.context(), List.of(state.subject()), List.of(),
                List.of("Derived explicit measurement plan; parent evidence is preserved"), parent.recordedAt());
        var finding = ContinuousGeometryRules.analyzer(geometry, gr, false).analyze(state, List.of(envelope), Map.of()).getFirst();
        var out = HbondCandidateRules.JSON.createObjectNode();
        out.set("plan", plan); out.set("measurements", HbondCandidateRules.JSON.readTree(finding.measurements().get("payload")));
        return out;
    }
    private static void operation(ObjectNode n, String id, String kind, AtomReference... atoms) {
        n.put("id", id); n.put("kind", kind); n.set("atoms", HbondCandidateRules.node(List.of(atoms)));
    }
    static Double value(JsonNode geometry, int operation, String quantity) {
        var q = geometry.path("measurements").path("operations").path(operation).path("quantities").path(quantity);
        if (!q.path("status").asText().equals("SUPPORTED_PRESENT") || !q.hasNonNull("value")) return null;
        double value = Double.parseDouble(q.get("value").asText());
        return Double.isFinite(value) ? value : null;
    }
}
