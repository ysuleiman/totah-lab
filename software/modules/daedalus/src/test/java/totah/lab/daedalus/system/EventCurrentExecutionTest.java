package totah.lab.daedalus.system;

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
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.*;

/** Artificial research/authority context tests only; no production qualification receipt. */
class EventCurrentExecutionTest {
    @TempDir Path temporary;
    @ParameterizedTest @ValueSource(strings={"valid","public","inherited","missing-plan","expired","missing-artifact","bad-implementation","time-failure","hash","state","configuration"})
    void governedExecutionPreservesInputsAndEvaluatesOnlyAfterGates(String variant)throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");f.state(b,List.of(f.event(b,k,EvidenceInterpretation.Status.SUPPORTED_PRESENT,false)));f.projection("p",b,List.of(k),true);
        var unqualified=RuleRegistry.decode(Files.readAllBytes(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/events-v1/ATHENA.EVENT.EXPLICIT_COVERAGE_ANALYSIS.rule.json")));
        var legacy=DirectAssessmentResearchFixture.create(unqualified,variant);var modern=ResearchV2Fixtures.upgrade(legacy,variant);var m=modern.manifest();
        var pipeline=pipeline();var catalog=new EvidenceSnapshotCatalog(temporary);
        var eventInputs=f.inputs();var foundation=pipeline.run(catalog,Optional.empty(),f.state,eventInputs,Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"events-foundation"),AT);
        var artifacts=new ArrayList<EvidenceEnvelope>();int serial=0;
        for(var bytes:new TreeMap<>(modern.bytes()).values())artifacts.add(DirectAssessmentExecutionAcceptanceTest.wrap(f.state,"research"+serial++,"fixture:research",bytes));
        var contextBytes=ResearchDocuments.encode(modern.context());var contextPin=ResearchGatePipelineAcceptanceTest.pin(contextBytes);artifacts.add(DirectAssessmentExecutionAcceptanceTest.wrap(f.state,"context","athena:rule-policy-context",contextBytes));
        var implementation=new RuleImplementationQualificationV2("athena-rule-implementation-qualification/2",variant.equals("bad-implementation")?"wrong":m.key(),m.research().definitionSha256(),m.research().domain().sha256(),RuleRegistry.digest(m),List.of(modern.raw()),List.of(modern.raw()),List.of(new RuleImplementationQualification.Check("synthetic-positive",true,modern.raw(),"engineering fixture, not scientific qualification")),ResearchV2Fixtures.EXECUTOR,modern.raw(),AT);
        var implBytes=ResearchDocuments.encode(implementation);var implPin=ResearchGatePipelineAcceptanceTest.pin(implBytes);artifacts.add(DirectAssessmentExecutionAcceptanceTest.wrap(f.state,"implementation","athena:rule-implementation-qualification",implBytes));
        if(!variant.equals("inherited"))for(var input:eventInputs)if(!variant.equals("missing-plan")||!input.evidenceType().equals("athena:event-analysis-plan"))artifacts.add(input);
        var request=new RuleRequest(variant.equals("state")?SourceSulfurConnectivityAcceptanceTest.state(true).binding():b,m.key(),variant.equals("hash")?"0".repeat(64):RuleRegistry.digest(m),List.of(),List.of(),List.of(),4.5,8,100,100);
        var calls=new AtomicInteger();var received=new ArrayList<EvidenceEnvelope>();
        var executor=new CurrentRuleExecution(pipeline,null,(context,at)->{if(variant.equals("time-failure"))throw new java.io.IOException("synthetic time failure");},(manifest,r)->{
            var actual=RuleAnalyzers.evaluator(manifest,r);return new SystemGraphAnalyzer(){
                public ScientificReference method(){return actual.method();}public Set<SystemGraphCertificate.Capability> requires(){return actual.requires();}public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}public Set<String> evidenceTypes(){return actual.evidenceTypes();}
                public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> inputs,Map<String,String> cfg)throws Exception{calls.incrementAndGet();received.addAll(inputs);return actual.analyze(s,inputs,cfg);}
            };
        });
        var pub=variant.equals("public")?new RuleExecutionPipeline(pipeline,null,(c,at)->{}).evaluateCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(m),ResearchDocuments.encode(m),request,new RuleExecutionPipeline.ResearchExecutionInputs(artifacts,contextPin,implPin),ref(ScientificReference.Kind.ACTIVITY,"events-current"),AT):executor.evaluate(catalog,foundation,variant.equals("configuration")?Map.of("unexpected","setting"):Map.of(),new RuleRegistry().register(m),ResearchDocuments.encode(m),request,new RuleExecutionPipeline.ResearchExecutionInputs(artifacts,contextPin,implPin),ref(ScientificReference.Kind.ACTIVITY,"events-current"),AT);
        var history=catalog.read(pub.catalogSnapshot()).orElseThrow().history();for(var input:artifacts)assertArrayEquals(input.readPayload(),history.envelopes().get(input.reference()).readPayload());
        assertEquals(variant.equals("valid")?1:0,calls.get(),()->history.interpretations().values().stream().filter(i->i.status()==EvidenceInterpretation.Status.FAILED).map(i->i.evaluator()+" "+i.reasons()).toList().toString());
        if(Set.of("valid","public").contains(variant)) {
            assertTrue(history.interpretations().values().stream().anyMatch(i->i.evaluator().namespace().equals("athena.events")&&i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT));
            if(variant.equals("valid"))assertEquals(new HashSet<>(eventInputs),new HashSet<>(received));
            assertFalse(history.envelopes().values().stream().anyMatch(e->e.evidenceType().equals("athena:rule-measurements")));
        }
        for(var capability:SystemGraphCertificate.Capability.values())assertEquals(foundation.certificate().capabilities().get(capability).status(),pub.certificate().capabilities().get(capability).status());
    }
}
