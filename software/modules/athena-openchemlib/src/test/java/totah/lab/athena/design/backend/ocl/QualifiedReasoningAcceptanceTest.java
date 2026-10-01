package totah.lab.athena.design.backend.ocl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.generation.*;
import totah.lab.athena.design.grammar.DesignGrammar;
import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.athena.design.reasoning.*;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.design.generation.MolecularDesignTree.*;
import static totah.lab.athena.design.reasoning.DesignKnowledge.*;
import static totah.lab.athena.design.reasoning.HypothesisDirectedPlanner.*;

/** Extends the frozen Phase 2.1 synthetic example using the existing OCL transaction/generation path. */
class QualifiedReasoningAcceptanceTest {
    @TempDir Path temporary;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final OclMolecularBackend backend = new OclMolecularBackend();
    private static Reference ref(String id) { return new Reference(id, "1"); }
    private static Reference snapshot(String version) { return new Reference("fixture-knowledge", version); }
    private static DesignKnowledge fixture() throws Exception {
        try (var input = QualifiedReasoningAcceptanceTest.class.getResourceAsStream("/reasoning/qualified-local-replacement.json")) {
            return JSON.readValue(input, DesignKnowledge.class);
        }
    }
    private static DesignKnowledge change(DesignKnowledge k, Consumer<ObjectNode> edit) {
        ObjectNode n = JSON.valueToTree(k); edit.accept(n); return JSON.convertValue(n, DesignKnowledge.class);
    }
    private static ObjectNode hypothesis(ObjectNode n) { return (ObjectNode)n.path("hypotheses").get(0); }
    private static ObjectNode evidence(ObjectNode n) { return (ObjectNode)n.path("evidence").get(0); }
    private Decision decision(DesignKnowledge k, DesignState parent, CanonicalIdentityService identity) {
        var out = new ArrayList<Decision>();
        new HypothesisDirectedPlanner(k, List.of(k.hypotheses().getLast().reference()), Set.of(EvidenceKind.SYNTHETIC_TEST, EvidenceKind.COMPUTATIONAL), out::add, identity).editsFor(parent);
        return out.getFirst();
    }
    private Decision decision(DesignKnowledge k) { return decision(k, k.hypotheses().getFirst().applicableParent(), backend); }
    private MolecularDesignTree execute(DesignKnowledge k) throws Exception {
        var parent = k.hypotheses().getFirst().applicableParent();
        try (var journal = new Journal(temporary.resolve(UUID.randomUUID() + ".jsonl"))) {
            journal.knowledge(k);
            return new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(), backend, backend).generateTraced(
                    parent.graph(), parent.provenance(), new MolecularDesignGraphGenerator.Configuration(GenerationStrategy.ENUMERATIVE,
                            8, 1, 8, false, new MolecularSanitizer.SanitizationPolicy(Set.of(), true)),
                    new HypothesisDirectedPlanner(k, List.of(ref("h")), Set.of(EvidenceKind.SYNTHETIC_TEST), journal::planningDecision, backend),
                    journal, (p,e,g) -> new MolecularDesignGraphGenerator.GeometryResult(true, List.of(), Set.of()));
        }
    }
    private static Evidence observation(DesignKnowledge k, Attempt a, String criterion, Value value, ScientificStatus status) {
        var c = k.hypotheses().getFirst().evaluationCriteria().stream().filter(x -> x.id().equals(criterion)).findFirst().orElseThrow();
        var state = new DesignState(a.resultingStateId(), "node-0001", a.parentStateId(), a.resultingProduct(), a.operation().provenance(), 1);
        return new Evidence(ref("observed-" + criterion), EvidenceKind.SYNTHETIC_TEST, ReviewStatus.REVIEWED, "synthetic test observation",
                k.hypotheses().getFirst().context(), ref("fixture-source"), "not real science",
                new Qualification(c.requirement().semantics(), status, state, ref("review"), "synthetic interval"), value);
    }
    private static Evaluation evaluate(DesignKnowledge k, Attempt a, Map<String, Reference> observations) {
        return k.evaluate(ref("evaluation"), ref("h"), "synthetic-run", a, ref("reviewer"), observations);
    }

    @Test void comparableEvidenceAuthorizesExactSiteWithoutCorrespondence() throws Exception {
        var k = fixture(); var d = decision(k, k.hypotheses().getFirst().applicableParent(), null);
        assertEquals(DecisionCode.PROPOSED, d.code()); assertNull(d.binding());
        assertEquals(Set.of("c"), d.proposal().edit().affectedAtomIds());
        assertEquals(Outcome.ACCEPTED, execute(k).attempts().getFirst().outcome());
    }
    @Test void unlikeEndpointsMethodsSystemsUnitsConditionsAndKindsCannotAuthorize() throws Exception {
        var k = fixture();
        for (var field : List.of("endpoint", "system")) {
            var other = change(k, n -> ((ObjectNode)evidence(n).path("qualification").path("semantics").path(field)).put("id", "other"));
            assertEquals(DecisionCode.NON_COMPARABLE_EVIDENCE, decision(other).code(), field);
        }
        for (var field : List.of("method", "conditions")) {
            var other = change(k, n -> ((ObjectNode)evidence(n).path("qualification").path("semantics").path(field)).put(field.equals("method") ? "name" : "medium", "other"));
            assertEquals(DecisionCode.NON_COMPARABLE_EVIDENCE, decision(other).code(), field);
        }
        assertEquals(DecisionCode.NON_COMPARABLE_EVIDENCE, decision(change(k,n -> ((ObjectNode)evidence(n).path("qualification").path("semantics")).put("unit","other"))).code());
        assertEquals(DecisionCode.NON_COMPARABLE_EVIDENCE, decision(change(k,n -> evidence(n).put("kind","COMPUTATIONAL"))).code());
        assertEquals(DecisionCode.INSUFFICIENT_EVIDENCE, decision(change(k,n -> evidence(n).put("context","other"))).code());
        assertEquals(DecisionCode.NON_COMPARABLE_EVIDENCE, decision(change(k,n -> ((ObjectNode)evidence(n).path("qualification").path("subject")).put("stateId","other"))).code());
        assertEquals(DecisionCode.NON_COMPARABLE_EVIDENCE, decision(change(k,n -> evidence(n).putNull("qualification"))).code());
    }
    @Test void qualifiedRequirementsCannotBeOmittedOrLeftUnbound() throws Exception {
        var k=fixture();
        assertEquals(DecisionCode.INSUFFICIENT_EVIDENCE,decision(change(k,n->hypothesis(n).putArray("requirements"))).code());
        assertEquals(DecisionCode.INSUFFICIENT_EVIDENCE,decision(change(k,n->((ObjectNode)hypothesis(n).path("evaluationCriteria").get(0)).putNull("requirement"))).code());
        assertEquals(DecisionCode.NON_COMPARABLE_EVIDENCE,decision(change(k,n->((ObjectNode)hypothesis(n).path("requirements").get(0).path("evidence")).put("id","unbound"))).code());
        assertThrows(IllegalArgumentException.class,()->change(k,n->((ObjectNode)hypothesis(n).path("evaluationCriteria").get(0)).putNull("policySource")));
    }
    @Test void screeningEvidenceCannotSatisfyDecisionGradeRequirement() throws Exception {
        var k = change(fixture(), n -> ((ObjectNode)hypothesis(n).path("requirements").get(0).path("requirement")).putArray("statuses").add("VALIDATED_REFERENCE"));
        assertEquals(DecisionCode.NON_COMPARABLE_EVIDENCE, decision(k).code());
    }
    @Test void allFiveOutcomesAreDistinctAndThresholdsAreAttributable() throws Exception {
        var k = fixture(); var a = execute(k).attempts().getFirst();
        var values = List.of(new Value(-17.,-15.,""), new Value(1.,2.,""), new Value(-11.,-9.,""));
        var outcomes = List.of(Conclusion.SUPPORTS, Conclusion.CONTRADICTS, Conclusion.UNRESOLVED);
        for (int i=0; i<values.size(); i++) {
            var e = observation(k,a,"mass",values.get(i),ScientificStatus.SCREENING_ONLY);
            var updated = k.withEvidence(snapshot("observed"),e);
            var evaluated = evaluate(updated,a,Map.of("mass",e.reference()));
            assertEquals(outcomes.get(i), evaluated.findings().get(0).conclusion());
            assertEquals(Conclusion.NOT_MEASURED, evaluated.findings().get(1).conclusion());
            assertTrue(evaluated.findings().get(0).interpretation().contains("synthetic-mass-policy"));
            assertEquals(1,updated.withEvaluation(snapshot("evaluated"),evaluated).evaluations().size());
        }
        var failed = observation(k,a,"mass",null,ScientificStatus.NUMERICAL_FAILURE);
        var result = evaluate(k.withEvidence(snapshot("failed"),failed),a,Map.of("mass",failed.reference()));
        assertEquals(Conclusion.FAILED_INVALID,result.findings().getFirst().conclusion());
        assertEquals("",result.findings().getFirst().value());
    }
    @Test void wrongObservationSemanticsOrStateRemainUnresolvedAndUnknownReferencesFail() throws Exception {
        var k = fixture(); var a = execute(k).attempts().getFirst();
        var e = observation(k,a,"mass",new Value(-16.,-16.,""),ScientificStatus.SCREENING_ONLY);
        var observed = k.withEvidence(snapshot("observed"),e);
        for (var field : List.of("endpoint", "method", "conditions", "subject", "context")) {
            var changed = change(observed,n -> {
                var node = (ObjectNode)n.path("evidence").get(0); // observed-mass sorts before prior
                if (field.equals("context")) node.put("context","other");
                else if (field.equals("subject")) ((ObjectNode)node.path("qualification").path("subject")).put("stateId","other");
                else ((ObjectNode)node.path("qualification").path("semantics").path(field)).put(field.equals("endpoint")?"id":field.equals("method")?"name":"medium","other");
            });
            assertEquals(Conclusion.UNRESOLVED,evaluate(changed,a,Map.of("mass",e.reference())).findings().getFirst().conclusion());
        }
        assertThrows(IllegalArgumentException.class,()->evaluate(observed,a,Map.of("mass",ref("missing"))));
        assertThrows(IllegalArgumentException.class,()->evaluate(observed,a,Map.of("unknown",e.reference())));
    }
    @Test void observationsReviseImmutablyWithoutPromotingUnresolvedOrConflictingClaims() throws Exception {
        for (var expected : List.of(Conclusion.SUPPORTS,Conclusion.CONTRADICTS,Conclusion.UNRESOLVED)) {
            var k=fixture(); var a=execute(k).attempts().getFirst();
            var mass=observation(k,a,"mass",new Value(-16.,-16.,""),ScientificStatus.SCREENING_ONLY);
            var y=observation(k,a,"Y",new Value(null,null,expected==Conclusion.CONTRADICTS?"lost":"retained"),ScientificStatus.SCREENING_ONLY);
            var observed=k.withEvidence(snapshot("2"),mass).withEvidence(snapshot("3"),y);
            var eval=evaluate(observed,a,expected==Conclusion.UNRESOLVED?Map.of("mass",mass.reference()):Map.of("mass",mass.reference(),"Y",y.reference()));
            var revised=observed.withEvaluation(snapshot("4"),eval).revise(snapshot("5"),ref("h"),"2",eval.reference(),"reviewed revised claim","continue testing, not truth",null,"synthetic only");
            assertEquals(k.hypotheses().getFirst(),revised.hypothesis(ref("h")).orElseThrow());
            var d=decision(revised);
            assertEquals(expected==Conclusion.SUPPORTS?DecisionCode.PROPOSED:expected==Conclusion.CONTRADICTS?DecisionCode.CONFLICTING_EVIDENCE:DecisionCode.UNRESOLVED_EVIDENCE,d.code());
            var path=temporary.resolve(expected+".json"); var codec=new DesignGrammarJsonCodec(); codec.writeKnowledge(path,revised);
            assertEquals(revised,codec.readKnowledge(path)); assertEquals(d,decision(codec.readKnowledge(path)));
        }
    }
    @Test void manuallyStrengthenedFindingsCannotEnterQualifiedSnapshot() throws Exception {
        var k=fixture(); var a=execute(k).attempts().getFirst();
        var e=evaluate(k,a,Map.of()); var observed=k.withEvaluation(snapshot("2"),e);
        assertThrows(IllegalArgumentException.class,()->change(observed,n->{
            var f=(ObjectNode)n.path("evaluations").get(0).path("findings").get(0); f.put("conclusion","SUPPORTS"); f.put("value","invented");
        }));
    }
    private static DesignState reordered(DesignState source) {
        var g=source.graph();
        var atoms=new ArrayList<MolecularGraph.Atom>();
        for(var a:g.atoms().reversed()) atoms.add(new MolecularGraph.Atom("new-"+a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),a.coordinates(),a.properties()));
        var bonds=g.bonds().reversed().stream().map(b->new MolecularGraph.Bond("new-"+b.id(),"new-"+b.firstAtomId(),"new-"+b.secondAtomId(),b.order(),b.aromatic(),b.stereochemistry(),b.properties())).toList();
        return new DesignState(source.stateId(), source.representativeNodeId(),null,new MolecularGraph(atoms,bonds,g.properties()),source.provenance(),0);
    }
    private static DesignKnowledge bound(DesignKnowledge k,DesignState target) {
        var region=new DesignGrammar.EditableVector("terminal","c",List.of("ATOM_SUBSTITUTION"),List.of(),List.of(),Set.of("a","b"),List.of(),List.of());
        return change(k,n->hypothesis(n).set("binding",JSON.valueToTree(new ReviewedBinding(ref("binding"),ref("reviewer"),ref("grammar"),region,target))));
    }
    @Test void reviewedRegionBindsThroughUniqueOclCorrespondenceAndPreservesAnchors() throws Exception {
        var k=fixture(); var parent=reordered(k.hypotheses().getFirst().applicableParent()); k=bound(k,parent);
        var d=decision(k,parent,backend); assertEquals(DecisionCode.PROPOSED,d.code());
        assertEquals(Set.of("new-c"),d.proposal().edit().affectedAtomIds()); assertFalse(d.binding().correspondence().ambiguous());
        assertEquals(Set.of("new-a","new-b"),d.proposal().provenance().retainedAnchors().getFirst().atomIds());
        assertTrue(d.proposal().provenance().resources().contains(ref("binding")));
        assertNotNull(new GraphEditTransactionEngine().apply(parent.graph(),d.proposal().edit(),d.proposal().authorization()));
        assertEquals(d,decision(k,parent,backend));
        assertEquals(d,JSON.readValue(JSON.writeValueAsBytes(d),Decision.class));
        try (var journal = new Journal(temporary.resolve("bound-run.jsonl"))) {
            journal.knowledge(k);
            var tree = new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(),backend,backend).generateTraced(
                    parent.graph(),parent.provenance(),new MolecularDesignGraphGenerator.Configuration(GenerationStrategy.ENUMERATIVE,
                            8,1,8,false,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)),
                    new HypothesisDirectedPlanner(k,List.of(ref("h")),Set.of(EvidenceKind.SYNTHETIC_TEST),journal::planningDecision,backend),
                    journal,(p,e,g)->new MolecularDesignGraphGenerator.GeometryResult(true,List.of(),Set.of()));
            assertEquals(Outcome.ACCEPTED,tree.attempts().getFirst().outcome());
            assertEquals("O",tree.attempts().getFirst().resultingProduct().atom("new-c").orElseThrow().element());
            var evaluation=evaluate(k,tree.attempts().getFirst(),Map.of());
            assertEquals(1,k.withEvaluation(snapshot("bound-evaluated"),evaluation).evaluations().size());
        }
    }
    @Test void symmetryDeclinesWithoutSelectingAnArbitrarySite() throws Exception {
        var k=change(fixture(),n->{
            ((ObjectNode)hypothesis(n).path("applicableParent").path("graph").path("atoms").get(0)).put("element","S");
            evidence(n).set("qualification",evidence(n).get("qualification").deepCopy());
            ((ObjectNode)evidence(n).path("qualification")).set("subject",hypothesis(n).get("applicableParent").deepCopy());
        });
        var parent=reordered(k.hypotheses().getFirst().applicableParent());
        var d=decision(bound(k,parent),parent,backend);
        assertEquals(DecisionCode.AMBIGUOUS_SITE,d.code()); assertTrue(d.binding().correspondence().alternatives().size()>1);
    }
    @Test void absentTruncatedAndMissingBackendLineageDeclineExplicitly() throws Exception {
        var k=fixture(); var parent=reordered(k.hypotheses().getFirst().applicableParent()); var bound=bound(k,parent);
        assertEquals(DecisionCode.UNSUPPORTED_LINEAGE,decision(bound,parent,null).code());
        for(boolean exhaustive:List.of(true,false)) {
            CanonicalIdentityService unsupported=new CanonicalIdentityService(){
                public Result identify(MolecularGraph g)throws MolecularBackendException{return backend.identify(g);}
                public Correspondence correspondence(MolecularGraph a,MolecularGraph b){return new Correspondence(List.of(new Mapping(Map.of(),Map.of())),exhaustive,1);}
            };
            assertEquals(DecisionCode.UNSUPPORTED_LINEAGE,decision(bound,parent,unsupported).code());
        }
    }
    @Test void geometryChangesAndUninterpretedRegionRestrictionsCannotBeSmuggledThroughIdentity() throws Exception {
        var k=fixture(); var parent=reordered(k.hypotheses().getFirst().applicableParent());
        ObjectNode node=JSON.valueToTree(parent); ((ObjectNode)node.path("graph").path("atoms").get(0)).set("coordinates",JSON.valueToTree(new MolecularGraph.Coordinates(1,2,3)));
        var moved=JSON.treeToValue(node,DesignState.class);
        assertEquals(DecisionCode.UNSUPPORTED_LINEAGE,decision(bound(k,moved),moved,backend).code());
        var restricted=change(bound(k,parent),n->((ObjectNode)hypothesis(n).path("binding").path("region")).putArray("hardRestrictions").add("unknown"));
        assertEquals(DecisionCode.UNRESOLVED_SITE,decision(restricted,parent,backend).code());
    }
}
