package totah.lab.daedalus.system;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.JSON;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;
class RestraintAcceptanceTest {
 @TempDir Path temp;
 @ParameterizedTest @ValueSource(strings={"SER","THR","VAL"})
 void allRows(String id)throws Exception {var w=new RestraintFixtures(id);w.prepare();w.bind();w.qualify(temp,true,true);var out=w.evaluate();assertEquals(SUPPORTED_PRESENT,out.status(),out.toString());var p=JSON.readTree(out.measurements().get("payload"));assertEquals(id.equals("SER")?10:13,p.path("rows").size());for(var r:p.path("rows")){double x=r.path("measurement").path("value").doubleValue(),t=Double.parseDouble(r.path("reference").path("targetDecimal").asText()),s=Double.parseDouble(r.path("reference").path("esdDecimal").asText());assertEquals(Double.doubleToRawLongBits(x-t),Double.doubleToRawLongBits(r.path("signedResidual").path("value").doubleValue()));assertEquals(Double.doubleToRawLongBits((x-t)/s),Double.doubleToRawLongBits(r.path("restraintNormalizedResidual").path("value").doubleValue()));assertFalse(r.path("reference").path("provenanceChain").isEmpty());}String path=System.getProperty("v07.replay");if(path!=null)Files.writeString(Path.of(path+"-"+id+".json"),out.measurements().get("payload"));}

 @ParameterizedTest(name="{0}") @ValueSource(strings={
  "UNSUPPORTED:D-SER","UNSUPPORTED:allo-THR","UNSUPPORTED:deprotonated-SER","UNSUPPORTED:modified","UNSUPPORTED:cyclic","UNKNOWN_INCONCLUSIVE:conflicting-isotope","UNKNOWN_INCONCLUSIVE:conflicting-radical","UNSUPPORTED:incoming-excluded","UNSUPPORTED:outgoing-excluded",
  "UNKNOWN_INCONCLUSIVE:charge","UNKNOWN_INCONCLUSIVE:hydrogens","UNKNOWN_INCONCLUSIVE:aromaticity","UNKNOWN_INCONCLUSIVE:stereo","UNKNOWN_INCONCLUSIVE:connection-coverage","UNKNOWN_INCONCLUSIVE:nonordinary-unresolved","UNKNOWN_INCONCLUSIVE:coherence","UNKNOWN_INCONCLUSIVE:incoming-unresolved","UNKNOWN_INCONCLUSIVE:outgoing-unresolved","UNKNOWN_INCONCLUSIVE:conflicting-profile","UNKNOWN_INCONCLUSIVE:empty-profile-evidence","UNKNOWN_INCONCLUSIVE:missing-coordinate","UNKNOWN_INCONCLUSIVE:degenerate-angle",
  "NOT_EVALUATED:binding-only","NOT_EVALUATED:no-source-authority","NOT_EVALUATED:no-profile-authority","NOT_EVALUATED:no-rule-authority","NOT_EVALUATED:no-profile-witness","NOT_EVALUATED:expired-profile","NOT_EVALUATED:expired-source","NOT_EVALUATED:expired-rule","NOT_EVALUATED:budget","NOT_EVALUATED:missing-binding",
  "REJECT:wrong-state","REJECT:wrong-profile","REJECT:wrong-row-digest","REJECT:role-collision","REJECT:extra-binding-field","REJECT:reversed-modifications","REJECT:wrong-link-component","REJECT:wrong-link-enum","REJECT:changed-raw-geometry","REJECT:changed-collected-payload","REJECT:invented-category","REJECT:changed-definition","REJECT:changed-target","UNKNOWN_INCONCLUSIVE:changed-reference-bytes","UNKNOWN_INCONCLUSIVE:missing-reference",
  "SUPPORTED_PRESENT:reordered-inputs","SUPPORTED_PRESENT:large-residual","SUPPORTED_PRESENT:source-unchanged"})
 void boundary(String spec)throws Exception {
  String expected=spec.split(":")[0],name=spec.split(":")[1];String id=name.contains("THR")?"THR":"SER";
  var xyz=new HashMap<String,double[]>();if(name.equals("degenerate-angle"))xyz.put("2_N",new double[]{1,0,0});if(name.equals("large-residual"))xyz.put("2_OG",new double[]{1000,1000,1000});
  var w=new RestraintFixtures(id,name.equals("deprotonated-SER")?"deprotonated":"valid",xyz);var f=w.f;String before=S1NitrogenAcceptanceTest.canonical(f.state.binding());
  var row=(com.fasterxml.jackson.databind.node.ObjectNode)f.facts.get("identityState").path("residues").get(1);var atom=(com.fasterxml.jackson.databind.node.ObjectNode)row.path("atoms").get(0);var stereo=(com.fasterxml.jackson.databind.node.ObjectNode)f.facts.get("stereochemistry").path("residues").get(1);
  switch(name){
   case "D-SER"->stereo.put("alpha","D");case "allo-THR"->stereo.put("beta","OTHER");case "modified"->row.put("domainStatus","UNSUPPORTED");case "cyclic"->f.facts.get("connections").put("cyclicPeptide","TRUE");case "conflicting-isotope"->{atom.put("isotopeMass",15);atom.put("isotopeStatus","EXPLICIT");}case "conflicting-radical"->atom.put("electronicState","KNOWN_NON_NONE");
   case "charge"->atom.putNull("formalCharge");case "hydrogens"->atom.putNull("nonExplicitHydrogenCount");case "aromaticity"->atom.putNull("aromatic");case "stereo"->stereo.put("alpha","UNKNOWN");case "connection-coverage"->((com.fasterxml.jackson.databind.node.ObjectNode)f.facts.get("connections").path("residues").get(1)).put("coverage","UNKNOWN");case "nonordinary-unresolved"->f.binding.put("sourceScope","UNKNOWN");case "coherence"->f.facts.get("coherence").put("correlation","UNRESOLVED");
   case "missing-coordinate"->{for(var a:f.binding.path("central").path("atoms"))if(a.path("source").path("atomId").asText().equals("2_OG"))((com.fasterxml.jackson.databind.node.ObjectNode)a).putNull("coordinate");}
  }
  w.prepare();var profile=(com.fasterxml.jackson.databind.node.ObjectNode)w.binding.get("referenceProfile");var links=(com.fasterxml.jackson.databind.node.ArrayNode)w.fact.get("linkFacts");
  switch(name){
   case "incoming-excluded"->((com.fasterxml.jackson.databind.node.ObjectNode)links.get(0)).put("applicability","EXCLUDED");case "outgoing-excluded"->((com.fasterxml.jackson.databind.node.ObjectNode)links.get(1)).put("applicability","EXCLUDED");case "incoming-unresolved"->((com.fasterxml.jackson.databind.node.ObjectNode)links.get(0)).put("applicability","UNRESOLVED");case "outgoing-unresolved"->((com.fasterxml.jackson.databind.node.ObjectNode)links.get(1)).put("applicability","UNRESOLVED");case "empty-profile-evidence"->((com.fasterxml.jackson.databind.node.ObjectNode)links.get(0)).putArray("applicabilityEvidence");
   case "wrong-state"->((com.fasterxml.jackson.databind.node.ObjectNode)w.binding.get("stateBinding")).put("coordinateSha256","0".repeat(64));case "wrong-profile"->profile.put("identity","OTHER");case "wrong-row-digest"->((com.fasterxml.jackson.databind.node.ObjectNode)profile.get("referenceRows")).put("sha256","0".repeat(64));case "role-collision"->{var a=(com.fasterxml.jackson.databind.node.ArrayNode)w.binding.get("atomCorrespondence");((com.fasterxml.jackson.databind.node.ObjectNode)a.get(1)).set("source",a.get(0).get("source"));}case "extra-binding-field"->w.binding.put("qualified",true);case "reversed-modifications"->profile.set("centralModifications",ResidueValidationFixtures.node(List.of("DEL-OXT","DEL-HN1")));case "wrong-link-component"->((com.fasterxml.jackson.databind.node.ObjectNode)w.binding.path("linkBindings").get(0)).put("centralComponent",1);case "wrong-link-enum"->((com.fasterxml.jackson.databind.node.ObjectNode)links.get(0)).put("applicability","DEFAULT");
  }
  w.bind();if(name.equals("conflicting-profile")){var other=w.fact.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)other.path("linkFacts").get(0)).put("applicability","EXCLUDED");w.witness(other);}
  if(!name.equals("binding-only"))w.qualify(temp,!name.equals("no-source-authority"),!name.equals("no-profile-authority"));
  switch(name){
   case "no-rule-authority"->f.inputs.removeIf(e->e.evidenceType().equals("athena:rule-qualification-receipt")&&e.reference().id().startsWith("rv-main/"));
   case "no-profile-witness"->f.inputs.removeAll(w.profileWitnesses);case "missing-binding"->f.inputs.remove(w.bindingEnvelope);
   case "expired-profile"->CbetaAcceptanceTest.expire(f,"v07-profile/");case "expired-source"->CbetaAcceptanceTest.expire(f,"rv-source/");case "expired-rule"->CbetaAcceptanceTest.expire(f,"rv-main/");
   case "budget"->f.request=new totah.lab.athena.system.rules.RuleRequest(f.request.state(),f.request.manifestKey(),f.request.manifestSha256(),f.request.atoms(),f.request.first(),f.request.second(),.1,0,10000,1);
   case "reordered-inputs"->Collections.reverse(f.inputs);
   case "changed-reference-bytes","missing-reference"->{f.inputs.remove(w.refs.get("s__SER.cif"));if(name.equals("changed-reference-bytes"))f.add("athena:source-artifact",Map.of("changed",true));}
  }
  if(Set.of("changed-collected-payload","invented-category","changed-definition","changed-target","changed-raw-geometry").contains(name)){tamper(w,name);return;}
  if(expected.equals("REJECT")){assertThrows(Exception.class,w::evaluate);return;}
  var result=w.evaluate();assertEquals(totah.lab.mnemosyne.EvidenceInterpretation.Status.valueOf(expected),result.status(),spec+result);
  assertEquals(before,S1NitrogenAcceptanceTest.canonical(f.state.binding()));if(result.measurements().containsKey("payload")){var out=JSON.readTree(result.measurements().get("payload"));assertFalse(out.has("category"));if(expected.equals("NOT_EVALUATED"))for(var r:out.path("rows"))assertTrue(r.path("signedResidual").path("value").isNull());if(name.equals("degenerate-angle"))assertTrue(out.path("rowCoverage").path("evaluatedRowIds").size()>0);}
 }
 void tamper(RestraintFixtures w,String name)throws Exception {
  var f=w.f;if(name.equals("changed-raw-geometry")){var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:rule-measurements")).findFirst().orElseThrow();var p=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(e.readPayload());p.put("forged",true);f.inputs.remove(e);f.add("athena:rule-measurements",totah.lab.athena.system.SystemStateView.bytes(p),e.method());assertThrows(Exception.class,w::evaluate);return;}
  var collector=totah.lab.athena.system.rules.RuleAnalyzers.collector(f.manifest,f.request);var raw=collector.analyze(f.state,f.inputs,Map.of()).getFirst();var p=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(raw.measurements().get("payload"));switch(name){case "invented-category"->p.put("category","FAVORED");case "changed-definition"->p.put("definitionSha256","0".repeat(64));case "changed-target"->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0).path("reference")).put("targetDecimal","1.4");default->p.put("assessmentStatus","SUPPORTED_PRESENT");}f.add("athena:rule-measurements",totah.lab.athena.system.SystemStateView.bytes(p),collector.method());assertThrows(Exception.class,()->totah.lab.athena.system.rules.RuleAnalyzers.evaluator(f.manifest,f.request).analyze(f.state,f.inputs,Map.of()));
 }
}
