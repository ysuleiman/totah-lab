package totah.lab.athena.design.reasoning;

import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.generation.MolecularDesignTree.*;

import java.util.*;

/** Immutable, version-addressed scientific inputs, not a molecule store or an inference engine. */
public record DesignKnowledge(String schema, Reference reference, List<Evidence> evidence,
                              List<Hypothesis> hypotheses, List<ReplacementRule> rules,
                              List<Evaluation> evaluations) {
    public static final String SCHEMA = "athena-design-knowledge/1";

    public DesignKnowledge {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported knowledge schema");
        Objects.requireNonNull(reference);
        evidence = sorted(evidence, Evidence::reference);
        hypotheses = sorted(hypotheses, Hypothesis::reference);
        rules = sorted(rules, ReplacementRule::reference);
        evaluations = sorted(evaluations, Evaluation::reference);
        var identities = new HashSet<Reference>();
        identities.add(reference);
        for (var list : List.of(evidence.stream().map(Evidence::reference).toList(),
                hypotheses.stream().map(Hypothesis::reference).toList(),
                rules.stream().map(ReplacementRule::reference).toList(),
                evaluations.stream().map(Evaluation::reference).toList())) {
            for (var id : list) if (!identities.add(id)) throw new IllegalArgumentException("duplicate reference: " + id);
        }
        var byId = new HashMap<Reference, Hypothesis>();
        hypotheses.forEach(h -> byId.put(h.reference(), h));
        for (var h : hypotheses) {
            var seen = new HashSet<Reference>();
            for (var cursor = h; cursor != null; cursor = cursor.previousVersion() == null ? null : byId.get(cursor.previousVersion())) {
                if (!seen.add(cursor.reference())) throw new IllegalArgumentException("cyclic hypothesis revisions");
                if (cursor.previousVersion() != null && !byId.containsKey(cursor.previousVersion()))
                    throw new IllegalArgumentException("unresolved previous hypothesis version");
            }
        }
        for (var evaluation : evaluations) {
            var h = byId.get(evaluation.hypothesis());
            if (h == null || !h.applicableParent().equals(evaluation.attempt().parentDesignState()))
                throw new IllegalArgumentException("evaluation does not resolve to its hypothesis and parent state");
            var expected = h.evaluationCriteria().stream().map(Criterion::id).collect(java.util.stream.Collectors.toSet());
            var found = evaluation.findings().stream().map(Finding::criterionId).toList();
            if (found.size() != expected.size() || !new HashSet<>(found).equals(expected))
                throw new IllegalArgumentException("each hypothesis criterion requires exactly one finding");
        }
    }

    public enum EvidenceKind { EXPERIMENTAL, COMPUTATIONAL, LITERATURE, SYNTHETIC_TEST }
    public enum ReviewStatus { REVIEWED, UNREVIEWED, WITHDRAWN }
    public enum Conclusion { SUPPORTS, CONTRADICTS, INCONCLUSIVE, NOT_EVALUATED }

    public record Evidence(Reference reference, EvidenceKind kind, ReviewStatus reviewStatus,
                           String claim, String context, Reference source, String limitations) {
        public Evidence {
            Objects.requireNonNull(reference); Objects.requireNonNull(kind); Objects.requireNonNull(reviewStatus);
            Objects.requireNonNull(source); text(claim); text(context); text(limitations);
        }
    }

    /** Deliberately cannot be used as an Evidence or Evaluation instance. */
    public record Prediction(String statement, Reference method, String limitations) {
        public Prediction { text(statement); Objects.requireNonNull(method); text(limitations); }
    }

    /** Functional retention is an evaluation obligation, not something graph retention proves. */
    public record Criterion(String id, String description, boolean retainedFunction) {
        public Criterion { text(id); text(description); }
    }

    /** Explicit stable-ID binding only. Multiple candidates are declined, never arbitrarily selected. */
    public record SiteRequirement(String vectorId, Set<String> candidateAtomIds) {
        public SiteRequirement {
            text(vectorId); candidateAtomIds = Collections.unmodifiableSet(new TreeSet<>(candidateAtomIds));
            candidateAtomIds.forEach(DesignKnowledge::text);
        }
    }

    public record Hypothesis(Reference reference, Reference previousVersion, String claim,
                             String context, List<Reference> supportingEvidence,
                             List<Reference> conflictingEvidence, DesignState applicableParent,
                             SiteRequirement site, List<RetainedAnchor> retainedAnchors,
                             Reference eligibleRule, List<Reference> geometryConstraints,
                             String intendedConsequence, Prediction prediction, String limitations,
                             List<Criterion> evaluationCriteria) {
        public Hypothesis {
            Objects.requireNonNull(reference); text(claim); text(context);
            supportingEvidence = references(supportingEvidence); conflictingEvidence = references(conflictingEvidence);
            Objects.requireNonNull(applicableParent); Objects.requireNonNull(site); Objects.requireNonNull(eligibleRule);
            retainedAnchors = List.copyOf(retainedAnchors); geometryConstraints = references(geometryConstraints);
            text(intendedConsequence); text(limitations); evaluationCriteria = List.copyOf(evaluationCriteria);
            if (evaluationCriteria.isEmpty() || evaluationCriteria.stream().map(Criterion::id).distinct().count() != evaluationCriteria.size())
                throw new IllegalArgumentException("nonempty unique evaluation criteria required");
            if (previousVersion != null && (!reference.id().equals(previousVersion.id()) || reference.equals(previousVersion)))
                throw new IllegalArgumentException("revision must have same hypothesis ID and a new version");
        }
    }

    /** One mechanical rule: replace a neutral, nonaromatic terminal atom. No property claim. */
    public record ReplacementRule(Reference reference, String sourceElement, String replacementElement,
                                  boolean enabled, Reference source, String limitations) {
        public ReplacementRule {
            Objects.requireNonNull(reference); Objects.requireNonNull(source);
            text(sourceElement); text(replacementElement); text(limitations);
            if (sourceElement.equals(replacementElement)) throw new IllegalArgumentException("replacement must change element");
        }
    }

    public record Finding(String criterionId, Conclusion conclusion, String value, String unit, String interpretation) {
        public Finding {
            text(criterionId); Objects.requireNonNull(conclusion); Objects.requireNonNull(value); Objects.requireNonNull(unit);
            text(interpretation);
            if (conclusion == Conclusion.NOT_EVALUATED && (!value.isEmpty() || !unit.isEmpty()))
                throw new IllegalArgumentException("unevaluated finding cannot contain a measurement");
            if (conclusion != Conclusion.NOT_EVALUATED) text(value);
        }
    }

    /** An externally supplied assessment bound to an existing execution receipt; never inferred from acceptance. */
    public record Evaluation(Reference reference, Reference hypothesis, String runId, Attempt attempt,
                             Reference evaluator, EvidenceKind kind, ReviewStatus reviewStatus,
                             List<Finding> findings, String limitations) {
        public Evaluation {
            Objects.requireNonNull(reference); Objects.requireNonNull(hypothesis); text(runId);
            Objects.requireNonNull(attempt); Objects.requireNonNull(evaluator); Objects.requireNonNull(kind);
            Objects.requireNonNull(reviewStatus);
            findings = List.copyOf(findings); text(limitations);
            if (attempt.operation() == null || !attempt.operation().provenance().hypothesis().equals(hypothesis)
                    || attempt.resultingStateId() == null || attempt.resultingProduct() == null
                    || (attempt.outcome() != Outcome.ACCEPTED && attempt.outcome() != Outcome.DEDUPLICATED))
                throw new IllegalArgumentException("evaluation requires the matching successful derivation receipt");
        }
    }

    public Optional<Hypothesis> hypothesis(Reference ref) { return hypotheses.stream().filter(h -> h.reference().equals(ref)).findFirst(); }
    public Optional<ReplacementRule> rule(Reference ref) { return rules.stream().filter(r -> r.reference().equals(ref)).findFirst(); }
    public Optional<Evidence> evidence(Reference ref) { return evidence.stream().filter(e -> e.reference().equals(ref)).findFirst(); }
    public Optional<Evaluation> evaluation(Reference ref) { return evaluations.stream().filter(e -> e.reference().equals(ref)).findFirst(); }

    public DesignKnowledge withEvaluation(Reference snapshot, Evaluation evaluation) {
        newSnapshot(snapshot);
        var hypothesis = hypothesis(evaluation.hypothesis()).orElseThrow(() -> new IllegalArgumentException("unresolved evaluated hypothesis"));
        var expected = hypothesis.evaluationCriteria().stream().map(Criterion::id).collect(java.util.stream.Collectors.toSet());
        var found = evaluation.findings().stream().map(Finding::criterionId).toList();
        if (found.size() != expected.size() || !new HashSet<>(found).equals(expected))
            throw new IllegalArgumentException("each hypothesis criterion requires exactly one finding, including NOT_EVALUATED");
        var updated = new ArrayList<>(evaluations); updated.add(evaluation);
        return new DesignKnowledge(schema, snapshot, evidence, hypotheses, rules, updated);
    }

    /** The scientist supplies the revised claim. Contradictions remain explicit and block automatic replanning. */
    public DesignKnowledge revise(Reference snapshot, Reference previous, String newVersion,
                                  Reference observation, String revisedClaim, String revisedConsequence,
                                  Prediction revisedPrediction, String limitations) {
        newSnapshot(snapshot);
        var old = hypothesis(previous).orElseThrow(() -> new IllegalArgumentException("unresolved prior hypothesis"));
        var result = evaluation(observation).orElseThrow(() -> new IllegalArgumentException("unresolved observation"));
        if (!result.hypothesis().equals(previous)) throw new IllegalArgumentException("observation belongs to another hypothesis version");
        if (result.reviewStatus() != ReviewStatus.REVIEWED) throw new IllegalArgumentException("revision requires reviewed observation");
        boolean conflict = result.findings().stream().anyMatch(f -> f.conclusion() == Conclusion.CONTRADICTS);
        boolean support = result.findings().stream().allMatch(f -> f.conclusion() == Conclusion.SUPPORTS);
        if (!conflict && !support) throw new IllegalArgumentException("inconclusive/unevaluated results cannot justify a revision in this slice");
        var supports = new ArrayList<>(old.supportingEvidence());
        var conflicts = new ArrayList<>(old.conflictingEvidence());
        (conflict ? conflicts : supports).add(observation);
        var revised = new Hypothesis(new Reference(previous.id(), newVersion), previous, revisedClaim, old.context(),
                supports, conflicts, old.applicableParent(), old.site(), old.retainedAnchors(), old.eligibleRule(),
                old.geometryConstraints(), revisedConsequence, revisedPrediction, limitations, old.evaluationCriteria());
        var updated = new ArrayList<>(hypotheses); updated.add(revised);
        return new DesignKnowledge(schema, snapshot, evidence, updated, rules, evaluations);
    }

    private void newSnapshot(Reference next) {
        if (!reference.id().equals(next.id()) || reference.equals(next))
            throw new IllegalArgumentException("knowledge update needs the same ID and a new snapshot version");
    }
    private static List<Reference> references(List<Reference> values) {
        if (new HashSet<>(values).size() != values.size()) throw new IllegalArgumentException("duplicate evidence/constraint reference");
        return sorted(values, x -> x);
    }
    private static <T> List<T> sorted(List<T> values, java.util.function.Function<T, Reference> id) {
        return values.stream().sorted(Comparator.comparing((T x) -> id.apply(x).id()).thenComparing(x -> id.apply(x).version())).toList();
    }
    private static void text(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("nonblank scientific value required");
    }
}
