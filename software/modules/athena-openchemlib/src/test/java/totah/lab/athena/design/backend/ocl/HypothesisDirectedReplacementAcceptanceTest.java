package totah.lab.athena.design.backend.ocl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.generation.*;
import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.athena.design.reasoning.*;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.design.generation.MolecularDesignTree.*;
import static totah.lab.athena.design.reasoning.DesignKnowledge.*;
import static totah.lab.athena.design.reasoning.HypothesisDirectedPlanner.*;

/** Real OCL and real journal; no alternate generator, identity, sanitizer or chemistry implementation. */
class HypothesisDirectedReplacementAcceptanceTest {
    @TempDir Path temporary;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final OclMolecularBackend backend = new OclMolecularBackend();
    private static Reference ref(String id, String version) { return new Reference(id, version); }
    private static DesignKnowledge fixture() throws Exception {
        try (var input = HypothesisDirectedReplacementAcceptanceTest.class.getResourceAsStream("/reasoning/local-replacement.json")) {
            return JSON.readValue(input, DesignKnowledge.class);
        }
    }
    private MolecularDesignTree run(DesignKnowledge k, Path path) throws Exception {
        var parent = k.hypotheses().getFirst().applicableParent();
        try (var journal = new Journal(path)) {
            journal.knowledge(k);
            var planner = new HypothesisDirectedPlanner(k, k.hypotheses().stream().map(Hypothesis::reference).toList(),
                    Set.of(EvidenceKind.SYNTHETIC_TEST), journal::planningDecision);
            return generator().generateTraced(parent.graph(), parent.provenance(), configuration(), planner, journal,
                    (p, e, g) -> new MolecularDesignGraphGenerator.GeometryResult(true, List.of("no geometry constraint requested"), Set.of()));
        }
    }
    private MolecularDesignGraphGenerator generator() {
        return new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(), backend, backend);
    }
    private static MolecularDesignGraphGenerator.Configuration configuration() {
        return new MolecularDesignGraphGenerator.Configuration(GenerationStrategy.ENUMERATIVE, 8, 1, 8, false,
                new MolecularSanitizer.SanitizationPolicy(Set.of(), true));
    }
    private static List<Decision> decisions(DesignKnowledge k) {
        var out = new ArrayList<Decision>();
        new HypothesisDirectedPlanner(k, List.of(k.hypotheses().getFirst().reference()), Set.of(EvidenceKind.SYNTHETIC_TEST), out::add)
                .editsFor(k.hypotheses().getFirst().applicableParent());
        return out;
    }

    @Test void completeReasoningReachesOclAttemptDeltaAndDurableJournal() throws Exception {
        var k = fixture(); var path = temporary.resolve("run.jsonl"); var tree = run(k, path);
        assertEquals(1, tree.attempts().size()); var attempt = tree.attempts().getFirst();
        assertEquals(Outcome.ACCEPTED, attempt.outcome());
        var h = k.hypotheses().getFirst(); var p = attempt.operation().provenance();
        assertEquals(h.reference(), p.hypothesis()); assertEquals(h.supportingEvidence(), p.evidence());
        assertEquals(h.retainedAnchors(), p.retainedAnchors()); assertEquals(h.eligibleRule(), p.rule());
        assertEquals(List.of(k.reference()), p.resources()); assertEquals(h.intendedConsequence(), p.intendedEffect());
        assertEquals(backend.identify(attempt.resultingProduct()).canonicalKey(), attempt.canonicalKey());
        assertTrue(attempt.backendEvidence().stream().allMatch(e -> e.backend().equals(OclMolecularBackend.BACKEND)));
        assertEquals(attempt.resultingProduct(), attempt.finalDelta().replay(attempt.parent()));
        assertEquals(attempt.parent().atom("a"), attempt.resultingProduct().atom("a"));
        assertTrue(k.evaluations().isEmpty()); // neither success nor prediction creates an observation
        var lines = Files.readAllLines(path);
        assertTrue(lines.stream().anyMatch(s -> s.contains("\"event\":\"planning-decision\"")));
        var receipt = lines.stream().map(s -> { try { return JSON.readTree(s); } catch (Exception e) { throw new IllegalStateException(e); } })
                .filter(n -> n.path("event").asText().equals("receipt")).findFirst().orElseThrow();
        assertEquals(attempt, JSON.treeToValue(receipt.path("data"), Attempt.class));
    }

    @Test void independentEvaluationAndContradictoryObservationCreateImmutableRevision() throws Exception {
        var k = fixture(); var tree = run(k, temporary.resolve("execution.jsonl")); var attempt = tree.attempts().getFirst();
        var descriptors = new OclPhysicochemicalDescriptorCalculator();
        double delta = descriptors.calculate(attempt.resultingProduct()).molecularWeight() - descriptors.calculate(attempt.parent()).molecularWeight();
        assertTrue(delta < 0);
        var evaluation = new Evaluation(ref("descriptor-evaluation", "1"), ref("h", "1"), "fixture-run", attempt,
                ref("OCL-descriptors", OclMolecularBackend.VERSION), EvidenceKind.COMPUTATIONAL, ReviewStatus.REVIEWED,
                List.of(new Finding("mass", Conclusion.SUPPORTS, Double.toString(delta), "g/mol", "computed mass decreases; no biological inference"),
                        new Finding("Y", Conclusion.NOT_EVALUATED, "", "", "no assay performed")), "descriptors do not establish Y retention");
        var evaluated = k.withEvaluation(ref("fixture-knowledge", "2"), evaluation);
        assertTrue(k.evaluations().isEmpty()); assertEquals(1, evaluated.evaluations().size());
        assertThrows(IllegalArgumentException.class, () -> evaluated.revise(ref("fixture-knowledge", "3"), ref("h", "1"), "2",
                evaluation.reference(), "unsupported revision", "unknown", null, "incomplete evaluation"));
        var observation = new Evaluation(ref("synthetic-observation", "1"), ref("h", "1"), "fixture-run", attempt,
                ref("synthetic-assay", "1"), EvidenceKind.SYNTHETIC_TEST, ReviewStatus.REVIEWED,
                List.of(new Finding("mass", Conclusion.SUPPORTS, "lower", "categorical", "synthetic comparison"),
                        new Finding("Y", Conclusion.CONTRADICTS, "not retained", "categorical", "synthetic counterexample, not real biology")),
                "explicitly synthetic test data");
        var observed = evaluated.withEvaluation(ref("fixture-knowledge", "3"), observation);
        var revised = observed.revise(ref("fixture-knowledge", "4"), ref("h", "1"), "2", observation.reference(),
                "Synthetic Y retention was contradicted; review the original rationale", "no further proposal before review", null, "synthetic contradiction only");
        assertEquals(k.hypotheses().getFirst(), revised.hypothesis(ref("h", "1")).orElseThrow());
        var h2 = revised.hypothesis(ref("h", "2")).orElseThrow();
        assertEquals(ref("h", "1"), h2.previousVersion()); assertEquals(List.of(observation.reference()), h2.conflictingEvidence());
        assertEquals(k.hypotheses().getFirst().supportingEvidence(), h2.supportingEvidence());
        var decisions = new ArrayList<Decision>();
        assertTrue(new HypothesisDirectedPlanner(revised, List.of(h2.reference()), Set.of(EvidenceKind.SYNTHETIC_TEST), decisions::add)
                .editsFor(h2.applicableParent()).isEmpty());
        assertEquals(DecisionCode.CONFLICTING_EVIDENCE, decisions.getFirst().code());
        var codec = new DesignGrammarJsonCodec(); var snapshot = temporary.resolve("revision.json");
        codec.writeKnowledge(snapshot, revised); assertEquals(revised, codec.readKnowledge(snapshot));
        try (var journal = new Journal(temporary.resolve("evaluation-and-revision.jsonl"))) {
            journal.knowledge(evaluated); journal.knowledge(observed); journal.knowledge(revised);
        }
        assertEquals(ref("h", "1"), attempt.operation().provenance().hypothesis());
    }

    @Test void supportingObservationAlsoCreatesANewVersion() throws Exception {
        var k = fixture(); var a = run(k, temporary.resolve("support-run.jsonl")).attempts().getFirst();
        var e = new Evaluation(ref("support", "1"), ref("h", "1"), "support-run", a, ref("synthetic-assay", "1"),
                EvidenceKind.SYNTHETIC_TEST, ReviewStatus.REVIEWED,
                List.of(new Finding("mass", Conclusion.SUPPORTS, "lower", "categorical", "synthetic"),
                        new Finding("Y", Conclusion.SUPPORTS, "retained", "categorical", "synthetic")), "not real evidence");
        var revised = k.withEvaluation(ref("fixture-knowledge", "2"), e).revise(ref("fixture-knowledge", "3"), ref("h", "1"), "2", e.reference(),
                "supported in synthetic fixture only", "retain scoped test objective", null, "no extrapolation");
        assertEquals(2, revised.hypotheses().size());
        assertTrue(revised.hypothesis(ref("h", "2")).orElseThrow().supportingEvidence().contains(e.reference()));
        assertTrue(k.evaluations().isEmpty());
    }

    @Test void distinctHypothesesDeduplicateChemistryButKeepDerivationsAndCorrespondence() throws Exception {
        ObjectNode n = JSON.valueToTree(fixture());
        ObjectNode second = ((ObjectNode)n.withArray("hypotheses").get(0)).deepCopy();
        ((ObjectNode)second.get("reference")).put("id", "h-other"); second.put("claim", "independent synthetic rationale for the same local edit");
        n.withArray("hypotheses").add(second);
        var k = JSON.treeToValue(n, DesignKnowledge.class);
        var tree = run(k, temporary.resolve("duplicates.jsonl"));
        assertEquals(List.of(Outcome.ACCEPTED, Outcome.DEDUPLICATED), tree.attempts().stream().map(Attempt::outcome).toList());
        assertEquals(3, tree.states().size()); assertEquals(2, tree.nodes().size());
        var duplicate = tree.attempts().get(1);
        assertTrue(duplicate.representativeMapping().exhaustive());
        assertEquals(3, duplicate.parentToRepresentativeAtoms().size());
        assertNotEquals(tree.states().get(1).provenance().hypothesis(), tree.states().get(2).provenance().hypothesis());
        assertEquals(tree, run(k, temporary.resolve("replay.jsonl")));
        assertEquals(Files.readString(temporary.resolve("duplicates.jsonl")), Files.readString(temporary.resolve("replay.jsonl")));
    }

    @Test void oclRemainsAuthoritativeForInvalidProducts() throws Exception {
        ObjectNode n = JSON.valueToTree(fixture());
        ((ObjectNode)n.withArray("rules").get(0)).put("replacementElement", "NoSuchElement");
        var k = JSON.treeToValue(n, DesignKnowledge.class);
        assertEquals(DecisionCode.PROPOSED, decisions(k).getFirst().code());
        var tree = run(k, temporary.resolve("invalid.jsonl"));
        assertEquals(Outcome.BACKEND_VALIDATION_FAILURE, tree.attempts().getFirst().outcome());
        assertEquals(1, tree.states().size());
    }

    @Test void protectedAnchorAlsoFailsIfCallerBypassesPlanner() throws Exception {
        var k = fixture(); var proposal = decisions(k).getFirst().proposal(); var p = proposal.provenance();
        var protectedContext = new Provenance(p.hypothesis(), p.evidence(), List.of(new RetainedAnchor("protected", Set.of("c"), Set.of(), false)),
                p.rule(), p.resources(), p.intendedEffect(), p.geometryConstraints(), false);
        var forged = new MolecularDesignGraphGenerator.AuthorizedEdit(proposal.edit(), proposal.authorization(), 0, protectedContext);
        var root = k.hypotheses().getFirst().applicableParent();
        var result = generator().generateTraced(root.graph(), root.provenance(), configuration(), s -> s.depth() == 0 ? List.of(forged) : List.of(), a -> {},
                (parent, edit, graph) -> new MolecularDesignGraphGenerator.GeometryResult(true, List.of(), Set.of()));
        assertEquals(Outcome.AUTHORIZATION_REJECTED, result.attempts().getFirst().outcome());
    }

    @Test void evaluationCannotAttachToAnotherHypothesisOrUseMissingCriteria() throws Exception {
        var k = fixture(); var a = run(k, temporary.resolve("eval-validation.jsonl")).attempts().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new Evaluation(ref("bad", "1"), ref("other", "1"), "run", a, ref("method", "1"),
                EvidenceKind.SYNTHETIC_TEST, ReviewStatus.REVIEWED, List.of(), "synthetic"));
        var incomplete = new Evaluation(ref("bad", "1"), ref("h", "1"), "run", a, ref("method", "1"), EvidenceKind.SYNTHETIC_TEST,
                ReviewStatus.REVIEWED, List.of(), "synthetic");
        assertThrows(IllegalArgumentException.class, () -> k.withEvaluation(ref("fixture-knowledge", "2"), incomplete));
        assertThrows(IllegalArgumentException.class, () -> new Finding("Y", Conclusion.NOT_EVALUATED, "true", "", "cannot invent observation"));
    }

    @Test void missingRequestedGeometryStillProducesPhaseOneRejection() throws Exception {
        ObjectNode n = JSON.valueToTree(fixture());
        ((ObjectNode)n.withArray("hypotheses").get(0)).withArray("geometryConstraints").add(JSON.valueToTree(ref("required-geometry", "1")));
        var tree = run(JSON.treeToValue(n, DesignKnowledge.class), temporary.resolve("geometry.jsonl"));
        assertEquals(Outcome.GEOMETRY_FAILURE, tree.attempts().getFirst().outcome());
    }

    @Test void plannerPersistenceFailureIsAnExplicitFailureNotAnUnrecordedProposal() throws Exception {
        var k = fixture(); var parent = k.hypotheses().getFirst().applicableParent();
        var planner = new HypothesisDirectedPlanner(k, List.of(ref("h", "1")), Set.of(EvidenceKind.SYNTHETIC_TEST),
                d -> { throw new java.io.IOException("decision storage unavailable"); });
        var path = temporary.resolve("planner-failure.jsonl");
        try (var journal = new Journal(path)) {
            var tree = generator().generateTraced(parent.graph(), parent.provenance(), configuration(), planner, journal,
                    (p, e, g) -> new MolecularDesignGraphGenerator.GeometryResult(true, List.of(), Set.of()));
            assertEquals(TerminationReason.PLANNER_FAILURE, tree.termination().reason());
            assertEquals(1, tree.states().size());
            assertEquals(Outcome.UNEXPECTED_EXECUTION_FAILURE, tree.attempts().getFirst().outcome());
            assertNull(tree.attempts().getFirst().operation());
        }
        assertTrue(Files.readString(path).contains("decision storage unavailable"));
    }
}
