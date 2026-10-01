package totah.lab.athena.design.reasoning;

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
        PROTECTED_ANCHOR, INELIGIBLE_TRANSFORMATION
    }
    public record Decision(Reference knowledge, Reference hypothesis, DesignState parent,
                           DecisionCode code, String reason, AuthorizedEdit proposal) {
        public Decision {
            Objects.requireNonNull(knowledge); Objects.requireNonNull(hypothesis); Objects.requireNonNull(parent);
            Objects.requireNonNull(code);
            if (reason == null || reason.isBlank() || (code == DecisionCode.PROPOSED) != (proposal != null))
                throw new IllegalArgumentException("decision/proposal mismatch");
        }
    }
    /** Production sinks must durably persist before returning; use Journal.planningDecision. */
    @FunctionalInterface public interface DecisionSink { void record(Decision decision) throws IOException; }

    private final DesignKnowledge knowledge;
    private final List<Reference> requested;
    private final Set<EvidenceKind> permittedEvidenceKinds;
    private final DecisionSink sink;

    /** Inputs are explicit and bounded; this constructor does not search a transformation library. */
    public HypothesisDirectedPlanner(DesignKnowledge knowledge, List<Reference> requested,
                                     Set<EvidenceKind> permittedEvidenceKinds, DecisionSink sink) {
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
        if (!h.applicableParent().equals(parent)) return decline(parent, ref, DecisionCode.NOT_APPLICABLE,
                "parent design state differs from the explicitly authorized applicability snapshot");
        var ids = h.site().candidateAtomIds();
        if (ids.isEmpty() || ids.stream().anyMatch(id -> parent.graph().atom(id).isEmpty()))
            return decline(parent, ref, DecisionCode.UNRESOLVED_SITE, "stable-ID site binding is missing or unresolved");
        if (ids.size() != 1) return decline(parent, ref, DecisionCode.AMBIGUOUS_SITE, "more than one site; an explicit binding is required");
        String atomId = ids.iterator().next();
        var protectedAtoms = new TreeSet<String>();
        var protectedBonds = new TreeSet<String>();
        for (var anchor : h.retainedAnchors()) {
            if (anchor.atomIds().stream().anyMatch(id -> parent.graph().atom(id).isEmpty())
                    || anchor.bondIds().stream().anyMatch(id -> parent.graph().bond(id).isEmpty()))
                return decline(parent, ref, DecisionCode.PROTECTED_ANCHOR, "retained anchor cannot be resolved");
            protectedAtoms.addAll(anchor.atomIds()); protectedBonds.addAll(anchor.bondIds());
        }
        if (protectedAtoms.contains(atomId)) return decline(parent, ref, DecisionCode.PROTECTED_ANCHOR, "replacement would change a retained atom");
        var rule = knowledge.rule(h.eligibleRule());
        if (rule.isEmpty() || !rule.get().enabled()) return decline(parent, ref, DecisionCode.INELIGIBLE_TRANSFORMATION,
                "exact rule version is unavailable or disabled");
        var r = rule.get();
        var atom = parent.graph().atom(atomId).orElseThrow();
        var incident = parent.graph().bonds().stream().filter(b -> b.firstAtomId().equals(atomId) || b.secondAtomId().equals(atomId)).toList();
        if (!atom.element().equals(r.sourceElement()) || atom.formalCharge() != 0 || atom.aromatic()
                || atom.isotope() != null || atom.explicitHydrogens() != 0 || !unspecified(atom.stereochemistry())
                || incident.size() != 1 || incident.getFirst().order() != MolecularGraph.BondOrder.SINGLE
                || incident.getFirst().aromatic() || !unspecified(incident.getFirst().stereochemistry()))
            return decline(parent, ref, DecisionCode.INELIGIBLE_TRANSFORMATION,
                    "rule requires matching neutral, nonaromatic, terminal atom with unspecified H/stereo and one unstereotyped single bond");
        var edit = new GraphEdit(operationId(ref), h.site().vectorId(), GraphEdit.Type.ATOM_SUBSTITUTION,
                Set.of(atomId), Set.of(), null, null, r.replacementElement(), null, Map.of());
        var authorization = new GraphEditTransactionEngine.Authorization(h.site().vectorId(), Set.of(edit.type()),
                Set.of(atomId), protectedAtoms, protectedBonds);
        var provenance = new Provenance(ref, h.supportingEvidence(), h.retainedAnchors(), r.reference(),
                List.of(knowledge.reference()), h.intendedConsequence(), h.geometryConstraints(), false);
        return new Decision(knowledge.reference(), ref, parent, DecisionCode.PROPOSED,
                "reviewed contextual evidence, exact parent, unique site, retained anchors and eligible rule resolved; consequence remains unverified",
                new AuthorizedEdit(edit, authorization, 0, provenance));
    }

    private Decision checkEvidence(DesignState parent, Hypothesis h, Reference ref, boolean conflicting) {
        var evidence = knowledge.evidence(ref);
        if (evidence.isPresent()) {
            var e = evidence.get();
            if (e.reviewStatus() != ReviewStatus.REVIEWED || !e.context().equals(h.context()) || !permittedEvidenceKinds.contains(e.kind()))
                return decline(parent, h.reference(), DecisionCode.INSUFFICIENT_EVIDENCE,
                        "evidence is unreviewed, withdrawn, outside context, or of a disallowed kind: " + ref);
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
        return null;
    }
    private Decision decline(DesignState parent, Reference ref, DecisionCode code, String reason) {
        return new Decision(knowledge.reference(), ref, parent, code, reason, null);
    }
    private static boolean unspecified(String stereo) { return stereo.equals("UNSPECIFIED") || stereo.equals("NONE"); }
    private static String operationId(Reference ref) {
        // Length prefixes prevent delimiter collisions without introducing another identity service.
        return "local-replacement:" + ref.id().length() + ":" + ref.id() + ":" + ref.version().length() + ":" + ref.version();
    }
}
