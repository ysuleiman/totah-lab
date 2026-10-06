package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Enumerates source-role tuples in explicit partner scopes; no inferred chemistry. */
final class HbondCandidateEnumeration {
    private HbondCandidateEnumeration() { }
    private record Scope(List<SystemStateView.Component> components, boolean complete) { }
    static ObjectNode build(SystemStateView state, RuleManifest manifest, RuleRequest request,
                            List<EvidenceEnvelope> inputs) throws Exception {
        var sources = new HbondCandidateSources(state, manifest, request, inputs);
        var roles = HbondCandidateRules.parameter(manifest, "roles");
        var first = scope(state, request.first()); var second = scope(state, request.second());
        var out = HbondCandidateRules.JSON.createObjectNode();
        out.put("definition", "I02.EXPLICIT_H_DIRECTIONAL_CANDIDATE/1");
        out.set("stateBinding", HbondCandidateRules.node(state.binding()));
        out.set("request", HbondCandidateRules.node(request)); out.set("sourceReports", HbondCandidateRules.node(sources.pins()));
        var pairs = out.putArray("classPairs"); boolean anyPositive = false, allComplete = true;
        int used = 0;
        for (var donorClass : names(roles.get("donors"))) for (var acceptorClass : names(roles.get("acceptors"))) {
            var pair = pairs.addObject(); pair.put("donorClass", donorClass); pair.put("acceptorClass", acceptorClass);
            var candidates = pair.putArray("candidates"); var reasons = new TreeSet<String>();
            boolean complete = first.complete() && second.complete() && state.frameQualified(); boolean positive = false;
            if (donorClass.equals("DONOR.PYRIDINIUM_NH")) {
                pair.put("supportedDomain", false); pair.put("assessment", UNKNOWN_INCONCLUSIVE.name());
                pair.put("complete", false); pair.putArray("reasons").add("pyridinium class pair remains unsupported");
                continue; // Explicitly excluded pairs never count as evaluated negatives.
            }
            pair.put("supportedDomain", true);
            var donorDescriptors = roles.get("donors").get(donorClass);
            var acceptorDescriptors = roles.get("acceptors").get(acceptorClass);
            var contextDescriptors = roles.get("contexts").get(acceptorClass);
            for (var dc : first.components()) for (var ac : second.components()) {
                if (dc.identity().equals(ac.identity()) || crossBond(state, dc, ac)) {
                    complete = false; reasons.add("same-component or conflicting intercomponent covalent scope"); continue;
                }
                complete &= sources.complete(dc, donorDescriptors) && sources.complete(ac, acceptorDescriptors)
                        && (contextDescriptors == null || sources.complete(ac, contextDescriptors));
                var donors = sources.anchors(dc, donorDescriptors);
                var acceptors = sources.anchors(ac, acceptorDescriptors);
                var contexts = contextDescriptors == null ? List.<HbondCandidateSources.Anchor>of() : sources.anchors(ac, contextDescriptors);
                for (var donor : donors) {
                    var hs = hydrogens(state, donor);
                    if (hs.isEmpty()) { complete = false; reasons.add("complete explicit donor-H state unavailable"); continue; }
                    for (var acceptor : acceptors) {
                        var matching = contexts.stream().filter(a -> a.atom().equals(acceptor.atom())).toList();
                        if (contextDescriptors != null && matching.isEmpty()) continue;
                        for (var h : hs) {
                            if (++used > request.maximumCandidates()) { complete = false; reasons.add("candidate budget incomplete"); continue; }
                            var geometry = HbondCandidateGeometry.measure(state, manifest, request, donor, h, acceptor);
                            Double da = HbondCandidateGeometry.value(geometry, 0, "distanceAngstrom");
                            Double ha = HbondCandidateGeometry.value(geometry, 1, "distanceAngstrom");
                            Double dha = HbondCandidateGeometry.value(geometry, 2, "angleDegrees");
                            boolean evaluable = da != null && ha != null && dha != null && da > 0 && ha > 0;
                            if (!evaluable) { complete = false; reasons.add("unevaluable/degenerate source geometry"); }
                            boolean qualifies = evaluable && da <= Double.parseDouble(manifest.parameters().get("distance").value())
                                    && dha >= Double.parseDouble(manifest.parameters().get("angle").value());
                            positive |= qualifies;
                            var tuple = candidates.addObject(); tuple.set("donor", HbondCandidateRules.node(donor.atom()));
                            tuple.set("hydrogen", HbondCandidateRules.node(h)); tuple.set("acceptor", HbondCandidateRules.node(acceptor.atom()));
                            tuple.set("donorOccurrence", donor.occurrence()); tuple.set("acceptorOccurrence", acceptor.occurrence());
                            tuple.set("acceptorContextOccurrences", HbondCandidateRules.node(matching.stream().map(HbondCandidateSources.Anchor::occurrence).toList()));
                            tuple.set("geometry", geometry); tuple.put("assessment", (qualifies ? SUPPORTED_PRESENT : evaluable ? ABSENT_FALSE : UNKNOWN_INCONCLUSIVE).name());
                        }
                    }
                }
            }
            if (!complete) reasons.add("supported scope/source coverage/search/geometry incomplete");
            pair.put("complete", complete); pair.put("assessment", (positive ? SUPPORTED_PRESENT : complete ? ABSENT_FALSE : UNKNOWN_INCONCLUSIVE).name());
            pair.set("reasons", HbondCandidateRules.node(reasons)); anyPositive |= positive; allComplete &= complete;
        }
        out.put("completeSupportedScope", allComplete);
        out.put("assessment", (anyPositive ? SUPPORTED_PRESENT : allComplete ? ABSENT_FALSE : UNKNOWN_INCONCLUSIVE).name());
        return out;
    }
    private static List<String> names(JsonNode node) {
        var names = new TreeSet<String>(); node.fieldNames().forEachRemaining(names::add); return List.copyOf(names);
    }
    private static Scope scope(SystemStateView s, List<ResidueId> requested) {
        var selected = new HashSet<>(requested); var covered = new TreeSet<AtomReference>();
        var components = new ArrayList<SystemStateView.Component>(); boolean complete = true;
        for (var c : s.components().stream().sorted(Comparator.comparing(c -> SystemStateView.digest(c.identity()))).toList()) {
            if (c.correspondenceAlternatives().size() != 1) { complete = false; continue; }
            var atoms = c.correspondenceAlternatives().getFirst().values();
            if (atoms.stream().noneMatch(a -> selected.contains(SystemStateView.residue(a)))) continue;
            if (atoms.stream().anyMatch(a -> !selected.contains(SystemStateView.residue(a)))) { complete = false; continue; }
            components.add(c); covered.addAll(atoms);
        }
        var actual = new TreeSet<AtomReference>(); s.atoms().keySet().stream().filter(a -> selected.contains(SystemStateView.residue(a))).forEach(actual::add);
        return new Scope(List.copyOf(components), complete && !actual.isEmpty() && covered.equals(actual));
    }
    private static List<AtomReference> hydrogens(SystemStateView state, HbondCandidateSources.Anchor donor) {
        var c = donor.report().component(); var coverage = donor.report().payload().path("sourceCoverage").path("atomState").path(donor.atomId());
        if (coverage.path("hydrogenMode").asText().equals("UNKNOWN") || !coverage.path("implicitHydrogenCount").isIntegralNumber()
                || coverage.path("implicitHydrogenCount").asInt() != 0) return List.of();
        var hs = new TreeSet<AtomReference>(); var mapping = c.correspondenceAlternatives().getFirst();
        for (var bond : c.chemistry().bonds()) {
            String other = bond.firstAtomId().equals(donor.atomId()) ? bond.secondAtomId()
                    : bond.secondAtomId().equals(donor.atomId()) ? bond.firstAtomId() : null;
            if (other == null || !c.chemistry().atom(other).orElseThrow().element().equals("H")) continue;
            var h = mapping.get(other);
            if (bond.order() != MolecularGraph.BondOrder.SINGLE || h == null || !state.atoms().containsKey(h)
                    || state.graph().structure().bonds().stream().noneMatch(b -> b.order().name().equals("SINGLE")
                        && (b.atom1().equals(donor.atom()) && b.atom2().equals(h) || b.atom2().equals(donor.atom()) && b.atom1().equals(h)))) return List.of();
            hs.add(h);
        }
        return List.copyOf(hs);
    }
    private static boolean crossBond(SystemStateView s, SystemStateView.Component a, SystemStateView.Component b) {
        if (a.correspondenceAlternatives().size() != 1 || b.correspondenceAlternatives().size() != 1) return true;
        var aa = new HashSet<>(a.correspondenceAlternatives().getFirst().values());
        var bb = new HashSet<>(b.correspondenceAlternatives().getFirst().values());
        return s.graph().structure().bonds().stream().anyMatch(edge -> aa.contains(edge.atom1()) && bb.contains(edge.atom2())
                || aa.contains(edge.atom2()) && bb.contains(edge.atom1()));
    }
}
