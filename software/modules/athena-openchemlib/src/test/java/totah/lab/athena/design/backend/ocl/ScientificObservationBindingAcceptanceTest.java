package totah.lab.athena.design.backend.ocl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.athena.design.generation.MolecularDesignTree;
import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.athena.design.reasoning.DesignKnowledge;
import totah.lab.athena.design.reasoning.HypothesisDirectedPlanner;
import totah.lab.athena.design.reasoning.ScientificObservationAdapter;
import totah.lab.hermes.pubchem.PubChemMolecularWeightImporter;
import totah.lab.mnemosyne.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.design.reasoning.DesignKnowledge.*;
import static totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/** Existing frozen source/knowledge fixtures only; no new calculation, source retrieval or candidate generation. */
class ScientificObservationBindingAcceptanceTest {
    @TempDir Path temporary;
    private static final Instant TIME = Instant.parse("2026-10-01T16:00:00Z");
    private final DesignGrammarJsonCodec codec = new DesignGrammarJsonCodec();
    private static MolecularDesignTree.Reference ref(String id) { return new MolecularDesignTree.Reference(id, "1"); }
    private static ScientificReference shared(ScientificReference.Kind kind, String id) { return new ScientificReference(kind, "fixture", id, "1"); }
    private SourceReceipt source() throws Exception {
        var raw = Path.of(System.getProperty("basedir"), "../hermes/src/test/resources/pubchem/cid-6343-molecular-weight.json");
        var path = temporary.resolve("source.json");
        var importer = new PubChemMolecularWeightImporter();
        importer.importRecord(raw, path, TIME); importer.readVerified(path);
        return codec.readSourceReceipt(path);
    }
    private DesignKnowledge fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/reasoning/qualified-local-replacement.json")) {
            return new ObjectMapper().readValue(input, DesignKnowledge.class);
        }
    }
    private SourceDecision decision(SourceReceipt source, DesignKnowledge k, String id, boolean approve) throws Exception {
        var review = new SourceReview(ref(id), ref("fixture-reviewer"), ref("manual-association"), TIME.toString(), approve,
                approve ? "Computed mass admissible for this fixture objective" : "Computed mass cannot establish biological activity",
                source.identifier(), k.hypotheses().getFirst().applicableParent(),
                new Semantics(source.method(), source.endpoint(), source.system(), source.conditions(), source.unit(), source.valueKind()),
                ScientificStatus.SCREENING_ONLY, UncertaintyKind.POINT_UNKNOWN, "No source uncertainty reported",
                source.claimBoundary(), "Computed mass only; no potency inference");
        return codec.registerSourceReview(temporary.resolve("registry"), source, review, ref("evidence-" + id));
    }
    private static Assessment assessment(Observation o, Review r, String id, Assessment.Outcome outcome, String reason) {
        return new Assessment(shared(ASSESSMENT, id), Optional.of(o.reference()), Optional.of(r.reference()),
                shared(PROPOSITION, "meets-policy-mass-objective"), shared(CRITERION, id), r.policy(), o.context(), outcome, List.of(reason), TIME);
    }

    @Test void sameFrozenObservationSupportsIndependentPoliciesAndExternalDesignBinding() throws Exception {
        var source = source(); var fixture = fixture();
        var first = decision(source, fixture, "a", true); var second = decision(source, fixture, "b", true);
        var declined = decision(source, fixture, "biology", false);
        var o = ScientificObservationAdapter.project(source, shared(OBSERVATION, "mass-source-6343"), shared(ACTIVITY, "frozen-source-observation"));
        String fieldsBefore = o.toString(); String rawBefore = source.rawBase64();
        var a = ScientificObservationAdapter.review(o, first, shared(POLICY, "mass-window-62-to-63"), TIME);
        var b = ScientificObservationAdapter.review(o, second, shared(POLICY, "mass-window-60-to-61"), TIME);
        var rejected = ScientificObservationAdapter.review(o, declined, shared(POLICY, "biological-evidence"), TIME);
        var history = new EvidenceHistory().append(o).append(a).append(b).append(rejected)
                .append(assessment(o, a, "within", Assessment.Outcome.SUPPORTS, "62.14 is inside explicitly declared [62,63] g/mol window"))
                .append(assessment(o, b, "outside", Assessment.Outcome.CONTRADICTS, "62.14 is outside explicitly declared [60,61] g/mol window"));
        assertEquals(1, history.observations().size()); assertEquals(3, history.reviews().size());
        assertEquals(fieldsBefore, o.toString()); assertSame(o, history.observations().get(o.reference()));
        assertEquals(rawBefore, source.rawBase64()); assertEquals(source.sha256(), o.provenance().artifact().id());
        assertEquals("62.14", o.value().orElseThrow().text()); assertInstanceOf(Observation.Unknown.class, o.uncertainty());
        assertEquals(Review.Decision.REJECTED, rejected.decision());
        var binding = ScientificObservationAdapter.bind(history, o.reference(), a.reference(), TIME, TIME, first);
        assertEquals(first.evidence(), binding.evidence());
        assertEquals(fixture.hypotheses().getFirst().applicableParent(), binding.evidence().qualification().subject());
        assertThrows(IllegalArgumentException.class, () -> ScientificObservationAdapter.bind(history, o.reference(), rejected.reference(), TIME, TIME, declined));
        assertThrows(IllegalArgumentException.class, () -> ScientificObservationAdapter.bind(history, o.reference(), a.reference(), TIME, TIME, second));

        // Existing rich hypothesis and planner consume the externally bound evidence unchanged.
        var old = fixture.hypotheses().getFirst();
        var h = new Hypothesis(old.reference(), null, "Test a local replacement under the fixture mass objective", source.context(),
                List.of(binding.evidence().reference()), List.of(), old.applicableParent(), old.site(), old.retainedAnchors(),
                old.eligibleRule(), old.geometryConstraints(), old.intendedConsequence(), old.prediction(), "Fixture policy; no biological inference",
                old.evaluationCriteria(), List.of(new EvidenceRequirement(binding.evidence().reference(),
                        new Requirement(binding.evidence().qualification().semantics(), Set.of(EvidenceKind.COMPUTATIONAL), Set.of(ScientificStatus.SCREENING_ONLY)))), null, List.of());
        var knowledge = new DesignKnowledge(QUALIFIED_SCHEMA, ref("bound-knowledge"), List.of(binding.evidence()), List.of(h), fixture.rules(), List.of());
        var decisions = new ArrayList<HypothesisDirectedPlanner.Decision>();
        new HypothesisDirectedPlanner(knowledge, List.of(h.reference()), Set.of(EvidenceKind.COMPUTATIONAL), decisions::add, new OclMolecularBackend())
                .editsFor(h.applicableParent());
        assertEquals(HypothesisDirectedPlanner.DecisionCode.PROPOSED, decisions.getFirst().code());
        var snapshot = temporary.resolve("knowledge.json"); codec.writeKnowledge(snapshot, knowledge, temporary.resolve("registry"));
        assertEquals(knowledge, new DesignGrammarJsonCodec().readKnowledge(snapshot, temporary.resolve("registry")));
        try (var journal = new MolecularDesignTree.Journal(temporary.resolve("journal.jsonl"))) {
            journal.sourceReview(first); journal.knowledge(knowledge);
        }
        var line = java.nio.file.Files.readAllLines(temporary.resolve("journal.jsonl")).getFirst();
        var mapper = new ObjectMapper();
        assertEquals(first, mapper.treeToValue(mapper.readTree(line).path("data"), SourceDecision.class));
    }

    @Test void retractionBlocksNewBindingWhileHistoricalBindingRemainsReconstructable() throws Exception {
        var source = source(); var decision = decision(source, fixture(), "a", true);
        var o = ScientificObservationAdapter.project(source, shared(OBSERVATION, "o"), shared(ACTIVITY, "run"));
        var review = ScientificObservationAdapter.review(o, decision, shared(POLICY, "mass-policy"), TIME);
        var original = new EvidenceHistory().append(o).append(review);
        var later = TIME.plusSeconds(2);
        var updated = original.append(new EvidenceHistory.ReviewChange(shared(REVIEW_CHANGE, "withdraw"), review.reference(),
                Optional.empty(), review.reviewer(), "Association needs reconsideration", TIME.plusSeconds(1), later));
        assertThrows(IllegalArgumentException.class, () -> ScientificObservationAdapter.bind(updated, o.reference(), review.reference(), later, later, decision));
        assertEquals(decision.evidence(), ScientificObservationAdapter.bind(updated, o.reference(), review.reference(), TIME, TIME, decision).evidence());
        assertEquals(o, updated.observations().get(o.reference()));
    }

    @Test void changedProjectionCannotBeBoundToUnchangedSourceReview() throws Exception {
        var source = source(); var decision = decision(source, fixture(), "a", true);
        var o = ScientificObservationAdapter.project(source, shared(OBSERVATION, "o"), shared(ACTIVITY, "run"));
        var altered = new Observation(o.reference(), o.subject(), o.endpoint(), o.method(), o.context(), o.activity(), o.provenance(),
                o.availability(), Optional.of(new Observation.Scalar("60", "g/mol")), o.uncertainty(), o.limitations());
        assertThrows(IllegalArgumentException.class, () -> ScientificObservationAdapter.review(altered, decision, shared(POLICY, "p"), TIME));
        assertEquals(o, ScientificObservationAdapter.project(source, o.reference(), o.activity()));
    }
}
