package totah.lab.athena.design.backend.ocl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.generation.*;
import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.athena.design.reasoning.*;
import totah.lab.hermes.pubchem.PubChemMolecularWeightImporter;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.design.generation.MolecularDesignTree.*;
import static totah.lab.athena.design.reasoning.DesignKnowledge.*;
import static totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.*;
import static totah.lab.athena.design.reasoning.HypothesisDirectedPlanner.*;

/** Actual PubChem records, explicitly reviewed associations and existing Phase 2.2 reasoning; no network or campaign. */
class RealSourceEvidenceAcceptanceTest {
    @TempDir Path temporary;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TIME="2026-10-01T16:00:00Z";
    private final DesignGrammarJsonCodec codec=new DesignGrammarJsonCodec();
    private final OclMolecularBackend backend=new OclMolecularBackend();
    private static Reference ref(String id){return new Reference(id,"1");}
    private static Reference snapshot(String version){return new Reference("fixture-knowledge",version);}
    private SourceReceipt source(int cid)throws Exception {
        var raw=Path.of(System.getProperty("basedir"),"../hermes/src/test/resources/pubchem/cid-"+cid+"-molecular-weight.json");
        var path=temporary.resolve("source-"+cid+"-"+UUID.randomUUID()+".json");
        var importer=new PubChemMolecularWeightImporter(); importer.importRecord(raw,path,Instant.parse(TIME));
        importer.readVerified(path); // Host boundary verifies the projection before scientific review.
        return codec.readSourceReceipt(path);
    }
    private static DesignState ethanolState() throws Exception {
        var source=legacyFixture().hypotheses().getFirst().applicableParent();
        ObjectNode state=JSON.valueToTree(source);
        ((ObjectNode)state.path("graph").path("atoms").get(2)).put("element","O");
        return JSON.treeToValue(state,DesignState.class);
    }
    private static Semantics semantics(SourceReceipt s){return new Semantics(s.method(),s.endpoint(),s.system(),s.conditions(),s.unit(),s.valueKind());}
    private static SourceReview review(SourceReceipt s,DesignState state,String id){
        return new SourceReview(ref(id),ref("fixture-reviewer"),ref("manual-compound-association"),TIME,true,
                "Reviewed named PubChem compound against this fixture's explicit molecular structure",s.identifier(),state,semantics(s),
                ScientificStatus.SCREENING_ONLY,UncertaintyKind.POINT_UNKNOWN,"No source uncertainty estimate is available",s.claimBoundary(),
                "Computed property only; subject association is explicit fixture review, not automated CID-to-graph inference");
    }
    private static DesignKnowledge legacyFixture()throws Exception {
        try(var input=RealSourceEvidenceAcceptanceTest.class.getResourceAsStream("/reasoning/qualified-local-replacement.json")){
            return JSON.readValue(input,DesignKnowledge.class);
        }
    }
    private static Requirement requirement(SourceReceipt s){return new Requirement(semantics(s),Set.of(EvidenceKind.COMPUTATIONAL),Set.of(ScientificStatus.SCREENING_ONLY));}
    private static DesignKnowledge knowledge(SourceDecision parent,SourceReceipt product)throws Exception {
        var previous=legacyFixture();var old=previous.hypotheses().getFirst();
        var criterion=new Criterion("mass","Test the source-reported computed product mass against this reviewed window",false,
                requirement(product),new Value(45.,47.,""),ref("fixture-product-mass-window"),ref("fixture-author"));
        var h=new Hypothesis(old.reference(),null,"Test the local replacement against a scoped computed-mass objective; Y is unknown",parent.receipt().context(),
                List.of(parent.evidenceReference()),List.of(),old.applicableParent(),old.site(),old.retainedAnchors(),old.eligibleRule(),old.geometryConstraints(),
                "Test whether product PubChem molecular weight is in [45,47] g/mol; no biological inference",old.prediction(),"Source values are computed; criterion is fixture policy",
                List.of(criterion,old.evaluationCriteria().get(1)),List.of(new EvidenceRequirement(parent.evidenceReference(),requirement(parent.receipt()))),null,List.of());
        return new DesignKnowledge(QUALIFIED_SCHEMA,snapshot("source-1"),List.of(parent.evidence()),List.of(h),previous.rules(),List.of());
    }
    private Decision decision(DesignKnowledge k)throws Exception {
        var out=new ArrayList<Decision>();var h=k.hypotheses().getLast();
        new HypothesisDirectedPlanner(k,List.of(h.reference()),Set.of(EvidenceKind.COMPUTATIONAL),out::add,backend).editsFor(h.applicableParent());
        return out.getFirst();
    }
    private SourceDecision register(SourceReceipt source,DesignState state,String name)throws Exception {
        return codec.registerSourceReview(temporary.resolve("registry"),source,review(source,state,"review-"+name),ref(name));
    }
    @Test void actualSourceFlowsThroughPlanningOclEvaluationAndImmutableRevision()throws Exception {
        var before=source(6343);var product=source(702);
        var parentState=legacyFixture().hypotheses().getFirst().applicableParent();
        assertEquals("Ethanethiol",before.title()); assertEquals("Ethanol",product.title());
        var prior=register(before,parentState,"prior-real");var k=knowledge(prior,product);
        assertEquals(62.14,prior.evidence().value().lower());
        assertEquals(DecisionCode.PROPOSED,decision(k).code());
        var journalPath=temporary.resolve("run.jsonl");MolecularDesignTree tree;
        try(var journal=new Journal(journalPath)) {
            journal.sourceReview(prior);journal.knowledge(k);
            tree=new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(),backend,backend).generateTraced(parentState.graph(),parentState.provenance(),
                    new MolecularDesignGraphGenerator.Configuration(GenerationStrategy.ENUMERATIVE,8,1,8,false,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)),
                    new HypothesisDirectedPlanner(k,List.of(ref("h")),Set.of(EvidenceKind.COMPUTATIONAL),journal::planningDecision,backend),journal,
                    (p,e,g)->new MolecularDesignGraphGenerator.GeometryResult(true,List.of(),Set.of()));
            var a=tree.attempts().getFirst();assertEquals(Outcome.ACCEPTED,a.outcome());
            var state=tree.states().stream().filter(s->s.stateId().equals(a.resultingStateId())).findFirst().orElseThrow();
            assertEquals("O",state.graph().atom("c").orElseThrow().element());
            var observed=register(product,state,"product-real");assertEquals(46.07,observed.evidence().value().lower());
            assertTrue(observed.evidence().qualification().uncertainty().contains("not zero physical uncertainty"));
            journal.sourceReview(observed);
            var withEvidence=k.withEvidence(snapshot("source-2"),observed.evidence());
            var evaluation=withEvidence.evaluate(ref("evaluation"),ref("h"),"real-source-fixture",a,ref("criterion-evaluator"),Map.of("mass",observed.evidenceReference()));
            assertEquals(Conclusion.SUPPORTS,evaluation.findings().get(0).conclusion());
            assertEquals(Conclusion.NOT_MEASURED,evaluation.findings().get(1).conclusion());
            var assessed=withEvidence.withEvaluation(snapshot("source-3"),evaluation);
            var revised=assessed.revise(snapshot("source-4"),ref("h"),"2",evaluation.reference(),"Computed mass meets window; Y remains unmeasured",
                    "Do not claim retention or biological improvement",null,"unknown Y and source uncertainty retained");
            assertEquals(DecisionCode.UNRESOLVED_EVIDENCE,decision(revised).code());
            assertEquals(k.hypotheses().getFirst(),revised.hypothesis(ref("h")).orElseThrow());
            var saved=temporary.resolve("snapshot.json");codec.writeKnowledge(saved,revised,temporary.resolve("registry"));
            var loaded=new DesignGrammarJsonCodec().readKnowledge(saved,temporary.resolve("registry"));
            assertEquals(revised,loaded);assertEquals(decision(revised),decision(loaded));
            assertEquals(evaluation,loaded.evaluate(ref("evaluation"),ref("h"),"real-source-fixture",a,ref("criterion-evaluator"),Map.of("mass",observed.evidenceReference())));
            assertEquals(observed,new DesignGrammarJsonCodec().readSourceReview(temporary.resolve("registry"),observed.evidenceReference()));
            assertEquals(product.rawBase64(),codec.readSourceReview(temporary.resolve("registry"),observed.evidenceReference()).receipt().rawBase64());
            journal.knowledge(assessed);journal.knowledge(revised);
        }
        var lines=Files.readAllLines(journalPath);assertEquals(2,lines.stream().filter(s->s.contains("\"event\":\"source-review\"")).count());
        var data=JSON.readTree(lines.getFirst()).path("data");assertEquals(prior,JSON.treeToValue(data,SourceDecision.class));
        assertEquals(before.sha256(),prior.evidence().source().version());
    }
    @Test void reviewCannotInventEndpointMethodSystemConditionsUnitStatusOrUncertainty()throws Exception {
        var source=source(702);var state=ethanolState();
        for(String dimension:List.of("endpoint","method","system","conditions","unit","status","uncertaintyKind","claimBoundary","scientificSubject","subject")) {
            ObjectNode n=JSON.valueToTree(review(source,state,"r-"+dimension));
            switch(dimension) {
                case "endpoint","system" -> ((ObjectNode)n.path("semantics").path(dimension)).put("id","invented");
                case "method" -> ((ObjectNode)n.path("semantics").path("method")).put("name","other");
                case "conditions" -> ((ObjectNode)n.path("semantics").path("conditions")).put("temperature","298 K");
                case "unit" -> ((ObjectNode)n.path("semantics")).put("unit","kg/mol");
                case "status" -> n.put("status","VALIDATED_REFERENCE");
                case "uncertaintyKind" -> n.put("uncertaintyKind","REPORTED_INTERVAL");
                case "subject" -> n.putNull("subject");
                default -> n.put(dimension,"invented");
            }
            var result=codec.registerSourceReview(temporary.resolve("registry"),source,JSON.treeToValue(n,SourceReview.class),ref(dimension));
            assertNull(result.evidence(),dimension);assertFalse(result.reasons().isEmpty());
            assertEquals(result,codec.readSourceReview(temporary.resolve("registry"),ref(dimension)));
        }
    }
    @Test void explicitReviewRejectionIsDurableAndCannotBePromotedOnReload()throws Exception {
        var source=source(702);ObjectNode n=JSON.valueToTree(review(source,ethanolState(),"reject"));
        n.put("approve",false);n.put("reason","Subject association has not been verified");
        var rejected=codec.registerSourceReview(temporary.resolve("registry"),source,JSON.treeToValue(n,SourceReview.class),ref("declined"));
        assertNull(rejected.evidence());assertTrue(rejected.reasons().getFirst().contains("reviewer rejected"));
        try(var journal=new Journal(temporary.resolve("decline.jsonl"))){journal.sourceReview(rejected);}
        assertEquals(rejected,new DesignGrammarJsonCodec().readSourceReview(temporary.resolve("registry"),ref("declined")));
        assertThrows(java.io.IOException.class,()->codec.registerSourceReview(temporary.resolve("registry"),source,
                review(source,ethanolState(),"accept"),ref("declined")));
    }
    @Test void evidenceIdentityCannotBeReboundAcrossIndependentCodecInstances()throws Exception {
        var source=source(702);var state=ethanolState();
        var first=register(source,state,"same");
        assertEquals(first,new DesignGrammarJsonCodec().registerSourceReview(temporary.resolve("registry"),source,first.review(),ref("same")));
        var other=source(6343);
        assertThrows(java.io.IOException.class,()->new DesignGrammarJsonCodec().registerSourceReview(temporary.resolve("registry"),other,review(other,legacyFixture().hypotheses().getFirst().applicableParent(),"different"),ref("same")));
        assertEquals(first,codec.readSourceReview(temporary.resolve("registry"),ref("same")));
    }
    @Test void independentlyLoadedSnapshotCannotReplaceReservedEvidence()throws Exception {
        var prior=register(source(6343),legacyFixture().hypotheses().getFirst().applicableParent(),"prior-real");var k=knowledge(prior,source(702));
        ObjectNode n=JSON.valueToTree(k);((ObjectNode)n.path("evidence").get(0)).put("claim","different immutable content");
        var altered=JSON.treeToValue(n,DesignKnowledge.class);var raw=temporary.resolve("external-snapshot.json");
        codec.writeKnowledge(raw,altered); // Legacy ungovened writer is intentionally still available for historical snapshots.
        assertThrows(java.io.IOException.class,()->new DesignGrammarJsonCodec().readKnowledge(raw,temporary.resolve("registry")));
        var out=temporary.resolve("must-not-exist.json");
        assertThrows(java.io.IOException.class,()->codec.writeKnowledge(out,altered,temporary.resolve("registry")));assertFalse(Files.exists(out));
    }
    @Test void strictPlanningRequirementsStillRejectUnlikeRealEvidence()throws Exception {
        var prior=register(source(6343),legacyFixture().hypotheses().getFirst().applicableParent(),"prior-real");var k=knowledge(prior,source(702));
        for(String dimension:List.of("endpoint","method","system","conditions","unit")) {
            ObjectNode n=JSON.valueToTree(k);var semantics=(ObjectNode)n.path("hypotheses").get(0).path("requirements").get(0).path("requirement").path("semantics");
            if(dimension.equals("unit")) semantics.put("unit","other");
            else ((ObjectNode)semantics.path(dimension)).put(dimension.equals("method")?"name":dimension.equals("conditions")?"temperature":"id","other");
            assertEquals(DecisionCode.NON_COMPARABLE_EVIDENCE,decision(JSON.treeToValue(n,DesignKnowledge.class)).code(),dimension);
        }
    }
    @Test void parseErrorsAreRecordedAndCannotBeCuredBySupplyingConvenientReviewSemantics()throws Exception {
        var s=source(702);ObjectNode receipt=JSON.valueToTree(s);receipt.putArray("errors").add("missing method in source");receipt.putNull("method");
        var incomplete=JSON.treeToValue(receipt,SourceReceipt.class);
        var result=codec.registerSourceReview(temporary.resolve("registry"),incomplete,review(s,ethanolState(),"review"),ref("missing"));
        assertNull(result.evidence());assertFalse(result.reasons().isEmpty());
    }
    @Test void corruptedRawPayloadAndTruncatedReservationFailClosed()throws Exception {
        var s=source(702);ObjectNode n=JSON.valueToTree(s);n.put("rawBase64",Base64.getEncoder().encodeToString(new byte[]{1}));
        assertThrows(Exception.class,()->JSON.treeToValue(n,SourceReceipt.class));
        var decision=register(s,ethanolState(),"e");
        Path file;try(var files=Files.list(temporary.resolve("registry"))){file=files.findFirst().orElseThrow();}
        Files.writeString(file,"{");
        assertThrows(java.io.IOException.class,()->codec.registerSourceReview(temporary.resolve("registry"),s,decision.review(),ref("e")));
        assertEquals("{",Files.readString(file));
    }

    @Test void concurrentConflictingReservationsHaveOnlyOneWinner() throws Exception {
        var source=source(702);var state=ethanolState();var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(String id:List.of("review-a","review-b")) futures.add(executor.submit(()->{
                start.await();
                try { new DesignGrammarJsonCodec().registerSourceReview(temporary.resolve("registry"),source,review(source,state,id),ref("race")); return true; }
                catch(java.io.IOException conflict) { return false; }
            }));
            start.countDown();int accepted=0;for(var future:futures) if(future.get()) accepted++;
            assertEquals(1,accepted);assertNotNull(codec.readSourceReview(temporary.resolve("registry"),ref("race")).evidence());
        }
    }
}
