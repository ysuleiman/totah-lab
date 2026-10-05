package totah.lab.daedalus.system;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.ref;

class DirectAssessmentExecutionAcceptanceTest {
    @TempDir Path temp;
    record Result(EvidenceHistory history,SystemQualificationPipeline.Published published,int calls,List<EvidenceEnvelope> received) { }
    static EvidenceEnvelope wrap(SystemStateView s,String id,String type,byte[] bytes) {
        return SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"direct-input"),id,type,bytes,REVIEWER,s.subject(),AT,List.of("synthetic engineering fixture only"));
    }
    static Result run(Path directory,String variant)throws Exception {
        var f=DirectAssessmentResearchFixture.create(SourceSulfurConnectivityAcceptanceTest.model(),variant);var m=variant.equals("schema2")?SourceSulfurConnectivityAcceptanceTest.model():f.manifest();
        var state=SourceSulfurConnectivityAcceptanceTest.state(variant.equals("positive"));
        if(variant.equals("conflict"))state=SourceSulfurConnectivityAcceptanceTest.structural(state,totah.lab.gaia.structure.ConnectivityProvenance.EXPLICIT,true,totah.lab.gaia.chemistry.BondOrder.SINGLE);
        if(variant.equals("unrelated-component"))state=AthenaScientificRulesAcceptanceTest.system(List.of(SourceSulfurConnectivityAcceptanceTest.source(false).graph(),B01FunctionalGroupAcceptanceTest.fixture("aldehyde").graph()),true,false);
        var coverage=SourceSulfurConnectivityAcceptanceTest.proof(state,variant.equals("positive"),"SUPPORTED_PRESENT").getFirst();
        var pipeline=AthenaScientificRulesAcceptanceTest.pipeline();var catalog=new EvidenceSnapshotCatalog(directory);
        var foundation=pipeline.run(catalog,Optional.empty(),state,List.of(coverage),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"direct-foundation"),AT);
        var inputs=new ArrayList<EvidenceEnvelope>();int i=0;
        for(var bytes:new TreeMap<>(f.bytes()).values())inputs.add(wrap(state,"research-"+i++,"fixture:research",bytes));
        byte[] context=ResearchDocuments.encode(f.context());var cp=ResearchGatePipelineAcceptanceTest.pin(context);
        inputs.add(wrap(state,"context","athena:rule-policy-context",context));
        var impl=new RuleImplementationQualification("athena-rule-implementation-qualification/1",variant.equals("bad-implementation")?"wrong":m.key(),f.manifest().research().definitionSha256(),f.manifest().research().domain().sha256(),List.of(f.raw()),List.of(f.raw()),
                variant.equals("missing-check")?List.of():List.of(new RuleImplementationQualification.Check("synthetic-positive",!variant.equals("failed-check"),f.raw(),"synthetic")),REVIEWER,f.raw(),AT);
        byte[] ib=ResearchDocuments.encode(impl);var ip=ResearchGatePipelineAcceptanceTest.pin(ib);inputs.add(wrap(state,"implementation","athena:rule-implementation-qualification",ib));
        if(!Set.of("inherited","no-coverage").contains(variant))inputs.add(coverage);
        // Unsupported opaque evidence must survive admission but never become an evaluator input.
        inputs.add(wrap(state,"opaque","fixture:uninterpreted","uninterpreted".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if(variant.equals("coverage-state")) {
            inputs.remove(coverage);var n=new com.fasterxml.jackson.databind.ObjectMapper().readTree(coverage.readPayload());
            ((com.fasterxml.jackson.databind.node.ObjectNode)n.path("stateBinding")).put("stateSha256","0".repeat(64));
            inputs.add(wrap(state,"wrong-coverage","athena:group-source-coverage",SystemStateView.bytes(n)));
        }
        if(variant.equals("unrelated-component")) {
            var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(coverage.readPayload());
            ((com.fasterxml.jackson.databind.node.ObjectNode)node).set("componentReference",new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(state.components().get(1).identity()));
            inputs.add(wrap(state,"unrelated","athena:group-source-coverage",SystemStateView.bytes(node)));
        }
        if(variant.equals("partial")) {inputs.remove(coverage);inputs.add(wrap(state,"partial","athena:group-source-coverage",SourceSulfurConnectivityAcceptanceTest.proof(state,false,"UNKNOWN_INCONCLUSIVE").getFirst().readPayload()));}
        if(variant.equals("reverse"))Collections.reverse(inputs);
        var old=SourceSulfurConnectivityAcceptanceTest.req(state);
        var request=new RuleRequest(variant.equals("request-state")?SourceSulfurConnectivityAcceptanceTest.state(true).binding():state.binding(),m.key(),variant.equals("request-hash")?"0".repeat(64):RuleRegistry.digest(m),old.atoms(),old.first(),old.second(),old.radiusAngstrom(),old.maximumHops(),old.maximumNodes(),old.maximumCandidates());
        var calls=new AtomicInteger();var clocks=new AtomicInteger();var received=new ArrayList<EvidenceEnvelope>();
        ResearchTimeAuthority clock=variant.equals("no-clock")?null:(c,at)->{
            int n=clocks.incrementAndGet();
            if(variant.equals("time-failure")||variant.equals("final-time-failure")&&n==2)throw new java.io.IOException("fixture time rejection");
            assertTrue(catalog.read(foundation.catalogSnapshot()).isPresent());
        };
        var executor=new CurrentRuleExecution(pipeline,null,clock,(manifest,r)->{
            var actual=RuleAnalyzers.evaluator(manifest,r);
            return new SystemGraphAnalyzer() {
                public ScientificReference method(){return actual.method();}
                public Set<SystemGraphCertificate.Capability> requires(){return actual.requires();}
                public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of(SystemGraphCertificate.Capability.ENERGETICS);}
                public Set<String> evidenceTypes(){return actual.evidenceTypes();}
                public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> e,Map<String,String> cfg)throws Exception {
                    calls.incrementAndGet();received.addAll(e);return actual.analyze(s,e,cfg);
                }
            };
        });
        var supplied=new RuleExecutionPipeline.ResearchExecutionInputs(inputs,cp,ip);
        var published=variant.equals("two-stage")?foundation:variant.equals("public")?new RuleExecutionPipeline(pipeline,null,clock).evaluateCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(m),ResearchDocuments.encode(m),request,supplied,ref(ScientificReference.Kind.ACTIVITY,"direct"),AT)
                :executor.evaluate(catalog,foundation,variant.equals("configuration")?Map.of("wrong","config"):Map.of(),new RuleRegistry().register(m),ResearchDocuments.encode(m),request,supplied,ref(ScientificReference.Kind.ACTIVITY,"direct"),AT);
        if(variant.equals("two-stage"))published=new RuleExecutionPipeline(pipeline,null,clock).runCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(m),ResearchDocuments.encode(m),request,Optional.empty(),supplied,ref(ScientificReference.Kind.ACTIVITY,"two-stage"),AT).published();
        var history=catalog.read(published.catalogSnapshot()).orElseThrow().history();
        for(var e:inputs)assertArrayEquals(e.readPayload(),history.envelopes().get(e.reference()).readPayload());
        assertFalse(history.envelopes().values().stream().anyMatch(e->e.evidenceType().equals("athena:rule-measurements")));
        for(var capability:SystemGraphCertificate.Capability.values())assertEquals(foundation.certificate().capabilities().get(capability).status(),published.certificate().capabilities().get(capability).status(),capability.name());
        return new Result(history,published,calls.get(),List.copyOf(received));
    }
    static List<EvidenceInterpretation> findings(Result r){return r.history.interpretations().values().stream().filter(i->i.evaluator().namespace().equals("athena.ss-connectivity")&&i.measurements().containsKey("assessment")).toList();}
    @Test void qualifiedDirectNegativeExactlyOnceOnlySelectedCoverage()throws Exception {
        var r=run(temp,"valid");assertEquals(1,r.calls);assertEquals(1,r.received.size());assertEquals("athena:group-source-coverage",r.received.getFirst().evidenceType());
        assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,findings(r).getFirst().status());
    }
    @Test void publicEntryPointPersistsDirectFinding()throws Exception {assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,findings(run(temp,"public")).getFirst().status());}
    @ParameterizedTest @ValueSource(strings={"inherited","no-coverage"}) void historicalEvidenceNeverImplicitlySelected(String v)throws Exception {
        var r=run(temp,v);assertEquals(1,r.calls);assertTrue(r.received.isEmpty());assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,findings(r).getFirst().status());
        assertTrue(r.history.envelopes().values().stream().anyMatch(e->e.evidenceType().equals("athena:group-source-coverage")));
    }
    @Test void unrelatedComponentCoverageIsPreservedButNotEvaluated()throws Exception {
        var r=run(temp,"unrelated-component");assertEquals(1,r.calls);assertEquals(1,r.received.size());
        assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,findings(r).getFirst().status());
        assertTrue(r.history.envelopes().values().stream().anyMatch(e->e.reference().id().endsWith("unrelated")));
        assertFalse(findings(r).getFirst().inputs().stream().anyMatch(i->i.reference().id().endsWith("unrelated")));
    }
    @Test void partialCoverageRemainsInconclusive()throws Exception {var r=run(temp,"partial");assertEquals(1,r.calls);assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,findings(r).getFirst().status());}
    @Test void positiveDoesNotGrantCapability()throws Exception {var r=run(temp,"positive");assertEquals(1,r.calls);assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,findings(r).getFirst().status());}
    @Test void conflictsRemainSeparate()throws Exception {var r=run(temp,"conflict");assertEquals(1,r.calls);assertEquals(Set.of(EvidenceInterpretation.Status.SUPPORTED_PRESENT,EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE),new HashSet<>(findings(r).stream().map(EvidenceInterpretation::status).toList()));}
    @ParameterizedTest @ValueSource(strings={"expired","wrong-definition","missing-system","unresolved","missing-artifact","failed-check","missing-check","bad-implementation","no-clock","time-failure","final-time-failure","configuration","request-hash","coverage-state","request-state","schema2","two-stage","invalidated","stale-context"})
    void failuresPreserveEvidenceAndInvokeZeroTimes(String variant)throws Exception {
        var r=run(temp,variant);assertEquals(0,r.calls);assertTrue(findings(r).isEmpty());
        assertTrue(r.history.interpretations().values().stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.FAILED||i.status()==EvidenceInterpretation.Status.UNSUPPORTED));
    }
    @Test void selectedOrderDoesNotChangeScientificFindings()throws Exception {
        var a=run(Files.createDirectory(temp.resolve("a")),"valid");var b=run(Files.createDirectory(temp.resolve("b")),"reverse");
        assertEquals(findings(a).getFirst().measurements(),findings(b).getFirst().measurements());
    }
    public static void main(String[] args)throws Exception {
        var r=run(Files.createDirectories(Path.of(args[0])),"valid");Files.write(Path.of(args[1]),SystemStateView.bytes(Map.of("snapshot",r.published.catalogSnapshot(),"certificate",r.published.certificate(),"calls",r.calls)));
    }
}
