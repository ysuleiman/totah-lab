package totah.lab.daedalus.system;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.rules.ResidueContextTestAccess;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;
class ResidueContextSourceAcceptanceTest {
 @ParameterizedTest @ValueSource(strings={"charge","h","isotope","aromatic","electronic","membership","bonds","incident-bonds","next-pro","neighbor","scope","stereo-token","stereo-ligand","missing-alpha","unknown-alpha","correspondence","preparation","coherence","frame","model","missing-fact","missing-locations","source-state","candidate-label","source-default","conflicting-fact"})
 void unknownFactsNeverBecomeEligible(String variant)throws Exception {
  var f=new ResidueValidationFixtures("SER",false,"valid");
  var identity=f.facts.get("identityState");var atoms=(ArrayNode)identity.path("residues").get(1).path("atoms");var atom=(ObjectNode)atoms.get(0);
  switch(variant){
   case "charge"->atom.putNull("formalCharge");case "h"->atom.putNull("nonExplicitHydrogenCount");case "isotope"->atom.put("isotopeStatus","UNKNOWN");case "aromatic"->atom.putNull("aromatic");case "electronic"->atom.put("electronicState","UNKNOWN");
   case "membership"->atoms.remove(atoms.size()-1);
   case "bonds"->((ArrayNode)identity.path("residues").get(1).path("bonds")).remove(0);
   case "incident-bonds"->((ArrayNode)f.facts.get("connections").path("residues").get(1).path("incidentOrdinaryBonds")).remove(0);
   case "next-pro"->f.facts.get("connections").put("nextProline","TRUE");
   case "neighbor"->f.facts.get("connections").put("nextPresence","UNKNOWN");
   case "scope"->{f.binding.put("sourceScope","UNKNOWN");((ObjectNode)f.facts.get("connections").path("residues").get(1)).put("coverage","UNKNOWN");}
   case "stereo-token"->((ObjectNode)f.facts.get("stereochemistry").path("residues").get(1).path("sourceStereo").get(0)).put("sourceToken","R");
   case "stereo-ligand"->((ArrayNode)f.facts.get("stereochemistry").path("residues").get(1).path("sourceStereo").get(0).path("orderedLigands")).remove(0);
   case "missing-alpha"->((ArrayNode)f.facts.get("stereochemistry").path("residues").get(1).path("sourceStereo")).removeAll();
   case "unknown-alpha"->((ObjectNode)f.facts.get("stereochemistry").path("residues").get(1)).put("alpha","UNKNOWN");
   case "correspondence"->((ObjectNode)f.facts.get("atomCorrespondence").path("residues").get(1).path("roles")).putNull("OG");
   case "preparation"->f.facts.get("preparation").put("sourceKind","PREPARED_SOURCE");
   case "coherence"->f.facts.get("coherence").put("correlation","UNRESOLVED");
   case "frame"->((ArrayNode)f.facts.get("coherence").path("selectedAtoms")).remove(0);
   case "model"->((ObjectNode)f.facts.get("coherence").path("modelIdentity")).put("rawValue","2");
   case "missing-fact"->f.facts.remove("identityState");
   case "missing-locations"->((ArrayNode)f.binding.path("central").path("sourceLocations")).removeAll();
   case "source-state"->((ObjectNode)identity.path("residues").get(1)).put("sourceStateDescription","different state");
   case "candidate-label"->((ObjectNode)f.binding.path("central")).put("candidateIdentity","THR");
   case "source-default"->atom.put("formalCharge",42);
   default->{}
  }
  f.bind();if(variant.equals("conflicting-fact")){var conflict=f.facts.get("identityState").deepCopy();((ObjectNode)conflict.path("residues").get(1)).put("sourceStateDescription","other");f.witness("identityState",conflict);}
  assertEquals(UNKNOWN_INCONCLUSIVE,ResidueContextTestAccess.factualStatus(f.state,f.bindingEnvelope,f.inputs),variant);
 }
 @ParameterizedTest @ValueSource(strings={"D","allo","cyclic","derived","known-nonordinary","reviewed-excluded"})
 void knownExclusionsStayUnsupported(String variant)throws Exception {
  var f=new ResidueValidationFixtures(variant.equals("allo")?"THR":"SER",false,"valid");
  switch(variant){
   case "D"->((ObjectNode)f.facts.get("stereochemistry").path("residues").get(1)).put("alpha","D");
   case "allo"->((ObjectNode)f.facts.get("stereochemistry").path("residues").get(1)).put("beta","OTHER");
   case "cyclic"->f.facts.get("connections").put("cyclicPeptide","TRUE");
   case "derived"->{f.binding.put("sourceKind","DERIVED");f.facts.get("preparation").put("sourceKind","DERIVED");f.binding.set("preparationReferences",ResidueValidationFixtures.node(java.util.List.of(S1NitrogenAcceptanceTest.pin(f.protocol))));f.facts.get("preparation").set("preparationReferences",f.binding.get("preparationReferences"));}
   case "known-nonordinary"->{f.binding.put("sourceScope","KNOWN_NONORDINARY");var r=(ObjectNode)f.facts.get("connections").path("residues").get(1);r.put("coverage","KNOWN_NONORDINARY");r.withArray("nonordinary").add(ResidueValidationFixtures.node(java.util.Map.of("first",f.source("2_OG"),"second",java.util.Map.of("artifact",S1NitrogenAcceptanceTest.pin(f.original),"selector","original explicit Zn connection endpoint"),"kind","DATIVE","sourceLocations",f.locations())));}
   case "reviewed-excluded"->((ObjectNode)f.facts.get("identityState").path("residues").get(1)).put("domainStatus","UNSUPPORTED");
  }
  f.bind();assertEquals(UNSUPPORTED,ResidueContextTestAccess.factualStatus(f.state,f.bindingEnvelope,f.inputs),variant);
 }
 @ParameterizedTest @ValueSource(strings={"extra-field","missing-field","unknown-enum","duplicate-member","duplicate-role","bad-residue","wrong-state","duplicate-bond"})
 void strictPublicInputRejection(String variant)throws Exception {
  var f=new ResidueValidationFixtures("SER",false,"valid");switch(variant){
   case "extra-field"->f.binding.put("ramaClass","GENERAL");case "missing-field"->f.binding.remove("sourceScope");case "unknown-enum"->f.binding.put("sourceScope","DEFAULT");
   case "duplicate-member"->{var a=(ArrayNode)f.binding.path("central").path("atoms");a.add(a.get(0));}
   case "duplicate-role"->((ObjectNode)f.binding.path("central").path("roles")).set("OG",f.source("2_CA"));
   case "bad-residue"->((ObjectNode)f.binding.path("central").path("residue")).put("residueNumber",99);
   case "wrong-state"->((ObjectNode)f.binding.path("stateBinding")).put("coordinateSha256","0".repeat(64));
   case "duplicate-bond"->{var b=(ArrayNode)f.facts.get("identityState").path("residues").get(1).path("bonds");b.add(b.get(0));}
  }
  f.bind();assertThrows(Exception.class,()->ResidueContextTestAccess.factualStatus(f.state,f.bindingEnvelope,f.inputs));
 }
 @ParameterizedTest @ValueSource(strings={"THR","VAL"})
 void missingNativeSidechainAttributionIsUnknown(String id)throws Exception {
  var f=new ResidueValidationFixtures(id,false,"valid");var s=(ObjectNode)f.facts.get("stereochemistry").path("residues").get(1);
  if(id.equals("THR"))s.put("beta","UNKNOWN");else s.putNull("valineMethylAttribution");
  f.bind();assertEquals(UNKNOWN_INCONCLUSIVE,ResidueContextTestAccess.factualStatus(f.state,f.bindingEnvelope,f.inputs));
 }
}
