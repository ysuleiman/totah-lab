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
class RestraintCurrentPipelineTest {
 @TempDir Path temp;
 @ParameterizedTest @ValueSource(strings={"positive","missing-clock","inherited"})
 void governedCurrentSelection(String variant)throws Exception {
  var wrapper=new RestraintFixtures("SER");wrapper.prepare();wrapper.bind();wrapper.qualify(temp.resolve("authority"),true,true);var f=wrapper.f;var q=f.main;var inputs=new ArrayList<>(f.inputs);inputs.removeIf(e->e.evidenceType().equals("athena:rule-qualification-receipt")&&e.reference().id().startsWith("rv-main/"));
  var context=q.artifacts().stream().filter(e->e.evidenceType().equals("athena:rule-policy-context")).findFirst().orElseThrow();
  var implementation=q.artifacts().stream().filter(e->{try{return B01FunctionalGroupAcceptanceTest.JSON.readTree(e.readPayload()).path("schema").asText().equals("athena-rule-implementation-qualification/2");}catch(Exception ignored){return false;}}).findFirst().orElseThrow();
  var catalog=new EvidenceSnapshotCatalog(Files.createDirectory(temp.resolve("catalog")));var pipeline=pipeline();
  var foundation=pipeline.run(catalog,Optional.empty(),f.state,variant.equals("inherited")?f.inputs:List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"rv-foundation"),AT);
  var executor=new RuleExecutionPipeline(pipeline,OCL,variant.equals("missing-clock")?null:(ctx,at)->{if(!at.equals(AT)||!ctx.issuer().equals(ResearchV2Fixtures.EXECUTOR))throw new java.io.IOException("Synthetic time mismatch");});
  var result=executor.runCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(f.manifest),ResearchDocuments.encode(f.manifest),q.request(),Optional.empty(),new RuleExecutionPipeline.ResearchExecutionInputs(variant.equals("inherited")?q.artifacts():inputs,ResearchGatePipelineAcceptanceTest.pin(context.readPayload()),ResearchGatePipelineAcceptanceTest.pin(implementation.readPayload())),ref(ScientificReference.Kind.ACTIVITY,"rv-current"),AT);
  var history=catalog.read(result.published().catalogSnapshot()).orElseThrow().history();var eval=history.interpretations().values().stream().filter(i->i.evaluator().namespace().equals("athena.restraint-deviation")&&i.evaluator().id().endsWith("/evaluate")).toList();
  if(variant.equals("positive"))assertTrue(eval.stream().anyMatch(e->e.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT),history.interpretations().values().stream().filter(e->e.status()==EvidenceInterpretation.Status.FAILED).toList().toString()+eval);
  else assertTrue(eval.stream().noneMatch(e->e.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT),"No implicit history or missing current clock activation");
 }
}
