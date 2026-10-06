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
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.JSON;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;

/** Existing synthetic Research Gate fixture cannot authorize a missing SP3 producer. */
class ImplicitHProxyCurrentPipelineTest {
    @TempDir Path temp;
    private EvidenceAdmission.Pin lastSnapshot;
    @ParameterizedTest @ValueSource(strings={"valid","expired","inherited","missing-time"})
    void governedProxyExecution(String variant)throws Exception {
        var w=ImplicitHProxyAcceptanceTest.fixture();
        w.assignment("SP3",false); // Unqualified caller assertion must survive the gate without becoming authority.
        var research=HbondCandidateResearchFixture.create(w.manifest,variant.equals("expired")?"expired":"valid");
        w.manifest=research.manifest();w.coverage(EvidenceInterpretation.Status.SUPPORTED_PRESENT);
        var m=w.manifest;var state=w.state;var pipeline=pipeline();var catalog=new EvidenceSnapshotCatalog(temp);
        var foundation=pipeline.run(catalog,Optional.empty(),state,variant.equals("inherited")?w.inputs():List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"i03-current-foundation"),AT);
        var inputs=new ArrayList<EvidenceEnvelope>();if(!variant.equals("inherited"))inputs.addAll(w.inputs());int i=0;
        for(var bytes:new TreeMap<>(research.bytes()).values())inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i03-research"),"artifact"+i++,"fixture:research-source",bytes,ResearchGateAcceptanceTest.REVIEWER,state.subject(),AT,List.of("synthetic administration only; reused gate fixture")));
        var context=ResearchDocuments.encode(research.context());var contextPin=ResearchGatePipelineAcceptanceTest.pin(context);
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i03-context"),"context","athena:rule-policy-context",context,ResearchGateAcceptanceTest.REVIEWER,state.subject(),AT,List.of()));
        var implementation=new RuleImplementationQualificationV2("athena-rule-implementation-qualification/2",m.key(),m.research().definitionSha256(),m.research().domain().sha256(),RuleRegistry.digest(m),List.of(research.raw()),List.of(research.raw()),List.of(new RuleImplementationQualification.Check("synthetic-positive",true,research.raw(),"engineering only")),ResearchV2Fixtures.EXECUTOR,research.raw(),AT);
        var ib=ResearchDocuments.encode(implementation);var ip=ResearchGatePipelineAcceptanceTest.pin(ib);
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i03-implementation"),"source","athena:rule-implementation-qualification",ib,ResearchGateAcceptanceTest.REVIEWER,state.subject(),AT,List.of()));
        var executor=new RuleExecutionPipeline(pipeline,OCL,variant.equals("missing-time")?null:(ctx,at)->{if(!at.equals(AT)||!ctx.issuer().equals(ResearchV2Fixtures.EXECUTOR))throw new java.io.IOException("fixture time mismatch");});
        var result=executor.runCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(m),ResearchDocuments.encode(m),w.request(),Optional.empty(),new RuleExecutionPipeline.ResearchExecutionInputs(inputs,contextPin,ip),ref(ScientificReference.Kind.ACTIVITY,"i03-current"),AT);
        lastSnapshot=result.published().catalogSnapshot();var history=catalog.read(lastSnapshot).orElseThrow().history();
        for(var e:inputs)assertEquals(e,history.envelopes().get(e.reference()),"raw supplied evidence survives every gate result");
        var evaluations=history.interpretations().values().stream().filter(x->x.evaluator().namespace().equals("athena.implicit-h-proxy")&&x.evaluator().id().endsWith("/evaluate")).toList();
        if(variant.equals("valid")){assertEquals(1,evaluations.size(),history.interpretations().values().stream().filter(x->x.status()==EvidenceInterpretation.Status.FAILED).toList().toString());assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluations.getFirst().status());assertFalse(JSON.readTree(evaluations.getFirst().measurements().get("payload")).get("complete").asBoolean());}
        else {assertTrue(evaluations.isEmpty());if(!variant.equals("inherited"))assertTrue(result.measurements().isPresent(),"measurement evidence survives failed current qualification");}
    }
    public static void main(String[] args)throws Exception {var t=new ImplicitHProxyCurrentPipelineTest();t.temp=Path.of(args[0]);Files.createDirectories(t.temp);t.governedProxyExecution("valid");Files.write(Path.of(args[1]),SystemStateView.bytes(t.lastSnapshot));}
}
