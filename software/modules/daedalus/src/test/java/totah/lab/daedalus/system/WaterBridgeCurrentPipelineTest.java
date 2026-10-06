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
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;

/** Existing synthetic Research Gate authority only; no real water-rule receipt is issued. */
class WaterBridgeCurrentPipelineTest {
    @TempDir Path temp;
    private EvidenceAdmission.Pin lastSnapshot;
    @ParameterizedTest @ValueSource(strings={"valid","multi","expired","inherited","missing-time"})
    void governedWaterExecution(String variant)throws Exception {
        var w=new WaterBridgeFixtures(variant.equals("multi")?2:1,variant.equals("multi"));
        // Mixed source attribution is preserved; modeled H is never relabelled as observed.
        w.artifacts.replaceAll(e->new EvidenceEnvelope(e.reference(),e.evidenceType(),e.payloadFormat(),e.payloadVersion(),e.payloadBase64(),e.artifactPath(),e.payloadSha256(),e.provenance(),e.method(),e.context(),e.subjects(),e.qualifications(),List.of("synthetic fixture: O coordinates stand for experimental source; H coordinates stand for supplied preparation model, not observation"),e.recordedAt()));
        var research=HbondCandidateResearchFixture.create(w.manifest,variant.equals("expired")?"expired":"valid");
        w.manifest=research.manifest();w.coverage(EvidenceInterpretation.Status.SUPPORTED_PRESENT);
        var m=w.manifest;var state=w.state;var pipeline=pipeline();var catalog=new EvidenceSnapshotCatalog(temp);
        var foundation=pipeline.run(catalog,Optional.empty(),state,variant.equals("inherited")?w.inputs():List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"water-current-foundation"),AT);
        var inputs=new ArrayList<EvidenceEnvelope>();if(!variant.equals("inherited"))inputs.addAll(w.inputs());int i=0;
        for(var bytes:new TreeMap<>(research.bytes()).values())inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"water-research"),"artifact"+i++,"fixture:research-source",bytes,ResearchGateAcceptanceTest.REVIEWER,state.subject(),AT,List.of("synthetic administration only; reused gate fixture")));
        var context=ResearchDocuments.encode(research.context());var contextPin=ResearchGatePipelineAcceptanceTest.pin(context);
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"water-context"),"context","athena:rule-policy-context",context,ResearchGateAcceptanceTest.REVIEWER,state.subject(),AT,List.of()));
        var implementation=new RuleImplementationQualificationV2("athena-rule-implementation-qualification/2",m.key(),m.research().definitionSha256(),m.research().domain().sha256(),RuleRegistry.digest(m),List.of(research.raw()),List.of(research.raw()),List.of(new RuleImplementationQualification.Check("synthetic-positive",true,research.raw(),"engineering only")),ResearchV2Fixtures.EXECUTOR,research.raw(),AT);
        var ib=ResearchDocuments.encode(implementation);var ip=ResearchGatePipelineAcceptanceTest.pin(ib);
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"water-implementation"),"source","athena:rule-implementation-qualification",ib,ResearchGateAcceptanceTest.REVIEWER,state.subject(),AT,List.of()));
        var executor=new RuleExecutionPipeline(pipeline,OCL,variant.equals("missing-time")?null:(ctx,at)->{if(!at.equals(AT)||!ctx.issuer().equals(ResearchV2Fixtures.EXECUTOR))throw new java.io.IOException("fixture time mismatch");});
        var result=executor.runCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(m),ResearchDocuments.encode(m),w.request(),Optional.empty(),new RuleExecutionPipeline.ResearchExecutionInputs(inputs,contextPin,ip),ref(ScientificReference.Kind.ACTIVITY,"water-current"),AT);
        lastSnapshot=result.published().catalogSnapshot();var history=catalog.read(lastSnapshot).orElseThrow().history();
        for(var e:inputs)assertEquals(e,history.envelopes().get(e.reference()),"raw supplied evidence survives every gate result");
        var evaluations=history.interpretations().values().stream().filter(x->x.evaluator().namespace().equals("athena.water-bridge")&&x.evaluator().id().endsWith("/evaluate")).toList();
        if(variant.equals("valid")||variant.equals("multi")){assertEquals(1,evaluations.size(),history.interpretations().values().stream().filter(x->x.status()==EvidenceInterpretation.Status.FAILED).toList().toString());assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,evaluations.getFirst().status());}
        else {assertTrue(evaluations.isEmpty());if(!variant.equals("inherited"))assertTrue(result.measurements().isPresent(),"measurement evidence survives failed current qualification");}
    }
    public static void main(String[] args)throws Exception {var t=new WaterBridgeCurrentPipelineTest();t.temp=Path.of(args[0]);Files.createDirectories(t.temp);t.governedWaterExecution("valid");Files.write(Path.of(args[1]),SystemStateView.bytes(t.lastSnapshot));}
}
