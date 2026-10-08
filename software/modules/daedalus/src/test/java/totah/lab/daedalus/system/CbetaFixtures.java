package totah.lab.daedalus.system;
import java.nio.file.*;
import java.util.*;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.JSON;
final class CbetaFixtures {
 static final String ID="ATHENA.V11.L_SER_THR_VAL_UNCORRECTED_CB_DEVIATION";
 static final Path RES=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/cbeta-deviation-v1");
 final ResidueValidationFixtures f;
 CbetaFixtures(String id,String variant,Map<String,double[]> xyz)throws Exception {f=new ResidueValidationFixtures(id,true,variant,xyz);f.manifest=RuleRegistry.decode(Files.readAllBytes(RES.resolve(ID+".rule.json")));}
 CbetaFixtures(String id)throws Exception {this(id,"valid",Map.of());}
 void bind()throws Exception {
  f.bindingEnvelope=f.add("athena:residue-context-binding",f.binding);for(var e:f.facts.entrySet())f.witness(e.getKey(),e.getValue());
  for(var spec:JSON.readTree(Files.readAllBytes(RES.resolve("SOURCES.json"))))f.add("athena:source-artifact",Files.readAllBytes(RES.resolve(spec.path("file").asText())),AthenaScientificRulesAcceptanceTest.ref(ScientificReference.Kind.METHOD,"pinned-v11-reference"));
  var atoms=List.of("2_N","2_CA","2_C","2_CB").stream().map(f.map::get).filter(Objects::nonNull).sorted().toList();f.request=new RuleRequest(f.state.binding(),f.manifest.key(),RuleRegistry.digest(f.manifest),atoms,List.of(ResidueValidationFixtures.residue(2)),List.of(),.1,0,10000,10000);
 }
 void qualify(Path temp,boolean source)throws Exception {f.qualify(temp,source);}
 SystemGraphAnalyzer.Finding evaluate()throws Exception {
  var inputs=new ArrayList<>(f.inputs);var collector=RuleAnalyzers.collector(f.manifest,f.request);var raw=collector.analyze(f.state,inputs,Map.of()).getFirst();
  if(raw.measurements().containsKey("payload"))inputs.add(SystemQualificationPipeline.envelope(AthenaScientificRulesAcceptanceTest.ref(ScientificReference.Kind.ACTIVITY,"cbeta-collected"),"measurement","athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),f.state.subject(),totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT,List.of("Synthetic V11 qualification only")));
  return RuleAnalyzers.evaluator(f.manifest,f.request).analyze(f.state,inputs,Map.of()).getFirst();
 }
}
