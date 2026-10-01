package totah.lab.athena.design.reasoning;

import totah.lab.athena.design.backend.CanonicalIdentityService;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.GraphEdit;
import totah.lab.athena.design.backend.GraphEditTransactionEngine;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.generation.MolecularDesignGraphGenerator.AuthorizedEdit;
import totah.lab.athena.design.generation.MolecularDesignGraphGenerator.StateAwarePlanner;
import totah.lab.athena.design.generation.MolecularDesignTree.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import static totah.lab.athena.design.reasoning.DesignKnowledge.*;

/** Checks scientist-authored justification before constructing at most one local edit per requested hypothesis. */
public final class HypothesisDirectedPlanner implements StateAwarePlanner {
    public enum DecisionCode {
        PROPOSED, UNRESOLVED_HYPOTHESIS, MISSING_EVIDENCE, INSUFFICIENT_EVIDENCE,
        CONFLICTING_EVIDENCE, NOT_APPLICABLE, UNRESOLVED_SITE, AMBIGUOUS_SITE,
        PROTECTED_ANCHOR, INELIGIBLE_TRANSFORMATION, NON_COMPARABLE_EVIDENCE, UNRESOLVED_EVIDENCE, UNSUPPORTED_LINEAGE
    }
    public record Decision(Reference knowledge, Reference hypothesis, DesignState parent,
                           DecisionCode code, String reason, AuthorizedEdit proposal, BindingProof binding) {
        public Decision {
            Objects.requireNonNull(knowledge); Objects.requireNonNull(hypothesis); Objects.requireNonNull(parent);
            Objects.requireNonNull(code);
            if (reason == null || reason.isBlank() || (code == DecisionCode.PROPOSED) != (proposal != null))
                throw new IllegalArgumentException("decision/proposal mismatch");
        }
        public Decision(Reference knowledge, Reference hypothesis, DesignState parent, DecisionCode code,
                        String reason, AuthorizedEdit proposal) {
            this(knowledge, hypothesis, parent, code, reason, proposal, null);
        }
    }
    /** Backend correspondence is retained in full; no selected symmetry mapping is presented as unique. */
    public record BindingProof(ReviewedBinding review, CanonicalIdentityService.Result sourceIdentity,
                               CanonicalIdentityService.Result targetIdentity,
                               CanonicalIdentityService.Correspondence correspondence) { }
    /** Production sinks must durably persist before returning; use Journal.planningDecision. */
    @FunctionalInterface public interface DecisionSink { void record(Decision decision) throws IOException; }

    private final DesignKnowledge knowledge;
    private final List<Reference> requested;
    private final Set<EvidenceKind> permittedEvidenceKinds;
    private final DecisionSink sink;
    private final CanonicalIdentityService identity;

    /** Inputs are explicit and bounded; this constructor does not search a transformation library. */
    public HypothesisDirectedPlanner(DesignKnowledge knowledge, List<Reference> requested,
                                     Set<EvidenceKind> permittedEvidenceKinds, DecisionSink sink) {
        this(knowledge, requested, permittedEvidenceKinds, sink, null);
    }

    public HypothesisDirectedPlanner(DesignKnowledge knowledge, List<Reference> requested,
                                     Set<EvidenceKind> permittedEvidenceKinds, DecisionSink sink,
                                     CanonicalIdentityService identity) {
        this.identity = identity;
        this.knowledge = Objects.requireNonNull(knowledge);
        if (requested.isEmpty() || requested.size() > 64 || new HashSet<>(requested).size() != requested.size())
            throw new IllegalArgumentException("request 1..64 unique hypothesis versions");
        this.requested = requested.stream().sorted(Comparator.comparing(Reference::id).thenComparing(Reference::version)).toList();
        this.permittedEvidenceKinds = Set.copyOf(permittedEvidenceKinds);
        this.sink = Objects.requireNonNull(sink);
    }

    @Override public List<AuthorizedEdit> editsFor(DesignState parent) {
        var proposals = new ArrayList<AuthorizedEdit>();
        for (var ref : requested) {
            var decision = decide(parent, ref);
            // Record before releasing any proposal. Persistence failure fails closed through the existing planner-failure path.
            try { sink.record(decision); }
            catch (IOException error) { throw new UncheckedIOException("planning decision persistence failed: " + error.getMessage(), error); }
            if (decision.proposal() != null) proposals.add(decision.proposal());
        }
        return List.copyOf(proposals);
    }

    private Decision decide(DesignState parent, Reference ref) {
        var resolved = knowledge.hypothesis(ref);
        if (resolved.isEmpty()) return decline(parent, ref, DecisionCode.UNRESOLVED_HYPOTHESIS, "exact hypothesis version not found");
        var h = resolved.get();
        if (!h.unresolvedObservations().isEmpty()) return decline(parent, ref, DecisionCode.UNRESOLVED_EVIDENCE,
                "unresolved observations require review; no automatic authorization");
        if (QUALIFIED_SCHEMA.equals(knowledge.schema()) && (h.evaluationCriteria().stream().anyMatch(c -> c.requirement() == null)
                || h.requirements().isEmpty())) return decline(parent, ref, DecisionCode.INSUFFICIENT_EVIDENCE,
                "qualified planning requires explicit evidence requirements and attributable evaluation criteria");
        if (QUALIFIED_SCHEMA.equals(knowledge.schema()) && h.requirements().stream().anyMatch(r ->
                !h.supportingEvidence().contains(r.evidence()) && !h.conflictingEvidence().contains(r.evidence())))
            return decline(parent, ref, DecisionCode.NON_COMPARABLE_EVIDENCE, "unbound evidence requirement cannot be ignored");
        if (h.supportingEvidence().isEmpty()) return decline(parent, ref, DecisionCode.MISSING_EVIDENCE, "hypothesis has no supporting evidence");
        for (var evidence : h.supportingEvidence()) {
            var failure = checkEvidence(parent, h, evidence, false);
            if (failure != null) return failure;
        }
        for (var evidence : h.conflictingEvidence()) {
            var failure = checkEvidence(parent, h, evidence, true);
            if (failure != null) return failure;
        }
        if (!h.conflictingEvidence().isEmpty()) return decline(parent, ref, DecisionCode.CONFLICTING_EVIDENCE,
                "recorded conflict requires scientific review; no automatic authorization");
        BindingProof proof = null;
        var ids = h.site().candidateAtomIds();
        var anchors = h.retainedAnchors();
        var regionProtected = new TreeSet<String>();
        if (!h.applicableParent().equals(parent)) {
            var review = h.binding();
            if (!QUALIFIED_SCHEMA.equals(knowledge.schema()) || review == null || !review.target().equals(parent))
                return decline(parent, ref, DecisionCode.NOT_APPLICABLE, "no reviewed applicability transfer to this exact state");
            var region = review.region();
            if (!region.id().equals(h.site().vectorId()) || ids.size() != 1 || !ids.contains(region.attachmentSite())
                    || !region.allowedTransformationClasses().equals(List.of(GraphEdit.Type.ATOM_SUBSTITUTION.name()))
                    || !region.hardRestrictions().isEmpty() || !region.geometricObjective().isEmpty()
                    || !region.permittedSubstituentClasses().isEmpty())
                return decline(parent, ref, DecisionCode.UNRESOLVED_SITE, "region is ambiguous or contains restrictions this local binder cannot interpret");
            if (identity == null) return decline(parent, ref, DecisionCode.UNSUPPORTED_LINEAGE, "backend correspondence service required");
            try {
                var source = identity.identify(h.applicableParent().graph());
                var target = identity.identify(parent.graph());
                var correspondence = identity.correspondence(h.applicableParent().graph(), parent.graph());
                proof = new BindingProof(review, source, target, correspondence);
                if (!source.canonicalKey().equals(target.canonicalKey())
                        || !source.evidence().backend().equals(target.evidence().backend())
                        || !source.evidence().version().equals(target.evidence().version()) || !correspondence.exhaustive()
                        || correspondence.alternatives().isEmpty())
                    return decline(parent, ref, DecisionCode.UNSUPPORTED_LINEAGE, "identity or exhaustive correspondence not established", proof);
                if (correspondence.ambiguous()) return decline(parent, ref, DecisionCode.AMBIGUOUS_SITE,
                        "multiple symmetry-equivalent mappings; no unique region lineage", proof);
                var mapping = correspondence.selected();
                if (!preservesState(h.applicableParent().graph(), parent.graph(), mapping))
                    return decline(parent, ref, DecisionCode.UNSUPPORTED_LINEAGE, "only complete correspondence with unchanged coordinates/metadata is supported", proof);
                ids = mapped(ids, mapping.atoms());
                var rebound = new ArrayList<RetainedAnchor>();
                for (var anchor : anchors) rebound.add(new RetainedAnchor(anchor.id(), mapped(anchor.atomIds(), mapping.atoms()),
                        mapped(anchor.bondIds(), mapping.bonds()), anchor.allowNewIncidentBonds()));
                anchors = List.copyOf(rebound);
                regionProtected.addAll(mapped(region.protectedNeighborhood(), mapping.atoms()));
            } catch (MolecularBackendException | IllegalArgumentException error) {
                return decline(parent, ref, DecisionCode.UNSUPPORTED_LINEAGE, "binding failed: " + error.getMessage(), proof);
            }
        }
        if (ids.isEmpty() || ids.stream().anyMatch(id -> parent.graph().atom(id).isEmpty()))
            return decline(parent, ref, DecisionCode.UNRESOLVED_SITE, "stable-ID site binding is missing or unresolved", proof);
        if (ids.size() != 1) return decline(parent, ref, DecisionCode.AMBIGUOUS_SITE, "more than one site; an explicit binding is required", proof);
        String atomId = ids.iterator().next();
        var protectedAtoms = new TreeSet<String>();
        var protectedBonds = new TreeSet<String>();
        protectedAtoms.addAll(regionProtected);
        for (var anchor : anchors) {
            if (anchor.atomIds().stream().anyMatch(id -> parent.graph().atom(id).isEmpty())
                    || anchor.bondIds().stream().anyMatch(id -> parent.graph().bond(id).isEmpty()))
                return decline(parent, ref, DecisionCode.PROTECTED_ANCHOR, "retained anchor cannot be resolved", proof);
            protectedAtoms.addAll(anchor.atomIds()); protectedBonds.addAll(anchor.bondIds());
        }
        if (protectedAtoms.contains(atomId)) return decline(parent, ref, DecisionCode.PROTECTED_ANCHOR, "replacement would change a retained atom", proof);
        var rule = knowledge.rule(h.eligibleRule());
        if (rule.isEmpty() || !rule.get().enabled()) return decline(parent, ref, DecisionCode.INELIGIBLE_TRANSFORMATION,
                "exact rule version is unavailable or disabled", proof);
        var r = rule.get();
        var atom = parent.graph().atom(atomId).orElseThrow();
        var incident = parent.graph().bonds().stream().filter(b -> b.firstAtomId().equals(atomId) || b.secondAtomId().equals(atomId)).toList();
        if (!atom.element().equals(r.sourceElement()) || atom.formalCharge() != 0 || atom.aromatic()
                || atom.isotope() != null || atom.explicitHydrogens() != 0 || !unspecified(atom.stereochemistry())
                || incident.size() != 1 || incident.getFirst().order() != MolecularGraph.BondOrder.SINGLE
                || incident.getFirst().aromatic() || !unspecified(incident.getFirst().stereochemistry()))
            return decline(parent, ref, DecisionCode.INELIGIBLE_TRANSFORMATION,
                    "rule requires matching neutral, nonaromatic, terminal atom with unspecified H/stereo and one unstereotyped single bond", proof);
        var edit = new GraphEdit(operationId(ref), h.site().vectorId(), GraphEdit.Type.ATOM_SUBSTITUTION,
                Set.of(atomId), Set.of(), null, null, r.replacementElement(), null, Map.of());
        var authorization = new GraphEditTransactionEngine.Authorization(h.site().vectorId(), Set.of(edit.type()),
                Set.of(atomId), protectedAtoms, protectedBonds);
        var provenance = new Provenance(ref, h.supportingEvidence(), anchors, r.reference(),
                proof == null ? List.of(knowledge.reference()) : List.of(knowledge.reference(), proof.review().reference(), proof.review().grammar(), proof.review().reviewer()), h.intendedConsequence(), h.geometryConstraints(), false);
        return new Decision(knowledge.reference(), ref, parent, DecisionCode.PROPOSED,
                "reviewed contextual evidence, exact parent, unique site, retained anchors and eligible rule resolved; consequence remains unverified",
                new AuthorizedEdit(edit, authorization, 0, provenance), proof);
    }

    private Decision checkEvidence(DesignState parent, Hypothesis h, Reference ref, boolean conflicting) {
        var evidence = knowledge.evidence(ref);
        if (evidence.isPresent()) {
            var e = evidence.get();
            if (e.reviewStatus() != ReviewStatus.REVIEWED || !e.context().equals(h.context()) || !permittedEvidenceKinds.contains(e.kind()))
                return decline(parent, h.reference(), DecisionCode.INSUFFICIENT_EVIDENCE,
                        "evidence is unreviewed, withdrawn, outside context, or of a disallowed kind: " + ref);
            if (QUALIFIED_SCHEMA.equals(knowledge.schema())) {
                var required = h.requirements().stream().filter(r -> r.evidence().equals(ref)).findFirst();
                if (required.isEmpty() || !required.get().requirement().accepts(e)
                        || !h.applicableParent().equals(e.qualification().subject()))
                    return decline(parent, h.reference(), DecisionCode.NON_COMPARABLE_EVIDENCE,
                            "evidence does not satisfy explicit method/endpoint/system/condition/unit/status/state requirement: " + ref);
            }
            return null;
        }
        var observation = knowledge.evaluation(ref);
        if (observation.isEmpty()) return decline(parent, h.reference(), DecisionCode.MISSING_EVIDENCE, "unresolved evidence version: " + ref);
        var e = observation.get();
        var source = knowledge.hypothesis(e.hypothesis());
        boolean conclusion = conflicting ? e.findings().stream().anyMatch(f -> f.conclusion() == Conclusion.CONTRADICTS)
                : !e.findings().isEmpty() && e.findings().stream().allMatch(f -> f.conclusion() == Conclusion.SUPPORTS);
        if (source.isEmpty() || !source.get().context().equals(h.context()) || !source.get().reference().id().equals(h.reference().id())
                || e.reviewStatus() != ReviewStatus.REVIEWED || !permittedEvidenceKinds.contains(e.kind()) || !conclusion)
            return decline(parent, h.reference(), DecisionCode.INSUFFICIENT_EVIDENCE, "observation is not applicable support/conflict: " + ref);
        if (QUALIFIED_SCHEMA.equals(knowledge.schema()) && (!source.get().evaluationCriteria().equals(h.evaluationCriteria())
                || !source.get().applicableParent().equals(h.applicableParent())
                || e.findings().stream().filter(f -> f.evidence() != null).anyMatch(f -> knowledge.evidence(f.evidence())
                        .map(raw -> !permittedEvidenceKinds.contains(raw.kind())).orElse(true))))
            return decline(parent, h.reference(), DecisionCode.NON_COMPARABLE_EVIDENCE, "observation source kinds or criteria differ");
        return null;
    }
    private static Set<String> mapped(Set<String> ids, Map<String, String> mapping) {
        var result = new TreeSet<String>();
        for (String id : ids) {
            if (!mapping.containsKey(id)) throw new IllegalArgumentException("unresolved region/anchor ID: " + id);
            result.add(mapping.get(id));
        }
        return Set.copyOf(result);
    }
    private static boolean preservesState(MolecularGraph a, MolecularGraph b, CanonicalIdentityService.Mapping mapping) {
        if (!mapping.atoms().keySet().equals(a.atoms().stream().map(MolecularGraph.Atom::id).collect(java.util.stream.Collectors.toSet()))
                || !new HashSet<>(mapping.atoms().values()).equals(b.atoms().stream().map(MolecularGraph.Atom::id).collect(java.util.stream.Collectors.toSet()))
                || mapping.atoms().size() != b.atoms().size()
                || !mapping.bonds().keySet().equals(a.bonds().stream().map(MolecularGraph.Bond::id).collect(java.util.stream.Collectors.toSet()))
                || !new HashSet<>(mapping.bonds().values()).equals(b.bonds().stream().map(MolecularGraph.Bond::id).collect(java.util.stream.Collectors.toSet()))
                || mapping.bonds().size() != b.bonds().size() || !a.properties().equals(b.properties())) return false;
        for (var atom : a.atoms()) {
            var other = b.atom(mapping.atoms().get(atom.id())).orElseThrow();
            if (!Objects.equals(atom.coordinates(), other.coordinates()) || !atom.properties().equals(other.properties())) return false;
        }
        for (var bond : a.bonds()) if (!bond.properties().equals(b.bond(mapping.bonds().get(bond.id())).orElseThrow().properties())) return false;
        return true;
    }
    private Decision decline(DesignState parent, Reference ref, DecisionCode code, String reason) {
        return decline(parent, ref, code, reason, null);
    }
    private Decision decline(DesignState parent, Reference ref, DecisionCode code, String reason, BindingProof proof) {
        return new Decision(knowledge.reference(), ref, parent, code, reason, null, proof);
    }
    private static boolean unspecified(String stereo) { return stereo.equals("UNSPECIFIED") || stereo.equals("NONE"); }
    private static String operationId(Reference ref) {
        // Length prefixes prevent delimiter collisions without introducing another identity service.
        return "local-replacement:" + ref.id().length() + ":" + ref.id() + ":" + ref.version().length() + ":" + ref.version();
    }
}
