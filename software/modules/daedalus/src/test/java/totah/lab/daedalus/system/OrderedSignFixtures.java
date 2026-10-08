package totah.lab.daedalus.system;
import java.nio.file.*;
import java.util.*;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.JSON;
final class OrderedSignFixtures {
 static final String ID="ATHENA.V08.STV_ORDERED_CHIRAL_SIGN_JAVA21";
 static final Path RES=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/ordered-chiral-sign-v1");
 final ResidueValidationFixtures f;
 OrderedSignFixtures(String id,String variant,Map<String,double[]> xyz)throws Exception {f=new ResidueValidationFixtures(id,true,variant,xyz);f.manifest=RuleRegistry.decode(Files.readAllBytes(RES.resolve(ID+".rule.json")));}
 OrderedSignFixtures(String id)throws Exception {this(id,"valid",Map.of());}
 void bind()throws Exception {
  f.bindingEnvelope=f.add("athena:residue-context-binding",f.binding);for(var e:f.facts.entrySet())f.witness(e.getKey(),e.getValue());
  for(var spec:JSON.readTree(Files.readAllBytes(RES.resolve("SOURCES.json"))))f.add("athena:source-artifact",Files.readAllBytes(RES.resolve(spec.path("file").asText())),AthenaScientificRulesAcceptanceTest.ref(ScientificReference.Kind.METHOD,"pinned-v11-reference"));
  var atoms=(f.identity.equals("THR")?List.of("2_N","2_CA","2_C","2_CB","2_OG1","2_CG2"):Set.of("SER","VAL").contains(f.identity)?List.of("2_N","2_CA","2_C","2_CB"):List.<String>of()).stream().map(f.map::get).filter(Objects::nonNull).sorted().toList();f.request=new RuleRequest(f.state.binding(),f.manifest.key(),RuleRegistry.digest(f.manifest),atoms,List.of(ResidueValidationFixtures.residue(2)),List.of(),.1,0,10000,10000);
 }
 void qualify(Path temp,boolean source)throws Exception {f.qualify(temp,source);}
 SystemGraphAnalyzer.Finding evaluate()throws Exception {
  var inputs=new ArrayList<>(f.inputs);var collector=RuleAnalyzers.collector(f.manifest,f.request);var raw=collector.analyze(f.state,inputs,Map.of()).getFirst();
  if(raw.measurements().containsKey("payload"))inputs.add(SystemQualificationPipeline.envelope(AthenaScientificRulesAcceptanceTest.ref(ScientificReference.Kind.ACTIVITY,"sign-collected"),"measurement","athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),f.state.subject(),totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT,List.of("Synthetic V08 qualification only")));
  return RuleAnalyzers.evaluator(f.manifest,f.request).analyze(f.state,inputs,Map.of()).getFirst();
 }
}
