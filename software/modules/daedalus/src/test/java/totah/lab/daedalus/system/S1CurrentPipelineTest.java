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

class S1CurrentPipelineTest {
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(strings={"valid","missing-clock","missing-scope-authority","inherited"})
    void directInvocationRequiresCurrentAndIndependentSourceAuthority(String variant)throws Exception {
        var f=new S1NitrogenAcceptanceTest.Fixture("primary");
        var q=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"s1");f.manifest=q.manifest();f.inputs.addAll(q.artifacts());
        if(!variant.equals("missing-scope-authority"))S1QualifiedProducerTest.qualifyScope(f,temp);
        f.inputs.removeIf(e->e.evidenceType().equals("athena:rule-qualification-receipt")&&e.reference().id().startsWith("s1/"));
        var context=q.artifacts().stream().filter(e->e.evidenceType().equals("athena:rule-policy-context")).findFirst().orElseThrow();
        var implementation=q.artifacts().stream().filter(e->{try{return B01FunctionalGroupAcceptanceTest.JSON.readTree(e.readPayload()).path("schema").asText().equals("athena-rule-implementation-qualification/2");}catch(Exception ignored){return false;}}).findFirst().orElseThrow();
        var catalog=new EvidenceSnapshotCatalog(Files.createDirectory(temp.resolve("pipeline")));var pipeline=pipeline();
        var foundation=pipeline.run(catalog,Optional.empty(),f.state,variant.equals("inherited")?f.inputs:List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"s1-foundation"),AT);
        var supplied=variant.equals("inherited")?q.artifacts():f.inputs;
        var executor=new RuleExecutionPipeline(pipeline,OCL,variant.equals("missing-clock")?null:(ctx,at)->{if(!at.equals(AT)||!ctx.issuer().equals(ResearchV2Fixtures.EXECUTOR))throw new java.io.IOException("synthetic time mismatch");});
        var result=executor.evaluateCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(f.manifest),ResearchDocuments.encode(f.manifest),f.request(),new RuleExecutionPipeline.ResearchExecutionInputs(supplied,ResearchGatePipelineAcceptanceTest.pin(context.readPayload()),ResearchGatePipelineAcceptanceTest.pin(implementation.readPayload())),ref(ScientificReference.Kind.ACTIVITY,"s1-current"),AT);
        var h=catalog.read(result.catalogSnapshot()).orElseThrow().history();
        var assignments=h.interpretations().values().stream().filter(i->i.evaluator().namespace().equals("athena.i03-n-sp3-s1")).toList();
        if(variant.equals("missing-clock"))assertTrue(assignments.isEmpty());
        else {assertEquals(1,assignments.size());assertEquals(variant.equals("valid")?EvidenceInterpretation.Status.SUPPORTED_PRESENT:EvidenceInterpretation.Status.NOT_EVALUATED,assignments.getFirst().status(),assignments.toString());}
    }
}
