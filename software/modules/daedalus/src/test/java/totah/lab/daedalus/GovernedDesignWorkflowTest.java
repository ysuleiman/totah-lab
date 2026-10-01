package totah.lab.daedalus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.generation.*;
import totah.lab.athena.design.reasoning.HypothesisDirectedPlanner;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.athena.design.reasoning.DesignKnowledge;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.design.generation.MolecularDesignTree.*;
import static totah.lab.athena.design.reasoning.DesignKnowledge.*;
import static totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.*;

class GovernedDesignWorkflowTest {
    @TempDir Path temporary;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant TIME=Instant.parse("2026-10-01T16:00:00Z");
    private final OclMolecularBackend backend=new OclMolecularBackend();
    private static Reference ref(String id){return new Reference(id,"1");}
    private static Path raw(int cid,boolean structure){return Path.of(System.getProperty("basedir"),"../hermes/src/test/resources/pubchem/cid-"+cid+(structure?"-structure.json":"-molecular-weight.json"));}
    private static DesignKnowledge fixture()throws Exception {
        return JSON.readValue(Files.readAllBytes(Path.of(System.getProperty("basedir"),"../athena-openchemlib/src/test/resources/reasoning/qualified-local-replacement.json")),DesignKnowledge.class);
    }
    private static SourceReview review(SourceReceipt source,DesignState state){return new SourceReview(ref("review"),ref("reviewer"),ref("process"),TIME.toString(),true,"explicit relevance review",
            source.identifier(),state,new Semantics(source.method(),source.endpoint(),source.system(),source.conditions(),source.unit(),source.valueKind()),ScientificStatus.SCREENING_ONLY,
            UncertaintyKind.POINT_UNKNOWN,"unreported uncertainty",source.claimBoundary(),"not biological evidence");}
    private SourceDecision imported(GovernedDesignWorkflow run)throws Exception {
        return run.reviewPubChem(raw(6343,false),raw(6343,true),TIME,ref("prior"),s->{
            try{return review(s,fixture().hypotheses().getFirst().applicableParent());}catch(Exception e){throw new IllegalStateException(e);}
        });
    }
    private static DesignKnowledge knowledge(SourceDecision decision)throws Exception {
        ObjectNode n=JSON.valueToTree(fixture());n.putArray("evidence").add(JSON.valueToTree(decision.evidence()));
        var h=(ObjectNode)n.path("hypotheses").get(0);h.put("context",decision.evidence().context());
        h.putArray("requirements").add(JSON.valueToTree(new EvidenceRequirement(ref("prior"),new Requirement(decision.evidence().qualification().semantics(),Set.of(EvidenceKind.COMPUTATIONAL),Set.of(ScientificStatus.SCREENING_ONLY)))));
        return JSON.treeToValue(n,DesignKnowledge.class);
    }
    @Test void factoryBindsSharedRegistryAndJournalsVerifiedLineageBeforePlanning()throws Exception {
        var factory=new PipelineFactory(temporary);Path snapshot,journal;SourceDecision first;
        try(var run=factory.openDesignReasoningRun(backend)) {
            first=imported(run);assertTrue(first.association().proof().proven());snapshot=run.saveKnowledge("knowledge.json",knowledge(first));
            assertEquals(1,run.plan(snapshot,List.of(ref("h")),Set.of(EvidenceKind.COMPUTATIONAL),first.review().subject()).size());
            journal=run.runDirectory().resolve("reasoning.jsonl");
            assertEquals(temporary.resolve("design-evidence-registry"),run.registryDirectory());
        }
        try(var second=factory.openDesignReasoningRun(backend)) {
            assertEquals(knowledge(first),second.load(snapshot));assertEquals(first,new DesignGrammarJsonCodec().readSourceReview(second.registryDirectory(),ref("prior")));
        }
        var events=new ArrayList<String>();for(String line:Files.readAllLines(journal))events.add(JSON.readTree(line).path("event").asText());
        int planned=events.indexOf("planning-decision");assertTrue(events.indexOf("source-review")<events.indexOf("knowledge"));assertTrue(events.lastIndexOf("knowledge")<planned);
        try(var wrong=new PipelineFactory(temporary.resolve("other")).openDesignReasoningRun(backend)) {assertThrows(java.io.IOException.class,()->wrong.load(snapshot));}
    }
    @Test void malformedOrWrongCidSourceCannotReachReviewer()throws Exception {
        var bad=temporary.resolve("bad.json");Files.writeString(bad,"{}");var called=new AtomicBoolean();
        try(var run=new PipelineFactory(temporary).openDesignReasoningRun(backend)) {
            assertThrows(java.io.IOException.class,()->run.reviewPubChem(bad,raw(6343,true),TIME,ref("bad"),s->{called.set(true);return null;}));
            assertThrows(java.io.IOException.class,()->run.reviewPubChem(raw(6343,false),raw(702,true),TIME,ref("wrong"),s->{called.set(true);return null;}));
            assertFalse(called.get());
            assertTrue(Files.readString(run.runDirectory().resolve("reasoning.jsonl")).contains("evidence-protocol-failure"));
        }
    }
    @Test void wrongMoleculeIsRecordedAsRejectedAndIdentityDoesNotOverrideRelevance()throws Exception {
        try(var run=new PipelineFactory(temporary).openDesignReasoningRun(backend)) {
            var parent=fixture().hypotheses().getFirst().applicableParent();
            var wrong=run.reviewPubChem(raw(702,false),raw(702,true),TIME,ref("wrong"),s->review(s,parent));
            assertNull(wrong.evidence());assertFalse(wrong.association().proof().proven());
            var rejected=run.reviewPubChem(raw(6343,false),raw(6343,true),TIME,ref("not-relevant"),s->{
                ObjectNode n=JSON.valueToTree(review(s,parent));n.put("approve",false);n.put("reason","endpoint irrelevant to the scientific question");return JSON.convertValue(n,SourceReview.class);
            });
            assertTrue(rejected.association().proof().proven());assertNull(rejected.evidence());
            assertTrue(rejected.reasons().stream().anyMatch(r->r.contains("reviewer rejected")));
        }
    }
    @Test void truncatedBackendProofCannotCertifyAssociation()throws Exception {
        CanonicalIdentityService incomplete=new CanonicalIdentityService(){
            public Result identify(MolecularGraph graph)throws MolecularBackendException{return backend.identify(graph);}
            public MolecularGraph decodeStructure(String format,String text)throws MolecularBackendException{return backend.decodeStructure(format,text);}
            public Correspondence correspondence(MolecularGraph a,MolecularGraph b)throws MolecularBackendException{return new Correspondence(backend.correspondence(a,b).alternatives(),false,1);}
        };
        try(var run=new PipelineFactory(temporary).openDesignReasoningRun(incomplete)){assertNull(imported(run).evidence());}
    }
    @Test void legacyUncertifiedAndConflictingSnapshotsFailClosed()throws Exception {
        var factory=new PipelineFactory(temporary);
        try(var run=factory.openDesignReasoningRun(backend)) {
            var d=imported(run);var k=knowledge(d);var codec=new DesignGrammarJsonCodec();
            ObjectNode n=JSON.valueToTree(k);((ObjectNode)n.path("evidence").get(0)).put("claim","invented claim");
            var bad=temporary.resolve("changed.json");codec.writeKnowledge(bad,JSON.treeToValue(n,DesignKnowledge.class));
            assertThrows(java.io.IOException.class,()->run.load(bad));
            assertThrows(java.io.IOException.class,()->codec.registerSourceReview(run.registryDirectory(),d.receipt(),d.review(),d.evidenceReference()));
            // A legacy reservation in a different factory workspace cannot silently acquire certification.
            var legacyRoot=temporary.resolve("legacy");codec.registerSourceReview(legacyRoot.resolve("design-evidence-registry"),d.receipt(),d.review(),ref("prior"));
            // A pre-2.4 JSON reservation lacks the optional association property. Idempotent legacy replay still works.
            try(var files=Files.list(legacyRoot.resolve("design-evidence-registry"))) {
                var reservation=files.findFirst().orElseThrow();ObjectNode old=JSON.readValue(Files.readAllBytes(reservation),ObjectNode.class);
                old.remove("association");Files.write(reservation,JSON.writeValueAsBytes(old));
            }
            assertNull(codec.registerSourceReview(legacyRoot.resolve("design-evidence-registry"),d.receipt(),d.review(),ref("prior")).association());
            var path=temporary.resolve("legacy.json");codec.writeKnowledge(path,k);
            try(var legacy=new PipelineFactory(legacyRoot).openDesignReasoningRun(backend)){assertThrows(java.io.IOException.class,()->legacy.load(path));}
        }
    }
    @Test void governedEvaluationAndReplayPreserveAllFiveOutcomes() throws Exception {
        try(var run=new PipelineFactory(temporary).openDesignReasoningRun(backend)) {
            var k=knowledge(imported(run));var parent=k.hypotheses().getFirst().applicableParent();
            // Reuse the frozen synthetic CCS -> CCO fixture, never a downstream compound design.
            var seed=run.saveKnowledge("seed.json",k);
            var planned=run.plan(seed,List.of(ref("h")),Set.of(EvidenceKind.COMPUTATIONAL),parent);
            var tree=new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(),backend,backend).generateTraced(
                    parent.graph(),parent.provenance(),new MolecularDesignGraphGenerator.Configuration(GenerationStrategy.ENUMERATIVE,
                            8,1,8,false,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)),
                    state->planned,
                    a->{},(p,e,g)->new MolecularDesignGraphGenerator.GeometryResult(true,List.of(),Set.of()));
            var attempt=tree.attempts().getFirst();assertEquals(Outcome.ACCEPTED,attempt.outcome());
            var subject=new DesignState(attempt.resultingStateId(),"node-0001",attempt.parentStateId(),attempt.resultingProduct(),attempt.operation().provenance(),1);
            var sem=k.hypotheses().getFirst().evaluationCriteria().getFirst().requirement().semantics();
            var expected=List.of(Conclusion.SUPPORTS,Conclusion.CONTRADICTS,Conclusion.UNRESOLVED,Conclusion.FAILED_INVALID,Conclusion.NOT_MEASURED);
            for(int i=0;i<expected.size();i++) {
                var id=ref("observed-"+i);var status=i==3?ScientificStatus.NUMERICAL_FAILURE:ScientificStatus.SCREENING_ONLY;
                var value=i==0?new Value(-16.0,-16.0,""):i==1?new Value(1.0,2.0,""):i==2?new Value(-15.0,0.0,""):null;
                var review=new SourceReview(ref("review-"+i),ref("test-reviewer"),ref("test-process"),TIME.toString(),true,"synthetic test only",
                        "existing toy fixture",subject,sem,status,UncertaintyKind.REPORTED_INTERVAL,"authored test interval","synthetic observation","not real science");
                String raw=JSON.writeValueAsString(Map.of("test-case",i,"origin","frozen synthetic fixture"));String hash=ContentHash.sha256(raw);
                var e=new Evidence(id,EvidenceKind.SYNTHETIC_TEST,ReviewStatus.REVIEWED,review.claimBoundary(),k.hypotheses().getFirst().context(),
                        new Reference("test-input",hash),"synthetic test only",new Qualification(sem,status,subject,review.reference(),"authored test interval"),value);
                var interpretation=new Interpretation(review,ref("test-policy"),"synthetic-test-fixture",raw,hash,review.claimBoundary(),e);
                run.registerInterpretation(interpretation);
                if(i==0) {
                    ObjectNode conflict=JSON.valueToTree(interpretation);
                    ((ObjectNode)conflict.path("evidence")).set("reference",JSON.valueToTree(ref("prior")));
                    var conflicting=JSON.treeToValue(conflict,Interpretation.class);
                    assertThrows(java.io.IOException.class,()->run.registerInterpretation(conflicting));
                }
                var updated=k.withEvidence(new Reference(k.reference().id(),"observation-"+i),e);
                var snapshot=run.saveKnowledge("observed-"+i+".json",updated);
                var evaluated=run.evaluate(snapshot,"evaluated-"+i+".json",new Reference(k.reference().id(),"evaluated-"+i),ref("evaluation-"+i),
                        ref("h"),"toy-run",attempt,ref("reviewer"),i==4?Map.of():Map.of("mass",id));
                var replay=run.load(evaluated);var findings=replay.evaluations().getFirst().findings();
                assertEquals(expected.get(i),findings.stream().filter(f->f.criterionId().equals("mass")).findFirst().orElseThrow().conclusion());
                assertEquals(Conclusion.NOT_MEASURED,findings.stream().filter(f->f.criterionId().equals("Y")).findFirst().orElseThrow().conclusion());
            }
        }
    }
}
