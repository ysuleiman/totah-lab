package totah.lab.daedalus.system;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import static org.junit.jupiter.api.Assertions.*;
/** Candidate status is preserved; this characterizes input handoff, not authorization. */
class DirectAssessmentExecutionCharacterizationTest {
 @TempDir Path temp;
 static Map<String,Object> observe(Path root)throws Exception {
  var s=SourceSulfurConnectivityAcceptanceTest.state(false);var input=SourceSulfurConnectivityAcceptanceTest.proof(s,false,"SUPPORTED_PRESENT");var m=SourceSulfurConnectivityAcceptanceTest.model();var r=SourceSulfurConnectivityAcceptanceTest.req(s);
  var direct=SourceSulfurConnectivityAcceptanceTest.summary(SourceSulfurConnectivityAcceptanceTest.run(s,input));
  var catalog=new EvidenceSnapshotCatalog(root);var pipeline=RuleRegistryAcceptanceTest.pipeline();
  var foundation=pipeline.run(catalog,Optional.empty(),s,input,Map.of(),List.of(),RuleRegistryAcceptanceTest.ref(ScientificReference.Kind.ACTIVITY,"source-foundation"),RuleRegistryAcceptanceTest.T);
  var result=new RuleExecutionPipeline(pipeline).run(catalog,foundation,Map.of(),new RuleRegistry().register(m),SystemStateView.bytes(m),r,Optional.empty(),RuleRegistryAcceptanceTest.ref(ScientificReference.Kind.ACTIVITY,"source-runner"),RuleRegistryAcceptanceTest.T);
  var history=catalog.read(result.published().catalogSnapshot()).orElseThrow().history();
  assertTrue(history.envelopes().containsKey(input.getFirst().reference()));
  var recorded=history.interpretations().values().stream().filter(i->i.evaluator().namespace().equals("athena.ss-connectivity")).findFirst().orElseThrow();
  assertEquals("ABSENT_FALSE",direct.measurements().get("assessment"));assertEquals("UNKNOWN_INCONCLUSIVE",recorded.measurements().get("assessment"));
  assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,direct.status());assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,recorded.status());
  assertTrue(result.measurements().isEmpty());
  return Map.of("directExplicitInputs",direct,"legacyRunnerInterpretation",recorded,"coveragePreservedButNotImplicitlySelected",true);
 }
 @Test void existingLegacyRunnerDoesNotForwardInheritedCoverage()throws Exception{observe(temp);}
 public static void main(String[] args)throws Exception{Files.write(Path.of(args[0]),SystemStateView.bytes(observe(Path.of(args[1]))));}
}
