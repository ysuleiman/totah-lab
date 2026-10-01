package totah.lab.athena.design.reasoning;

import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.athena.pocket.evidence.EvidenceMethod;
import totah.lab.athena.design.grammar.DesignGrammar;
import totah.lab.athena.design.generation.MolecularDesignTree.*;

import java.util.*;

/** Immutable, version-addressed scientific inputs, not a molecule store or an inference engine. */
public record DesignKnowledge(String schema, Reference reference, List<Evidence> evidence,
                              List<Hypothesis> hypotheses, List<ReplacementRule> rules,
                              List<Evaluation> evaluations) {
    public static final String SCHEMA = "athena-design-knowledge/1";
    public static final String QUALIFIED_SCHEMA = "athena-design-knowledge/2";

    public DesignKnowledge {
        if (!SCHEMA.equals(schema) && !QUALIFIED_SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported knowledge schema");
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
            if (h == null || (!h.applicableParent().equals(evaluation.attempt().parentDesignState())
                    && (h.binding() == null || !h.binding().target().equals(evaluation.attempt().parentDesignState()))))
                throw new IllegalArgumentException("evaluation does not resolve to its hypothesis and parent state");
            var expected = h.evaluationCriteria().stream().map(Criterion::id).collect(java.util.stream.Collectors.toSet());
            var found = evaluation.findings().stream().map(Finding::criterionId).toList();
            if (found.size() != expected.size() || !new HashSet<>(found).equals(expected))
                throw new IllegalArgumentException("each hypothesis criterion requires exactly one finding");
            if (QUALIFIED_SCHEMA.equals(schema)) {
                for (var finding : evaluation.findings()) {
                    var criterion = h.evaluationCriteria().stream().filter(c -> c.id().equals(finding.criterionId())).findFirst().orElseThrow();
                    var input = evidence.stream().filter(e -> e.reference().equals(finding.evidence())).findFirst().orElse(null);
                    if (finding.evidence() != null && input == null) throw new IllegalArgumentException("unresolved finding evidence");
                    var checked = assess(criterion, input, evaluation.attempt(), h.context());
                    if (!checked.equals(finding)) throw new IllegalArgumentException("finding must preserve qualified assessment semantics");
                }
            }
        }
    }

    public enum EvidenceKind { EXPERIMENTAL, COMPUTATIONAL, LITERATURE, SYNTHETIC_TEST }
    public enum ReviewStatus { REVIEWED, UNREVIEWED, WITHDRAWN }
    public enum Conclusion { SUPPORTS, CONTRADICTS, INCONCLUSIVE, NOT_EVALUATED, UNRESOLVED, NOT_MEASURED, FAILED_INVALID }

    public record Evidence(Reference reference, EvidenceKind kind, ReviewStatus reviewStatus,
                           String claim, String context, Reference source, String limitations, Qualification qualification, Value value) {
        public Evidence {
            Objects.requireNonNull(reference); Objects.requireNonNull(kind); Objects.requireNonNull(reviewStatus);
            Objects.requireNonNull(source); text(claim); text(context); text(limitations);
            if (qualification != null && value != null && qualification.semantics().valueKind() != value.kind())
                throw new IllegalArgumentException("measurement kind differs from semantics");
        }
        public Evidence(Reference reference, EvidenceKind kind, ReviewStatus reviewStatus,
                        String claim, String context, Reference source, String limitations) {
            this(reference, kind, reviewStatus, claim, context, source, limitations, null, null);
        }
    }

    /** Independent scientific dimensions; exact comparison performs no implicit unit/method conversion. */
    public record Semantics(EvidenceMethod method, Reference endpoint, Reference system,
                            Map<String, String> conditions, String unit, ValueKind valueKind) {
        public Semantics {
            Objects.requireNonNull(method); Objects.requireNonNull(endpoint); Objects.requireNonNull(system);
            conditions = Map.copyOf(conditions); text(unit); Objects.requireNonNull(valueKind);
        }
    }
    public enum ValueKind { QUANTITATIVE, QUALITATIVE }
    /** Inclusive uncertainty bounds, or a categorical observation; never a universal confidence score. */
    public record Value(Double lower, Double upper, String category) {
        public Value {
            Objects.requireNonNull(category);
            if (lower == null || upper == null) {
                if (lower != null || upper != null) throw new IllegalArgumentException("both bounds required");
                text(category);
            } else if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > upper || !category.isEmpty())
                throw new IllegalArgumentException("finite ordered bounds and no category required");
        }
        public ValueKind kind() { return lower == null ? ValueKind.QUALITATIVE : ValueKind.QUANTITATIVE; }
    }
    public record Qualification(Semantics semantics, ScientificStatus status, DesignState subject,
                                Reference review, String uncertainty) {
        public Qualification {
            Objects.requireNonNull(semantics); Objects.requireNonNull(status); Objects.requireNonNull(subject);
            Objects.requireNonNull(review); text(uncertainty);
        }
    }
    public record Requirement(Semantics semantics, Set<EvidenceKind> kinds, Set<ScientificStatus> statuses) {
        public Requirement {
            Objects.requireNonNull(semantics); kinds = Set.copyOf(kinds); statuses = Set.copyOf(statuses);
            if (kinds.isEmpty() || statuses.isEmpty() || statuses.stream().anyMatch(DesignKnowledge::failed))
                throw new IllegalArgumentException("explicit usable kinds/statuses required");
        }
        public boolean accepts(Evidence evidence) {
            return evidence != null && evidence.reviewStatus() == ReviewStatus.REVIEWED && evidence.qualification() != null
                    && semantics.equals(evidence.qualification().semantics()) && kinds.contains(evidence.kind())
                    && statuses.contains(evidence.qualification().status()) && evidence.value() != null
                    && evidence.value().kind() == semantics.valueKind();
        }
    }
    public record EvidenceRequirement(Reference evidence, Requirement requirement) {
        public EvidenceRequirement { Objects.requireNonNull(evidence); Objects.requireNonNull(requirement); }
    }
    /** Reviewed transfer to one exact state, using an existing grammar region and backend proof at planning time. */
    public record ReviewedBinding(Reference reference, Reference reviewer, Reference grammar,
                                  DesignGrammar.EditableVector region, DesignState target) {
        public ReviewedBinding {
            Objects.requireNonNull(reference); Objects.requireNonNull(reviewer); Objects.requireNonNull(grammar);
            Objects.requireNonNull(region); Objects.requireNonNull(target);
        }
    }

    /** Deliberately cannot be used as an Evidence or Evaluation instance. */
    public record Prediction(String statement, Reference method, String limitations) {
        public Prediction { text(statement); Objects.requireNonNull(method); text(limitations); }
    }

    /** Functional retention is an evaluation obligation, not something graph retention proves. */
    public record Criterion(String id, String description, boolean retainedFunction,
                            Requirement requirement, Value acceptedRange, Reference policy, Reference policySource) {
        public Criterion {
            text(id); text(description);
            if (requirement != null && (acceptedRange == null || policy == null || policySource == null
                    || requirement.semantics().valueKind() != acceptedRange.kind()))
                throw new IllegalArgumentException("qualified criteria require attributable versioned thresholds/categories");
        }
        public Criterion(String id, String description, boolean retainedFunction) {
            this(id, description, retainedFunction, null, null, null, null);
        }
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
                             List<Criterion> evaluationCriteria, List<EvidenceRequirement> requirements,
                             ReviewedBinding binding, List<Reference> unresolvedObservations) {
        public Hypothesis {
            Objects.requireNonNull(reference); text(claim); text(context);
            supportingEvidence = references(supportingEvidence); conflictingEvidence = references(conflictingEvidence);
            Objects.requireNonNull(applicableParent); Objects.requireNonNull(site); Objects.requireNonNull(eligibleRule);
            retainedAnchors = List.copyOf(retainedAnchors); geometryConstraints = references(geometryConstraints);
            text(intendedConsequence); text(limitations); evaluationCriteria = List.copyOf(evaluationCriteria);
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            unresolvedObservations = unresolvedObservations == null ? List.of() : references(unresolvedObservations);
            if (requirements.stream().map(EvidenceRequirement::evidence).distinct().count() != requirements.size())
                throw new IllegalArgumentException("duplicate evidence requirement");
            if (evaluationCriteria.isEmpty() || evaluationCriteria.stream().map(Criterion::id).distinct().count() != evaluationCriteria.size())
                throw new IllegalArgumentException("nonempty unique evaluation criteria required");
            if (previousVersion != null && (!reference.id().equals(previousVersion.id()) || reference.equals(previousVersion)))
                throw new IllegalArgumentException("revision must have same hypothesis ID and a new version");
        }
        public Hypothesis(Reference reference, Reference previousVersion, String claim, String context,
                          List<Reference> supportingEvidence, List<Reference> conflictingEvidence, DesignState applicableParent,
                          SiteRequirement site, List<RetainedAnchor> retainedAnchors, Reference eligibleRule,
                          List<Reference> geometryConstraints, String intendedConsequence, Prediction prediction,
                          String limitations, List<Criterion> evaluationCriteria) {
            this(reference, previousVersion, claim, context, supportingEvidence, conflictingEvidence, applicableParent,
                    site, retainedAnchors, eligibleRule, geometryConstraints, intendedConsequence, prediction, limitations,
                    evaluationCriteria, List.of(), null, List.of());
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

    public record Finding(String criterionId, Conclusion conclusion, String value, String unit, String interpretation, Reference evidence) {
        public Finding {
            text(criterionId); Objects.requireNonNull(conclusion); Objects.requireNonNull(value); Objects.requireNonNull(unit);
            text(interpretation);
            if ((conclusion == Conclusion.NOT_EVALUATED || conclusion == Conclusion.NOT_MEASURED || conclusion == Conclusion.FAILED_INVALID) && (!value.isEmpty() || !unit.isEmpty()))
                throw new IllegalArgumentException("unevaluated finding cannot contain a measurement");
            if (conclusion == Conclusion.SUPPORTS || conclusion == Conclusion.CONTRADICTS || conclusion == Conclusion.INCONCLUSIVE) text(value);
        }
        public Finding(String criterionId, Conclusion conclusion, String value, String unit, String interpretation) {
            this(criterionId, conclusion, value, unit, interpretation, null);
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
        if (!conflict && !support && !QUALIFIED_SCHEMA.equals(schema))
            throw new IllegalArgumentException("inconclusive/unevaluated results cannot justify a legacy revision");
        var supports = new ArrayList<>(old.supportingEvidence());
        var conflicts = new ArrayList<>(old.conflictingEvidence());
        var unresolved = new ArrayList<>(old.unresolvedObservations());
        (conflict ? conflicts : support ? supports : unresolved).add(observation);
        var revised = new Hypothesis(new Reference(previous.id(), newVersion), previous, revisedClaim, old.context(),
                supports, conflicts, old.applicableParent(), old.site(), old.retainedAnchors(), old.eligibleRule(),
                old.geometryConstraints(), revisedConsequence, revisedPrediction, limitations, old.evaluationCriteria(),
                old.requirements(), old.binding(), unresolved);
        var updated = new ArrayList<>(hypotheses); updated.add(revised);
        return new DesignKnowledge(schema, snapshot, evidence, updated, rules, evaluations);
    }

    public DesignKnowledge withEvidence(Reference snapshot, Evidence observation) {
        newSnapshot(snapshot);
        var updated = new ArrayList<>(evidence); updated.add(observation);
        return new DesignKnowledge(schema, snapshot, updated, hypotheses, rules, evaluations);
    }

    /** Dimension-wise assessment using only hypothesis-owned, versioned acceptance criteria. */
    public Evaluation evaluate(Reference reference, Reference hypothesis, String runId, Attempt attempt,
                               Reference evaluator, Map<String, Reference> observations) {
        if (!QUALIFIED_SCHEMA.equals(schema)) throw new IllegalArgumentException("qualified schema required");
        var h = hypothesis(hypothesis).orElseThrow();
        var known = h.evaluationCriteria().stream().map(Criterion::id).collect(java.util.stream.Collectors.toSet());
        if (!known.containsAll(observations.keySet())) throw new IllegalArgumentException("unknown evaluation criterion");
        if (observations.values().stream().anyMatch(r -> evidence(r).isEmpty())) throw new IllegalArgumentException("unresolved observation evidence");
        var findings = h.evaluationCriteria().stream().map(c -> assess(c,
                evidence(observations.get(c.id())).orElse(null), attempt, h.context())).toList();
        return new Evaluation(reference, hypothesis, runId, attempt, evaluator, EvidenceKind.COMPUTATIONAL,
                ReviewStatus.REVIEWED, findings, "Rule assessment only; source evidence kinds, methods and limits remain authoritative");
    }

    private static Finding assess(Criterion criterion, Evidence input, Attempt attempt, String context) {
        if (input == null) return finding(criterion, null, Conclusion.NOT_MEASURED, "intended consequence not measured");
        if (!context.equals(input.context())) return finding(criterion, input, Conclusion.UNRESOLVED, "evidence context differs");
        if (criterion.requirement() == null || input.qualification() == null
                || input.reviewStatus() != ReviewStatus.REVIEWED
                || !criterion.requirement().semantics().equals(input.qualification().semantics())
                || !criterion.requirement().kinds().contains(input.kind()))
            return finding(criterion, input, Conclusion.UNRESOLVED, "method/endpoint/system/conditions/unit/kind/status not qualified");
        var subject = input.qualification().subject();
        if (!subject.stateId().equals(attempt.resultingStateId()) || !subject.graph().equals(attempt.resultingProduct())
                || !Objects.equals(subject.parentStateId(), attempt.parentStateId())
                || !subject.provenance().equals(attempt.operation().provenance()))
            return finding(criterion, input, Conclusion.UNRESOLVED, "evidence does not apply to this resulting design state");
        if (failed(input.qualification().status()))
            return finding(criterion, input, Conclusion.FAILED_INVALID, "source evaluation failed or is unavailable; not a contradiction");
        if (!criterion.requirement().accepts(input))
            return finding(criterion, input, Conclusion.UNRESOLVED, "source status or value does not meet criterion qualification");
        var value = input.value(); var accepted = criterion.acceptedRange();
        Conclusion conclusion;
        if (value.kind() == ValueKind.QUALITATIVE)
            conclusion = value.category().equals(accepted.category()) ? Conclusion.SUPPORTS : Conclusion.CONTRADICTS;
        else if (value.lower() >= accepted.lower() && value.upper() <= accepted.upper()) conclusion = Conclusion.SUPPORTS;
        else if (value.upper() < accepted.lower() || value.lower() > accepted.upper()) conclusion = Conclusion.CONTRADICTS;
        else conclusion = Conclusion.UNRESOLVED;
        return finding(criterion, input, conclusion, "versioned criterion " + criterion.policy() + "; uncertainty bounds retained");
    }
    private static Finding finding(Criterion criterion, Evidence input, Conclusion conclusion, String reason) {
        boolean measured = input != null && input.value() != null && conclusion != Conclusion.FAILED_INVALID;
        return new Finding(criterion.id(), conclusion, measured ? input.value().toString() : "",
                measured && input.qualification() != null ? input.qualification().semantics().unit() : "", reason,
                input == null ? null : input.reference());
    }
    private static boolean failed(ScientificStatus status) {
        return status == ScientificStatus.UNAVAILABLE || status == ScientificStatus.NONCONVERGED
                || status == ScientificStatus.NUMERICAL_FAILURE || status == ScientificStatus.UNSUPPORTED_CHEMISTRY;
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
