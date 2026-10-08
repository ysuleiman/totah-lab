package totah.lab.daedalus.system;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.JSON;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;
class CbetaAcceptanceTest {
 @TempDir Path temp;
 static Stream<String> cases()throws Exception {try(var in=CbetaAcceptanceTest.class.getResourceAsStream("/cbeta-deviation-v1/ACCEPTANCE_MATRIX.json")){var a=JSON.readTree(in).path("cases");assertEquals(71,a.size());return StreamSupport.stream(a.spliterator(),false).map(r->r.path("id").asText()+"|"+r.path("group").asText()+"|"+r.path("case").asText()).toList().stream();}}
 @ParameterizedTest(name="{0}") @MethodSource("cases")
 void matrix(String spec)throws Exception {
  String[] parts=spec.split("\\|");String group=parts[1],name=parts[2];
  if(group.equals("geometry")||group.equals("category")){numerical(group,name);return;}
  if(group.equals("preservation")){preservation(name);return;}
  String id=name.contains("THR")||name.contains("beta")?"THR":name.contains("VAL")?"VAL":Set.of("GLY","ALA","PRO").contains(name)?name:"SER";
  var wrapper=new CbetaFixtures(id,name.equals("deprotonated-SER")?"deprotonated":"valid",Map.of());var f=wrapper.f;
  var identity=(ObjectNode)f.facts.get("identityState").path("residues").get(1);var atom=(ObjectNode)identity.path("atoms").get(0);var stereo=(ObjectNode)f.facts.get("stereochemistry").path("residues").get(1);
  if(group.equals("source-positive")){}
  else if(group.equals("known-exclusions"))switch(name){
   case "D-SER"->stereo.put("alpha","D");case "allo-THR"->stereo.put("beta","OTHER");case "cyclic-peptide"->f.facts.get("connections").put("cyclicPeptide","TRUE");
   case "modified-THR"->identity.put("domainStatus","UNSUPPORTED");
   case "central-terminal"->{f.binding.putNull("previous");((ArrayNode)f.binding.path("peptideLinks")).remove(0);for(var fact:f.facts.values())if(fact.has("residues"))((ArrayNode)fact.path("residues")).remove(0);f.facts.get("connections").put("previousPresence","ABSENT");f.facts.get("connections").putNull("previousResidue");}
   case "known-nonordinary"->{f.binding.put("sourceScope","KNOWN_NONORDINARY");var row=(ObjectNode)f.facts.get("connections").path("residues").get(1);row.put("coverage","KNOWN_NONORDINARY");row.withArray("nonordinary").add(ResidueValidationFixtures.node(Map.of("first",f.source("2_OG"),"second",Map.of("artifact",S1NitrogenAcceptanceTest.pin(f.original),"selector","exact source dative endpoint"),"kind","DATIVE","sourceLocations",f.locations())));}
   case "DERIVED"->{f.binding.put("sourceKind","DERIVED");f.facts.get("preparation").put("sourceKind","DERIVED");f.binding.set("preparationReferences",ResidueValidationFixtures.node(List.of(S1NitrogenAcceptanceTest.pin(f.protocol))));f.facts.get("preparation").set("preparationReferences",f.binding.get("preparationReferences"));}
   default->{}
  }
  else if(group.equals("unknown"))switch(name){
   case "missing-charge"->atom.putNull("formalCharge");case "missing-H"->atom.putNull("nonExplicitHydrogenCount");case "missing-isotope-state"->atom.put("isotopeStatus","UNKNOWN");case "missing-aromaticity"->atom.putNull("aromatic");case "missing-electronic-state"->atom.put("electronicState","UNKNOWN");
   case "incomplete-connection-scope"->{f.binding.put("sourceScope","UNKNOWN");((ObjectNode)f.facts.get("connections").path("residues").get(1)).put("coverage","UNKNOWN");}
   case "missing-alpha-stereo"->stereo.put("alpha","UNKNOWN");case "missing-native-beta"->stereo.put("beta","UNKNOWN");case "missing-VAL-attribution"->stereo.putNull("valineMethylAttribution");
   case "missing-peptide-context"->f.facts.get("connections").put("nextPresence","UNKNOWN");
   case "missing-CA-coordinate","missing-CB-coordinate"->{String role=name.equals("missing-CA-coordinate")?"CA":"CB";for(var a:f.binding.path("central").path("atoms"))if(a.path("source").path("atomId").asText().equals("2_"+role))((ObjectNode)a).putNull("coordinate");}
   case "unresolved-alt-correlation"->f.facts.get("coherence").put("correlation","UNRESOLVED");
   case "conflicting-preparation"->f.facts.get("preparation").put("sourceKind","PREPARED_SOURCE");default->{}
  }
  else if(group.equals("provenance-serialization"))switch(name){
   case "wrong-frame"->f.binding.set("frameReference",ResidueValidationFixtures.node(S1NitrogenAcceptanceTest.pin(f.protocol)));
   case "wrong-model"->((ObjectNode)f.binding.path("modelIdentity")).put("rawValue","2");
   case "duplicate-source-atom"->{var a=(ArrayNode)f.binding.path("central").path("atoms");a.add(a.get(0));}
   case "unknown-enum"->f.binding.put("sourceScope","INVENTED");default->{}
  }
  wrapper.bind();
  if(name.equals("conflicting-state-witness")){var fact=f.facts.get("identityState").deepCopy();((ObjectNode)fact.path("residues").get(1)).put("sourceStateDescription","conflicting exact state");f.witness("identityState",fact);}
  if(group.equals("authority")){
   if(!name.equals("binding-only")&&!name.equals("missing-rule-review"))wrapper.qualify(temp,!name.equals("missing-source-review"));
   if(name.equals("missing-rule-review")){wrapper.qualify(temp,true);f.inputs.removeIf(e->e.evidenceType().equals("athena:rule-qualification-receipt")&&e.reference().id().startsWith("rv-main/"));}
   if(name.startsWith("expired"))expire(f,name.equals("expired-rule")?"rv-main/":"rv-source/");
   if(name.equals("source-review-covers-other-state")){var fact=f.facts.get("identityState").deepCopy();((ObjectNode)fact.path("residues").get(1)).put("sourceStateDescription","new state not covered by existing review");f.witness("identityState",fact);}
   if(name.equals("historical-only-receipt"))f.inputs.removeIf(e->e.evidenceType().equals("athena:event-source"));
   assertEquals(NOT_EVALUATED,wrapper.evaluate().status());return;
  }
  wrapper.qualify(temp,true);
  if(group.equals("provenance-serialization")){
   if(name.equals("changed-original-bytes")||name.equals("missing-pin")){f.inputs.remove(f.original);if(name.equals("changed-original-bytes"))f.add("athena:source-artifact",Map.of("changed",true));assertThrows(Exception.class,wrapper::evaluate);return;}
   if(Set.of("wrong-frame","wrong-model").contains(name)){assertEquals(UNKNOWN_INCONCLUSIVE,wrapper.evaluate().status());return;}
   if(Set.of("duplicate-source-atom","unknown-enum").contains(name)){assertThrows(Exception.class,wrapper::evaluate);return;}
   tamper(wrapper,name);return;
  }
  var result=wrapper.evaluate();var expected=group.equals("source-positive")?SUPPORTED_PRESENT:group.equals("known-exclusions")?UNSUPPORTED:UNKNOWN_INCONCLUSIVE;assertEquals(expected,result.status(),spec+result);
  if(result.measurements().containsKey("payload")){var p=JSON.readTree(result.measurements().get("payload"));assertEquals(expected==SUPPORTED_PRESENT,!p.path("category").isNull());if(expected!=SUPPORTED_PRESENT)assertTrue(p.path("deviation").path("value").isNull());}
 }
 static void expire(ResidueValidationFixtures f,String prefix)throws Exception {
  var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:rule-qualification-receipt")&&x.reference().id().startsWith(prefix)).findFirst().orElseThrow();var q=ResearchDocuments.decode(e.readPayload(),RuleQualificationReceipt.class);
  var expired=new RuleQualificationReceipt(q.schema(),q.ruleKey(),q.manifestSha256(),q.eligibility(),q.implementationReport(),q.foundationCertificate(),q.stateBinding(),q.request(),q.qualification(),q.mode(),q.evaluatedAt().plusSeconds(3600),q.reasons());f.inputs.remove(e);f.inputs.add(S1ResearchFixtures.envelope(f.state,"cb-expired",prefix,"athena:rule-qualification-receipt",ResearchDocuments.encode(expired)));
 }
 void tamper(CbetaFixtures w,String name)throws Exception {
  var f=w.f;var collect=RuleAnalyzers.collector(f.manifest,f.request);var raw=collect.analyze(f.state,f.inputs,Map.of()).getFirst();var p=(ObjectNode)JSON.readTree(raw.measurements().get("payload"));
  switch(name){
   case "wrong-definition-digest"->p.put("definitionSha256","0".repeat(64));case "swapped-tuple"->{var a=(ArrayNode)p.path("atomTuple");var x=a.get(0);a.set(0,a.get(1));a.set(1,x);}
   case "invented-caller-category"->p.put("category","NON_OUTLIER");case "coordinate-bits-mismatch"->{var point=JSON.createObjectNode();point.set("value",ResidueValidationFixtures.node(List.of(1.,0.,0.)));point.set("binary64Hex",ResidueValidationFixtures.node(List.of("0".repeat(16),"0".repeat(16),"0".repeat(16))));point.put("unit","ANGSTROM");((ObjectNode)p.path("reconstruction")).set("normalizedReference",point);}
   case "signed-zero-mismatch"->{((ObjectNode)p.path("deviation")).put("value",0.);((ObjectNode)p.path("deviation")).put("binary64Hex","8000000000000000");}
   case "extra-field"->p.put("selfQualified",true);case "missing-required-field"->p.remove("stateBinding");default->fail("Unmapped case "+name);
  }
  f.add("athena:rule-measurements",SystemStateView.bytes(p),collect.method());assertThrows(Exception.class,()->RuleAnalyzers.evaluator(f.manifest,f.request).analyze(f.state,f.inputs,Map.of()));
 }
 void numerical(String group,String name)throws Exception {
  if(name.startsWith("guard-")){double x=name.endsWith("nextDown")?Math.nextDown(.000001):name.endsWith("nextUp")?Math.nextUp(.000001):.000001;assertEquals(name.endsWith("nextUp"),CbetaTestAccess.normalizable(x));return;}
  if(name.equals("zero-mean")){assertEquals("ZERO_MEAN_DISTANCE",CbetaTestAccess.zeroMean());return;}
  if(name.equals("nonfinite")){assertEquals("NONFINITE",CbetaTestAccess.calculate("SER",new double[][]{{Double.NaN,0,0},{1,0,0},{1,1,0},{1,0,1}}).path("degeneracy").asText());return;}
  if(Set.of("nextDown(0.25)","0.25","nextUp(0.25)").contains(name)){double x=name.startsWith("nextDown")?Math.nextDown(.25):name.startsWith("nextUp")?Math.nextUp(.25):.25;assertEquals(name.startsWith("nextDown")?"NON_OUTLIER":"OUTLIER",CbetaTestAccess.category(x));return;}
  var coords=new HashMap<String,double[]>();var ideal=CbetaTestAccess.ideal("SER");
  if(name.equals("ordinary-reference-CB"))coords.put("2_CB",ideal);
  if(name.equals("reference-plus-large-offset"))coords.put("2_CB",new double[]{ideal[0]+1,ideal[1],ideal[2]});
  if(name.equals("collinear"))coords.put("2_C",new double[]{2,0,0});
  if(name.equals("coincident-N-CA"))coords.put("2_N",new double[]{1,0,0});
  if(name.equals("constructed-offset-rounding"))coords.put("2_CB",new double[]{ideal[0]+Math.nextDown(.25),ideal[1],ideal[2]});
  var w=new CbetaFixtures("SER","valid",coords);w.bind();w.qualify(temp,true);var r=w.evaluate();
  if(Set.of("collinear","coincident-N-CA").contains(name)){assertEquals(UNKNOWN_INCONCLUSIVE,r.status());return;}
  assertEquals(SUPPORTED_PRESENT,r.status(),r.toString());var p=JSON.readTree(r.measurements().get("payload"));double d=p.path("deviation").path("value").doubleValue();assertEquals(CbetaTestAccess.category(d),p.path("category").asText());if(name.equals("ordinary-reference-CB"))assertEquals(0,d);else assertEquals("OUTLIER",p.path("category").asText());assertNotEquals(ABSENT_FALSE,r.status());
 }
 void preservation(String name)throws Exception {
  if(name.equals("two-independent-JVMs")){var reports=new TreeMap<String,Object>();for(var id:List.of("SER","THR","VAL")){var w=new CbetaFixtures(id);w.bind();w.qualify(temp.resolve(id),true);var r=w.evaluate();assertEquals(SUPPORTED_PRESENT,r.status());reports.put(id,JSON.readTree(r.measurements().get("payload")));}byte[] bytes=SystemStateView.bytes(reports);System.out.println("V11_REPLAY_SHA256="+EvidenceExchange.sha256(bytes));String out=System.getProperty("athena.cbetaReplayOutput");if(out!=null)Files.write(Path.of(out),bytes);return;}
  if(name.equals("V09-V10-regression")||name.equals("old-payload-replay")){var f=new ResidueValidationFixtures("SER",name.equals("V09-V10-regression"),"valid");f.bind();f.qualify(temp,true);var r=f.evaluate();assertEquals(SUPPORTED_PRESENT,r.status());var old=JSON.readTree(Files.readAllBytes(Path.of("software/qualification/v09-v10-implementation-20261008/independent-replay-2/replay-1.json"))).path(name.equals("V09-V10-regression")?"rama-SER":"rotamer-SER");assertEquals(old,JSON.readTree(r.measurements().get("payload")));return;}
  if(name.equals("V10-shared-domain-extraction")){for(var id:List.of("SER","THR","VAL")){var f=new ResidueValidationFixtures(id,false,"valid");f.bind();assertEquals(SUPPORTED_PRESENT,CbetaTestAccess.domain(f.state,f.bindingEnvelope,f.inputs));}return;}
  var w=new CbetaFixtures("SER");w.bind();w.qualify(temp,true);var before=SystemStateView.bytes(w.f.state.snapshot());var result=w.evaluate();assertEquals(SUPPORTED_PRESENT,result.status());
  if(name.equals("reordered-evidence")){Collections.reverse(w.f.inputs);assertEquals(result,w.evaluate());}
  else if(name.equals("source-graph-before-after"))assertArrayEquals(before,SystemStateView.bytes(w.f.state.snapshot()));
  else if(name.equals("operational-budget-truncation")){var r=w.f.request;w.f.request=new RuleRequest(r.state(),r.manifestKey(),r.manifestSha256(),r.atoms(),r.first(),r.second(),r.radiusAngstrom(),r.maximumHops(),3,1);assertEquals(NOT_EVALUATED,w.evaluate().status());}
  else fail("Unmapped case "+name);
 }
}
