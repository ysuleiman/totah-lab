package totah.lab.daedalus.system;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.REVIEWER;

class ResearchGatePipelineAcceptanceTest {
    @TempDir Path temp;
    record Outcome(EvidenceHistory history,RuleExecutionPipeline.Result result,RuleManifest manifest,RulePolicyContext context) { }
    static RuleManifest.Source pin(byte[] value){String h=EvidenceExchange.sha256(value);return new RuleManifest.Source("sha256:"+h,h,"synthetic test pin");}
    static Outcome run(Path directory,String variant)throws Exception {
        var f=ResearchGateAcceptanceTest.fixture(variant);var m=f.manifest();
        var chemical=B01FunctionalGroupAcceptanceTest.fixture("aldehyde");var state=system(List.of(chemical.graph()),true,false);
        var pipeline=pipeline();var catalog=new EvidenceSnapshotCatalog(directory);
        var foundation=pipeline.run(catalog,Optional.empty(),state,List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"foundation"),AT);
        var inputs=new ArrayList<EvidenceEnvelope>();int i=0;
        for(var bytes:new TreeMap<>(f.bytes()).values())inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"artifact"),"a"+i++,"fixture:research-source",bytes,REVIEWER,state.subject(),AT,List.of("synthetic administrative fixture")));
        byte[] context=ResearchDocuments.encode(f.context());var contextPin=pin(context);
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"context"),"source","athena:rule-policy-context",context,REVIEWER,state.subject(),AT,List.of()));
        var implementation=new RuleImplementationQualification("athena-rule-implementation-qualification/1",m.key(),m.research().definitionSha256(),m.research().domain().sha256(),
                List.of(f.raw()),List.of(f.raw()),variant.equals("missing-check")?List.of():List.of(new RuleImplementationQualification.Check("synthetic-positive",!variant.equals("failed-check"),f.raw(),"synthetic domain")),REVIEWER,f.raw(),AT);
        byte[] implementationBytes=ResearchDocuments.encode(implementation);var implementationPin=pin(implementationBytes);
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"implementation"),"source","athena:rule-implementation-qualification",implementationBytes,REVIEWER,state.subject(),AT,List.of()));
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"coverage"),"source","athena:group-source-coverage",SystemStateView.bytes(B01FunctionalGroupAcceptanceTest.coverage(state,chemical)),REVIEWER,state.subject(),AT,List.of()));
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"definition"),"source","athena:group-definition",m.parameters().get("definition").value().getBytes(java.nio.charset.StandardCharsets.UTF_8),REVIEWER,state.subject(),AT,List.of()));
        var executor=variant.equals("missing-time-authority")?new RuleExecutionPipeline(pipeline,OCL):new RuleExecutionPipeline(pipeline,OCL,(c,at)->{
            if(variant.equals("replayed-current-time")||!AT.equals(at)||!c.issuer().equals(REVIEWER))throw new java.io.IOException("time context not authorized by fixture authority");
        });
        var result=executor.runCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(m),ResearchDocuments.encode(m),B01FunctionalGroupAcceptanceTest.request(state,m),Optional.empty(),
                new RuleExecutionPipeline.ResearchExecutionInputs(inputs,contextPin,implementationPin),ref(ScientificReference.Kind.ACTIVITY,"current"),AT);
        var history=catalog.read(result.published().catalogSnapshot()).orElseThrow().history();
        for(var input:inputs)assertArrayEquals(input.readPayload(),history.envelopes().get(input.reference()).readPayload());
        return new Outcome(history,result,m,f.context());
    }
    @Test void currentExecutionPreservesEverythingBeforeQualifiedClassification()throws Exception {
        var out=run(temp,"valid");assertTrue(out.result.measurements().isPresent());
        assertTrue(out.history.interpretations().values().stream().anyMatch(x->x.evaluator().id().endsWith("/evaluate")&&x.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT),out.history.interpretations().toString());
        assertEquals(1,out.history.envelopes().values().stream().filter(e->e.evidenceType().equals("athena:rule-qualification-receipt")).count());
    }
    @ParameterizedTest @ValueSource(strings={"expired","wrong-definition","missing-system","unresolved","invalidated","failed-check","missing-check","missing-time-authority","replayed-current-time"})
    void failedGatePreservesInputsAndRawMeasurementsWithoutClassification(String variant)throws Exception {
        var out=run(temp,variant);assertTrue(out.result.measurements().isPresent());
        assertFalse(out.history.envelopes().values().stream().anyMatch(e->e.evidenceType().equals("athena:rule-qualification-receipt")));
        assertFalse(out.history.interpretations().values().stream().anyMatch(x->x.evaluator().id().endsWith("/evaluate")));
        assertTrue(out.history.interpretations().values().stream().anyMatch(x->x.status()==EvidenceInterpretation.Status.FAILED||x.evaluator().id().equals("research-eligibility")&&x.status()==EvidenceInterpretation.Status.UNSUPPORTED));
    }
    @Test void forgedReceiptStatusOrModeCannotMakeAHandle()throws Exception {
        var out=run(temp,"valid");var bytes=new HashMap<String,byte[]>();for(var e:out.history.envelopes().values())bytes.put(e.payloadSha256(),e.readPayload());
        var envelope=out.history.envelopes().values().stream().filter(e->e.evidenceType().equals("athena:rule-qualification-receipt")).findFirst().orElseThrow();
        var r=ResearchDocuments.decode(envelope.readPayload(),RuleQualificationReceipt.class);
        ResearchArtifactReader reader=s->{var b=bytes.get(s.sha256());if(b==null)throw new java.io.IOException("missing");return b;};
        var registry=new RuleRegistry().register(out.manifest);
        assertNotNull(registry.requireCurrent(out.manifest.key(),RuleRegistry.digest(out.manifest),r,out.context,reader,AT));
        var forged=new RuleQualificationReceipt(r.schema(),r.ruleKey(),r.manifestSha256(),r.eligibility(),r.implementationReport(),r.foundationCertificate(),r.stateBinding(),r.request(),r.qualification(),QualificationMode.HISTORICAL_REPLAY,r.evaluatedAt(),r.reasons());
        assertThrows(java.io.IOException.class,()->registry.requireCurrent(out.manifest.key(),RuleRegistry.digest(out.manifest),forged,out.context,reader,AT));
    }
    @Test void legacyExecutionDoesNotAcceptV3()throws Exception {
        var f=ResearchGateAcceptanceTest.fixture("valid");var state=system(List.of(B01FunctionalGroupAcceptanceTest.fixture("aldehyde").graph()),true,false);var catalog=new EvidenceSnapshotCatalog(temp);
        var foundation=pipeline().run(catalog,Optional.empty(),state,List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"legacy-foundation"),AT);
        var result=new RuleExecutionPipeline(pipeline(),OCL).run(catalog,foundation,Map.of(),new RuleRegistry().register(f.manifest()),ResearchDocuments.encode(f.manifest()),B01FunctionalGroupAcceptanceTest.request(state,f.manifest()),Optional.empty(),ref(ScientificReference.Kind.ACTIVITY,"legacy-attempt"),AT);
        assertTrue(result.measurements().isEmpty());var h=catalog.read(result.published().catalogSnapshot()).orElseThrow().history();
        assertTrue(h.envelopes().values().stream().anyMatch(e->e.evidenceType().equals("athena:rule-manifest")));
        assertTrue(h.interpretations().values().stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.FAILED));
    }
    public static void main(String[] args)throws Exception {
        Files.createDirectories(Path.of(args[0]));var out=run(Path.of(args[0]),"valid");
        Files.write(Path.of(args[1]),SystemStateView.bytes(Map.of("snapshot",out.result.published().catalogSnapshot(),"certificate",out.result.published().certificate())));
    }
}
