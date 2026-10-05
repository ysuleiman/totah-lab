package totah.lab.daedalus.system;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.system.rules.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
class B00ScientificMigrationTest {
 @Test void historicalManifestRejectsCorrectedMatcher()throws Exception {
  var old=RuleRegistry.load(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/scientific"));
  for(var m:old.manifests().values()) {
   var s=fixture(AthenaScientificRules.family(m.ruleId()),false,false);
   var ex=assertThrows(MolecularBackendException.class,()->AthenaScientificRules.collect(s,m,request(s,m,1000,false),OCL));
   assertTrue(ex.getMessage().contains("binding mismatch"));
  }
 }
 @Test void glyoxalRecoveredCarbonylIsEligibleAtUnchangedGeometry()throws Exception {
  var g=new MolecularGraph(List.of(atom("O1","O",0,false,12,0,0),atom("C1","C",0,false,13.2,0,0),atom("C2","C",0,false,4,0,0),atom("O2","O",0,false,2.8,0,0)),
   List.of(bond("O1","C1",MolecularGraph.BondOrder.DOUBLE),bond("C1","C2",MolecularGraph.BondOrder.SINGLE),bond("C2","O2",MolecularGraph.BondOrder.DOUBLE)),Map.of());
  var s=system(List.of(methanol(),g),true,false);var m=manifest(RuleRegistry.scientific(),"HBOND");
  var measured=AthenaScientificRules.collect(s,m,request(s,m,1000,false),OCL);
  assertEquals(List.of(true,true),measured.candidateSupported());
  assertEquals(2.8,measured.raw().candidates().get(1).values().get("distance"));
  assertTrue(AthenaScientificRules.qualifies(measured.raw().candidates().get(1),m));
  assertEquals("2",m.implementationVersion());
 }
 @Test void definitionsAndHistoricalBytesAreNotMigrated()throws Exception {
  var old=RuleRegistry.load(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/scientific"));
  for(var historical:old.manifests().values()) {
   var current=manifest(RuleRegistry.scientific(),AthenaScientificRules.family(historical.ruleId()));
   assertEquals(historical.parameters(),current.parameters());assertEquals(historical.scientificSources(),current.scientificSources());
   assertEquals(historical.negativeCoverage(),current.negativeCoverage());
   assertEquals(historical.version(),current.version());assertNotEquals(historical.key(),current.key());
  }
 }
}
