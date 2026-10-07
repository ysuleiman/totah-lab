package totah.lab.daedalus.system;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;
class SourceFragmentParentCurrentPipelineTest {
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(strings={"valid","missing-clock","inherited"})
    void independentCurrentExecution(String variant)throws Exception {
        var tests=new SourceFragmentParentAcceptanceTest();tests.temp=temp;var sample=tests.sample("valid");var f=sample.source();var q=sample.qualification();var inputs=new ArrayList<>(sample.inputs());inputs.addAll(q.artifacts());inputs.removeIf(e->e.evidenceType().equals("athena:rule-qualification-receipt")&&e.reference().id().startsWith("a08/"));
        var context=q.artifacts().stream().filter(e->e.evidenceType().equals("athena:rule-policy-context")).findFirst().orElseThrow();var implementation=q.artifacts().stream().filter(e->{try{return B01FunctionalGroupAcceptanceTest.JSON.readTree(e.readPayload()).path("schema").asText().equals("athena-rule-implementation-qualification/2");}catch(Exception ex){return false;}}).findFirst().orElseThrow();
        var catalog=new EvidenceSnapshotCatalog(Files.createDirectory(temp.resolve("current")));var pipeline=pipeline();var foundation=pipeline.run(catalog,Optional.empty(),f.state,variant.equals("inherited")?sample.inputs():List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"a08-foundation"),AT);
        var executor=new RuleExecutionPipeline(pipeline,OCL,variant.equals("missing-clock")?null:(ctx,at)->{if(!at.equals(AT)||!ctx.issuer().equals(ResearchV2Fixtures.EXECUTOR))throw new java.io.IOException("synthetic current time mismatch");});
        var result=executor.runCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(f.manifest),ResearchDocuments.encode(f.manifest),q.request(),Optional.empty(),new RuleExecutionPipeline.ResearchExecutionInputs(variant.equals("inherited")?q.artifacts():inputs,ResearchGatePipelineAcceptanceTest.pin(context.readPayload()),ResearchGatePipelineAcceptanceTest.pin(implementation.readPayload())),ref(ScientificReference.Kind.ACTIVITY,"a08-current"),AT);
        var history=catalog.read(result.published().catalogSnapshot()).orElseThrow().history();var evaluations=history.interpretations().values().stream().filter(i->i.evaluator().namespace().equals("athena.source-fragment-parent")&&i.evaluator().id().endsWith("/evaluate")).toList();
        if(variant.equals("valid")){assertEquals(1,evaluations.size(),history.interpretations().values().stream().filter(i->i.status()==EvidenceInterpretation.Status.FAILED).toList().toString());assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,evaluations.getFirst().status(),evaluations.toString());}else if(variant.equals("inherited")){assertEquals(1,evaluations.size());assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,evaluations.getFirst().status());assertFalse(evaluations.getFirst().measurements().containsKey("selectedGraph"));}else assertTrue(evaluations.isEmpty(),evaluations.toString());
    }
}
