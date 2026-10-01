package totah.lab.athena.design.reasoning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.athena.design.generation.MolecularDesignTree;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.design.generation.MolecularDesignTree.*;
import static totah.lab.athena.design.reasoning.DesignKnowledge.*;
import static totah.lab.athena.design.reasoning.HypothesisDirectedPlanner.*;

class HypothesisDirectedPlannerTest {
    @TempDir Path temporary;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Reference ref(String id) { return new Reference(id, "1"); }

    private static DesignKnowledge fixture() {
        var graph = new MolecularGraph(List.of(atom("a", "C"), atom("b", "C"), atom("c", "S")),
                List.of(bond("ab", "a", "b"), bond("bc", "b", "c")), Map.of());
        var root = new Provenance(ref("root"), List.of(), List.of(), ref("input"), List.of(), "synthetic input", List.of(), false);
        var parent = new DesignState("state-0000", "node-0000", null, graph, root, 0);
        var hypothesis = new Hypothesis(ref("h"), null, "test a contextual local replacement", "synthetic-context",
                List.of(ref("e")), List.of(), parent, new SiteRequirement("terminal", Set.of("c")),
                List.of(new RetainedAnchor("core", Set.of("a", "b"), Set.of("ab"), false)), ref("rule"), List.of(),
                "test whether X decreases while Y remains", new Prediction("X may decrease", ref("human"), "not measured"),
                "synthetic reasoning fixture only", List.of(new Criterion("X", "measure X", false), new Criterion("Y", "measure Y retention", true)));
        return new DesignKnowledge(SCHEMA, ref("knowledge"), List.of(new Evidence(ref("e"), EvidenceKind.SYNTHETIC_TEST,
                ReviewStatus.REVIEWED, "synthetic prior observation motivates testing this region", "synthetic-context", ref("fixture-source"), "not real SAR")),
                List.of(hypothesis), List.of(new ReplacementRule(ref("rule"), "S", "O", true, ref("mechanical-rule"), "no property implication")), List.of());
    }
    private static MolecularGraph.Atom atom(String id, String element) { return new MolecularGraph.Atom(id, element, null, 0, 0, false, "UNSPECIFIED", null, Map.of()); }
    private static MolecularGraph.Bond bond(String id, String a, String b) { return new MolecularGraph.Bond(id, a, b, MolecularGraph.BondOrder.SINGLE, false, "UNSPECIFIED", Map.of()); }
    private static DesignKnowledge changed(Consumer<ObjectNode> change) {
        ObjectNode node = JSON.valueToTree(fixture()); change.accept(node);
        return JSON.convertValue(node, DesignKnowledge.class);
    }
    private static ObjectNode hypothesis(ObjectNode node) { return (ObjectNode) node.withArray("hypotheses").get(0); }
    private static Decision decision(DesignKnowledge k) {
        var decisions = new ArrayList<Decision>();
        new HypothesisDirectedPlanner(k, List.of(ref("h")), Set.of(EvidenceKind.SYNTHETIC_TEST), decisions::add)
                .editsFor(k.hypotheses().getFirst().applicableParent());
        return decisions.getFirst();
    }

    @Test void justifiedLocalReplacementIsProposedWithoutEditingParent() {
        var k = fixture(); var d = decision(k);
        assertEquals(DecisionCode.PROPOSED, d.code());
        assertEquals(GraphEdit.Type.ATOM_SUBSTITUTION, d.proposal().edit().type());
        assertEquals("S", d.parent().graph().atom("c").orElseThrow().element());
        assertEquals("O", d.proposal().edit().replacementElement());
        assertEquals(k.hypotheses().getFirst().supportingEvidence(), d.proposal().provenance().evidence());
    }
    @Test void sameEnumerableEditWithoutEvidenceIsDeclined() {
        var k = changed(n -> n.withArray("evidence").removeAll());
        assertEquals(DecisionCode.MISSING_EVIDENCE, decision(k).code());
        var d = decision(fixture());
        assertNotNull(new GraphEditTransactionEngine().apply(d.parent().graph(), d.proposal().edit(), d.proposal().authorization()));
    }
    @Test void emptySupportCannotAuthorizeAnEdit() {
        assertEquals(DecisionCode.MISSING_EVIDENCE, decision(changed(n -> hypothesis(n).withArray("supportingEvidence").removeAll())).code());
    }
    @Test void unreviewedWithdrawnAndWrongContextEvidenceAreInsufficient() {
        for (var status : List.of("UNREVIEWED", "WITHDRAWN"))
            assertEquals(DecisionCode.INSUFFICIENT_EVIDENCE, decision(changed(n -> ((ObjectNode)n.withArray("evidence").get(0)).put("reviewStatus", status))).code());
        assertEquals(DecisionCode.INSUFFICIENT_EVIDENCE, decision(changed(n -> ((ObjectNode)n.withArray("evidence").get(0)).put("context", "another-context"))).code());
    }
    @Test void syntheticEvidenceRequiresExplicitOptIn() {
        var k = fixture(); var decisions = new ArrayList<Decision>();
        assertTrue(new HypothesisDirectedPlanner(k, List.of(ref("h")), Set.of(EvidenceKind.EXPERIMENTAL), decisions::add)
                .editsFor(k.hypotheses().getFirst().applicableParent()).isEmpty());
        assertEquals(DecisionCode.INSUFFICIENT_EVIDENCE, decisions.getFirst().code());
    }
    @Test void unresolvedAndAmbiguousSitesHaveExplicitDeclines() {
        assertEquals(DecisionCode.UNRESOLVED_SITE, decision(changed(n -> ((ObjectNode)hypothesis(n).get("site")).putArray("candidateAtomIds").add("absent"))).code());
        assertEquals(DecisionCode.AMBIGUOUS_SITE, decision(changed(n -> ((ObjectNode)hypothesis(n).get("site")).putArray("candidateAtomIds").add("a").add("c"))).code());
    }
    @Test void protectedOrMissingAnchorCannotBeProposed() {
        for (String id : List.of("c", "missing"))
            assertEquals(DecisionCode.PROTECTED_ANCHOR, decision(changed(n -> ((ObjectNode)hypothesis(n).withArray("retainedAnchors").get(0)).withArray("atomIds").add(id))).code());
    }
    @Test void wrongDisabledOrUnresolvedRuleCannotBeProposed() {
        assertEquals(DecisionCode.INELIGIBLE_TRANSFORMATION, decision(changed(n -> ((ObjectNode)n.withArray("rules").get(0)).put("sourceElement", "N"))).code());
        assertEquals(DecisionCode.INELIGIBLE_TRANSFORMATION, decision(changed(n -> ((ObjectNode)n.withArray("rules").get(0)).put("enabled", false))).code());
        assertEquals(DecisionCode.INELIGIBLE_TRANSFORMATION, decision(changed(n -> n.withArray("rules").removeAll())).code());
    }
    @Test void exactParentIncludesDesignContextAndCoordinates() {
        var k = fixture(); var parent = k.hypotheses().getFirst().applicableParent();
        var wrong = new DesignState("different", parent.representativeNodeId(), null, parent.graph(), parent.provenance(), 0);
        var decisions = new ArrayList<Decision>();
        assertTrue(new HypothesisDirectedPlanner(k, List.of(ref("h")), Set.of(EvidenceKind.SYNTHETIC_TEST), decisions::add).editsFor(wrong).isEmpty());
        assertEquals(DecisionCode.NOT_APPLICABLE, decisions.getFirst().code());
    }
    @Test void decisionReplayIsIdenticalAndPredictionsAreNotObservations() {
        assertEquals(decision(fixture()), decision(fixture()));
        assertTrue(fixture().evaluations().isEmpty());
        assertNotNull(fixture().hypotheses().getFirst().prediction());
    }
    @Test void unresolvedHypothesisAndBoundedRequests() {
        var k = fixture(); var decisions = new ArrayList<Decision>();
        assertTrue(new HypothesisDirectedPlanner(k, List.of(ref("absent")), Set.of(), decisions::add).editsFor(k.hypotheses().getFirst().applicableParent()).isEmpty());
        assertEquals(DecisionCode.UNRESOLVED_HYPOTHESIS, decisions.getFirst().code());
        assertThrows(IllegalArgumentException.class, () -> new HypothesisDirectedPlanner(k, Collections.nCopies(65, ref("h")), Set.of(), decisions::add));
    }
    @Test void decisionSinkFailureCannotReleaseAnEdit() {
        var k = fixture();
        assertThrows(java.io.UncheckedIOException.class, () -> new HypothesisDirectedPlanner(k, List.of(ref("h")), Set.of(EvidenceKind.SYNTHETIC_TEST),
                d -> { throw new java.io.IOException("disk full"); }).editsFor(k.hypotheses().getFirst().applicableParent()));
    }
    @Test void knowledgeRoundTripAndDurableDeclineUseExistingInfrastructure() throws Exception {
        var k = changed(n -> n.withArray("evidence").removeAll());
        var codec = new DesignGrammarJsonCodec(); var path = temporary.resolve("knowledge.json");
        codec.writeKnowledge(path, k); assertEquals(k, codec.readKnowledge(path));
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> codec.writeKnowledge(path, k));
        var journalPath = temporary.resolve("run.jsonl");
        try (var journal = new MolecularDesignTree.Journal(journalPath)) {
            journal.knowledge(k);
            assertTrue(new HypothesisDirectedPlanner(k, List.of(ref("h")), Set.of(EvidenceKind.SYNTHETIC_TEST), journal::planningDecision)
                    .editsFor(k.hypotheses().getFirst().applicableParent()).isEmpty());
        }
        var entries = java.nio.file.Files.readAllLines(journalPath);
        assertEquals(2, entries.size());
        assertEquals("MISSING_EVIDENCE", JSON.readTree(entries.get(1)).path("data").path("code").asText());
    }
    @Test void duplicateVersionsCannotOverwriteKnowledge() {
        assertThrows(IllegalArgumentException.class, () -> changed(n -> n.withArray("hypotheses").add(n.withArray("hypotheses").get(0).deepCopy())));
    }

    @Test void unsupportedSchemaAndUnresolvedRevisionAreRejectedOnLoad() {
        assertThrows(IllegalArgumentException.class, () -> changed(n -> n.put("schema", "unknown")));
        assertThrows(IllegalArgumentException.class, () -> changed(n -> hypothesis(n).set("previousVersion", JSON.valueToTree(new Reference("h", "missing")))));
    }

    @Test void terminalRuleDoesNotSilentlyInterpretChargeIsotopeHydrogensOrStereo() {
        for (var field : List.of("formalCharge", "explicitHydrogens", "isotope")) {
            var k = changed(n -> ((ObjectNode)hypothesis(n).path("applicableParent").path("graph").path("atoms").get(2)).put(field, 1));
            assertEquals(DecisionCode.INELIGIBLE_TRANSFORMATION, decision(k).code());
        }
        var stereo = changed(n -> ((ObjectNode)hypothesis(n).path("applicableParent").path("graph").path("atoms").get(2)).put("stereochemistry", "PARITY_1"));
        assertEquals(DecisionCode.INELIGIBLE_TRANSFORMATION, decision(stereo).code());
    }
}
