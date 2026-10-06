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

class HbondCandidateCurrentPipelineTest {
    @TempDir Path temp;
    private EvidenceAdmission.Pin lastSnapshot;
    @ParameterizedTest @ValueSource(strings={"valid","expired","inherited","missing-time"})
    void governedExecutionAndExplicitSelection(String variant) throws Exception {
        var s=HbondCandidateFixtures.sample("DONOR.AMINE_PRIMARY","ETHER_O",3,180,true,"complete");
        var f=HbondCandidateResearchFixture.create(s.manifest(),variant.equals("expired")?"expired":"valid");
        var m=f.manifest();var state=s.state();var pipeline=pipeline();var catalog=new EvidenceSnapshotCatalog(temp);
        var foundation=pipeline.run(catalog,Optional.empty(),state,variant.equals("inherited")?s.inputs():List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"i02-current-foundation"),AT);
        var inputs=new ArrayList<EvidenceEnvelope>();
        if(!variant.equals("inherited"))inputs.addAll(s.inputs());
        int i=0;
        for(var bytes:new TreeMap<>(f.bytes()).values())inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i02-research"),"artifact"+i++,"fixture:research-source",bytes,ResearchGateAcceptanceTest.REVIEWER,state.subject(),AT,List.of("synthetic administration only")));
        var context=ResearchDocuments.encode(f.context());var contextPin=ResearchGatePipelineAcceptanceTest.pin(context);
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i02-context"),"context","athena:rule-policy-context",context,ResearchGateAcceptanceTest.REVIEWER,state.subject(),AT,List.of()));
        var implementation=new RuleImplementationQualificationV2("athena-rule-implementation-qualification/2",m.key(),m.research().definitionSha256(),m.research().domain().sha256(),RuleRegistry.digest(m),List.of(f.raw()),List.of(f.raw()),List.of(new RuleImplementationQualification.Check("synthetic-positive",true,f.raw(),"engineering fixture")),ResearchV2Fixtures.EXECUTOR,f.raw(),AT);
        var ib=ResearchDocuments.encode(implementation);var ip=ResearchGatePipelineAcceptanceTest.pin(ib);
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i02-implementation"),"source","athena:rule-implementation-qualification",ib,ResearchGateAcceptanceTest.REVIEWER,state.subject(),AT,List.of()));
        var executor=new RuleExecutionPipeline(pipeline,OCL,variant.equals("missing-time")?null:(contextValue,at)->{
            if(!at.equals(AT)||!contextValue.issuer().equals(ResearchV2Fixtures.EXECUTOR))throw new java.io.IOException("fixture time mismatch");
        });
        var result=executor.runCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(m),ResearchDocuments.encode(m),request(state,m,10000,false),Optional.empty(),new RuleExecutionPipeline.ResearchExecutionInputs(inputs,contextPin,ip),ref(ScientificReference.Kind.ACTIVITY,"i02-current"),AT);
        lastSnapshot=result.published().catalogSnapshot();
        var history=catalog.read(result.published().catalogSnapshot()).orElseThrow().history();
        for(var e:inputs)assertEquals(e,history.envelopes().get(e.reference()));
        var evaluations=history.interpretations().values().stream().filter(x->x.evaluator().namespace().equals("athena.hbond-candidate")&&x.evaluator().id().endsWith("/evaluate")).toList();
        if(variant.equals("valid")||variant.equals("inherited")) {
            assertEquals(1,evaluations.size(),history.interpretations().toString());
            assertEquals(variant.equals("valid")?EvidenceInterpretation.Status.SUPPORTED_PRESENT:EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,evaluations.getFirst().status(),evaluations.getFirst().toString());
        } else {assertTrue(evaluations.isEmpty());assertTrue(result.measurements().isPresent(),"measurements survive failed qualification");}
    }
    public static void main(String[] args) throws Exception {
        var test=new HbondCandidateCurrentPipelineTest();test.temp=Path.of(args[0]);Files.createDirectories(test.temp);
        test.governedExecutionAndExplicitSelection("valid");
        Files.write(Path.of(args[1]),SystemStateView.bytes(test.lastSnapshot));
    }
}
