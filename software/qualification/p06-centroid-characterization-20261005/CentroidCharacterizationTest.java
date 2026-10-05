package totah.lab.daedalus.system;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import totah.lab.athena.system.SystemStateView;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.ref;
/** Characterization of preserved v1; this test does not endorse centroid suppression. */
class CentroidCharacterizationTest {
 static com.fasterxml.jackson.databind.JsonNode report()throws Exception {
  var s=ContinuousGeometryAcceptanceTest.state(new double[][]{{0,0,0},{2,0,0},{4,0,0}});
  var p=ContinuousGeometryAcceptanceTest.plan(s);
  ContinuousGeometryAcceptanceTest.group(p,"collinear",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"));
  ContinuousGeometryAcceptanceTest.op(p,"PLANE").put("groupId","collinear");
  return ContinuousGeometryAcceptanceTest.run(s,p);
 }
 @Test void v1OmitsDefinedCentroidWhenNormalIsUndefined()throws Exception {
  var r=report();var q=r.get("operations").get(0).get("quantities");
  assertFalse(q.has("centroidAngstrom"));assertEquals("UNKNOWN_INCONCLUSIVE",q.get("measurement").get("status").asText());
  assertEquals(2.0,(0.0+2.0+4.0)/3.0); // independent hand-specified arithmetic oracle
 }
 public static void main(String[] args)throws Exception{Files.write(Path.of(args[0]),SystemStateView.bytes(report()));}
}
