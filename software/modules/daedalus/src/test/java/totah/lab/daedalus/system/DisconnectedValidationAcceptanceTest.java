package totah.lab.daedalus.system;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.system.SystemStateView;
import static totah.lab.athena.design.backend.MolecularValidationService.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;
import static org.junit.jupiter.api.Assertions.*;
class DisconnectedValidationAcceptanceTest {
 static final OclMolecularBackend OLD=new OclMolecularBackend(),NEW=OclMolecularBackend.forChemicalStateValidation();
 static MolecularGraph separate(String first,int charge1,String second,int charge2) {
  return new MolecularGraph(List.of(new MolecularGraph.Atom("a",first,null,charge1,0,false,"UNSPECIFIED",new MolecularGraph.Coordinates(0,0,0),Map.of()),new MolecularGraph.Atom("b",second,null,charge2,0,false,"UNSPECIFIED",new MolecularGraph.Coordinates(5,0,0),Map.of())),List.of(),Map.of("fixture","supplied disconnected atoms"));
 }
 static Map<String,MolecularGraph> fixtures()throws Exception {
  return new TreeMap<>(Map.of("disconnected-net-zero",separate("Na",1,"Cl",-1),"disconnected-neutral",separate("C",0,"C",0),"disconnected-charged",separate("Na",1,"C",0),"connected-neutral",DimensionalValidationAcceptanceTest.graph("methylamine"),"connected-charged",DimensionalValidationAcceptanceTest.graph("methylammonium")));
 }
 @TestFactory java.util.stream.Stream<DynamicTest> scopes()throws Exception {
  return fixtures().entrySet().stream().flatMap(e->Arrays.stream(NeutralityPolicy.values()).map(policy->DynamicTest.dynamicTest(e.getKey()+"/"+policy,()->{
   var graph=e.getValue();var before=SystemStateView.bytes(graph);var r=NEW.validateDimensions(graph,policy);
   for(var d:Dimension.values())if(d!=Dimension.NET_NEUTRALITY)assertEquals(SUPPORTED_PRESENT,r.dimensions().get(d).status(),r.toString());
   int charge=graph.atoms().stream().mapToInt(MolecularGraph.Atom::formalCharge).sum();assertEquals(charge,r.netFormalCharge());
   assertEquals(policy==NeutralityPolicy.OBSERVE_ONLY?NOT_EVALUATED:charge==0?SUPPORTED_PRESENT:ABSENT_FALSE,r.dimensions().get(Dimension.NET_NEUTRALITY).status());
   var sanitized=NEW.sanitize(graph,new MolecularSanitizer.SanitizationPolicy(Set.of(),true));assertEquals(graph,sanitized.graph());assertTrue(NEW.validate(graph).valid());
   if(charge==0){assertEquals(OLD.sanitize(graph,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)).graph(),sanitized.graph());assertTrue(OLD.validate(graph).valid());}
   else {assertThrows(MolecularBackendException.class,()->OLD.sanitize(graph,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)));assertTrue(r.evidence().messages().toString().contains("unbalanced atom charge"));}
   assertArrayEquals(before,SystemStateView.bytes(graph));
  })));
 }
 @TestFactory java.util.stream.Stream<DynamicTest> malformed() {
  return java.util.stream.Stream.of("endpoint","self","duplicate").flatMap(kind->Arrays.stream(NeutralityPolicy.values()).map(policy->DynamicTest.dynamicTest(kind+"/"+policy,()->{
   var graph=separate("Na",1,"Cl",-1);var bond=new MolecularGraph.Bond("x","a",kind.equals("endpoint")?"missing":kind.equals("self")?"a":"b",MolecularGraph.BondOrder.SINGLE,false,"UNSPECIFIED",Map.of());
   var bad=new MolecularGraph(graph.atoms(),kind.equals("duplicate")?List.of(bond,bond):List.of(bond),Map.of());
   assertEquals(FAILED,NEW.validateDimensions(bad,policy).dimensions().get(Dimension.TOPOLOGY_VALENCE).status());
   assertThrows(MolecularBackendException.class,()->OLD.sanitize(bad,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)));
   assertThrows(MolecularBackendException.class,()->NEW.sanitize(bad,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)));
  })));
 }
 @Test void systemLevelConnectednessRemainsExplicitAndUnchanged()throws Exception {
  var graph=separate("C",0,"C",0);var state=AthenaScientificRulesAcceptanceTest.system(List.of(graph),true,false);
  var legacy=new totah.lab.athena.system.SystemGraphValidation(OLD,OLD,OLD).validate(state);
  var corrected=new totah.lab.athena.system.SystemGraphValidation(NEW,NEW,NeutralityPolicy.OBSERVE_ONLY).validate(state);
  var capability=totah.lab.athena.system.SystemGraphCertificate.Capability.GRAPH_TRANSFORMATIONS;
  assertEquals(totah.lab.athena.system.SystemGraphCertificate.Status.FAILED,legacy.capabilities().get(capability).status());
  assertEquals(legacy.capabilities().get(capability),corrected.capabilities().get(capability));
  assertEquals(SUPPORTED_PRESENT,NEW.validateDimensions(graph,NeutralityPolicy.OBSERVE_ONLY).dimensions().get(Dimension.TOPOLOGY_VALENCE).status());
 }
 public static void main(String[] args)throws Exception {
  var results=new TreeMap<String,Object>();for(var e:fixtures().entrySet())for(var p:NeutralityPolicy.values())results.put(e.getKey()+"/"+p,NEW.validateDimensions(e.getValue(),p));Files.write(Path.of(args[0]),SystemStateView.bytes(results));
 }
}
