package totah.lab.daedalus.system;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.JSON;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;
class ResidueValidationAcceptanceTest {
 @TempDir Path temp;
 @ParameterizedTest @ValueSource(strings={"SER","THR","VAL"})
 void eligibleSelectedRotamer(String residue)throws Exception {
  var f=new ResidueValidationFixtures(residue,false,"valid");f.bind();f.qualify(temp,true);var result=f.evaluate();
  assertEquals(SUPPORTED_PRESENT,result.status(),result.toString());var p=JSON.readTree(result.measurements().get("payload"));assertFalse(p.path("category").isNull());assertEquals("SUPPORTED_PRESENT",p.path("sourceAdmissionStatus").asText());
 }
 @ParameterizedTest @ValueSource(strings={"GLY","SER","VAL","PRO"})
 void eligibleSelectedRama(String residue)throws Exception {
  var f=new ResidueValidationFixtures(residue,true,"valid");f.bind();f.qualify(temp,true);var result=f.evaluate();assertEquals(SUPPORTED_PRESENT,result.status(),result.toString());
 }
 @Test void noSourceReviewNoScore()throws Exception {
  var f=new ResidueValidationFixtures("SER",false,"valid");f.bind();f.qualify(temp,false);var result=f.evaluate();assertEquals(NOT_EVALUATED,result.status());assertTrue(JSON.readTree(result.measurements().get("payload")).path("referenceScore").path("value").isNull());
 }
 @Test void noCurrentRuleNoScore()throws Exception {
  var f=new ResidueValidationFixtures("SER",false,"valid");f.bind();var result=f.evaluate();assertEquals(NOT_EVALUATED,result.status());
 }

 @ParameterizedTest @ValueSource(strings={"degenerate","tiny","deprotonated","missing-grid","missing-geometry","expired-main","missing-witness","expired-source","reordered","outlier"})
 void eligibilityAndReplayBoundaries(String variant)throws Exception {
  var f=new ResidueValidationFixtures("SER",false,java.util.Set.of("degenerate","tiny","deprotonated","outlier").contains(variant)?variant:"valid");
  if(variant.equals("missing-witness"))f.facts.remove("stereochemistry");
  f.bind();f.qualify(temp,true);
  if(variant.equals("missing-grid"))f.inputs.removeIf(e->{try{return e.evidenceType().equals("athena:source-artifact")&&e.payloadSha256().equals(totah.lab.mnemosyne.EvidenceExchange.sha256(java.nio.file.Files.readAllBytes(ResidueValidationFixtures.RESOURCE.resolve("reference/reference_data--Top8000__Top8000_rotamer_pct_contour_grids__rota8000-ser.data"))));}catch(Exception ex){throw new RuntimeException(ex);}});
  if(variant.equals("missing-geometry"))f.inputs.removeIf(e->e.evidenceType().equals("athena:continuous-geometry-plan"));
  if(variant.equals("expired-main")||variant.equals("expired-source")){
   var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:rule-qualification-receipt")&&x.reference().id().startsWith(variant.equals("expired-main")?"rv-main/":"rv-source/")).findFirst().orElseThrow();
   var q=totah.lab.athena.system.rules.research.ResearchDocuments.decode(e.readPayload(),totah.lab.athena.system.rules.research.RuleQualificationReceipt.class);
   var expired=new totah.lab.athena.system.rules.research.RuleQualificationReceipt(q.schema(),q.ruleKey(),q.manifestSha256(),q.eligibility(),q.implementationReport(),q.foundationCertificate(),q.stateBinding(),q.request(),q.qualification(),q.mode(),q.evaluatedAt().plusSeconds(3600),q.reasons());
   f.inputs.remove(e);f.inputs.add(S1ResearchFixtures.envelope(f.state,"rv-expired","receipt","athena:rule-qualification-receipt",totah.lab.athena.system.rules.research.ResearchDocuments.encode(expired)));
  }
  var result=f.evaluate();
  var expected=java.util.Set.of("expired-main","expired-source","missing-witness").contains(variant)?NOT_EVALUATED:variant.equals("deprotonated")?UNSUPPORTED:java.util.Set.of("reordered","outlier").contains(variant)?SUPPORTED_PRESENT:UNKNOWN_INCONCLUSIVE;
  assertEquals(expected,result.status(),result.toString());var p=JSON.readTree(result.measurements().get("payload"));
  if(expected!=SUPPORTED_PRESENT){assertTrue(p.path("referenceScore").path("value").isNull());assertTrue(p.path("category").isNull());}
  if(variant.equals("outlier")){assertEquals("OUTLIER",p.path("category").asText());assertNotEquals(ABSENT_FALSE,result.status());}
  if(variant.equals("reordered")){java.util.Collections.reverse(f.inputs);assertEquals(result,f.evaluate());}
 }
 @ParameterizedTest @ValueSource(strings={"extra","score","bits","definition","tuple","category"})
 void serializedOutputCannotSelfQualify(String variant)throws Exception {
  var f=new ResidueValidationFixtures("SER",false,"valid");f.bind();var collector=totah.lab.athena.system.rules.RuleAnalyzers.collector(f.manifest,f.request);var raw=collector.analyze(f.state,f.inputs,java.util.Map.of()).getFirst();
  var p=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(raw.measurements().get("payload"));
  switch(variant){case "extra"->p.put("admitted",true);case "score"->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("referenceScore")).put("value",1);case "bits"->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("geometry").path("torsions").get(0)).put("binary64Hex","0000000000000000");case "definition"->p.put("definition",ResidueValidationFixtures.V09+"/1");case "tuple"->((com.fasterxml.jackson.databind.node.ArrayNode)p.path("geometry").path("torsions").get(0).path("atoms")).remove(0);case "category"->p.put("category","FAVORED");}
  f.add("athena:rule-measurements",totah.lab.athena.system.SystemStateView.bytes(p),collector.method());
  assertThrows(Exception.class,()->totah.lab.athena.system.rules.RuleAnalyzers.evaluator(f.manifest,f.request).analyze(f.state,f.inputs,java.util.Map.of()));
 }
 @Test void independentReplayCorpus()throws Exception {
  var reports=new java.util.TreeMap<String,Object>();
  for(var id:java.util.List.of("GLY","SER","VAL","PRO")){
   var f=new ResidueValidationFixtures(id,true,"valid");f.bind();f.qualify(temp.resolve("rama-"+id),true);var result=f.evaluate();assertEquals(SUPPORTED_PRESENT,result.status());reports.put("rama-"+id,JSON.readTree(result.measurements().get("payload")));
  }
  for(var id:java.util.List.of("SER","THR","VAL")){
   var f=new ResidueValidationFixtures(id,false,"valid");f.bind();f.qualify(temp.resolve("rotamer-"+id),true);var result=f.evaluate();assertEquals(SUPPORTED_PRESENT,result.status());reports.put("rotamer-"+id,JSON.readTree(result.measurements().get("payload")));
  }
  byte[] bytes=totah.lab.athena.system.SystemStateView.bytes(reports);System.out.println("V09_V10_REPLAY_SHA256="+totah.lab.mnemosyne.EvidenceExchange.sha256(bytes));
  var output=System.getProperty("athena.residueReplayOutput");if(output!=null)java.nio.file.Files.write(java.nio.file.Path.of(output),bytes);
 }

 @ParameterizedTest @ValueSource(strings={"GLY:valid:GLYCINE","SER:valid:GENERAL","VAL:valid:ILE_VAL","PRO:valid:TRANS_PROLINE","PRO:cis-pro:CIS_PROLINE","SER:next-pro:PRE_PROLINE","GLY:next-pro:GLYCINE","VAL:next-pro:PRE_PROLINE"})
 void sourceBoundSixClassComposition(String spec)throws Exception {
  var parts=spec.split(":");var f=new ResidueValidationFixtures(parts[0],true,parts[1]);f.bind();f.qualify(temp,true);var r=f.evaluate();assertEquals(SUPPORTED_PRESENT,r.status(),r.toString());assertEquals(parts[2],JSON.readTree(r.measurements().get("payload")).path("classSelection").path("ramaClass").asText());
 }
}
