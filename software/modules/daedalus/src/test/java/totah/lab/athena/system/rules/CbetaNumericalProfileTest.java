package totah.lab.athena.system.rules;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CbetaNumericalProfileTest {
 @Test void pinnedUpstreamCoordinateCharacterizationWithoutUniversalParity()throws Exception {
  var rows=EventPayload.JSON.readTree(getClass().getResourceAsStream("/cbeta-deviation-v1/UPSTREAM_PROBES.json")).path("cases");assertEquals(24,rows.size());var differences=new ArrayList<String>();int eligible=0;
  for(var row:rows){var points=new ArrayList<CbetaReferenceGeometry.V>();for(var p:row.path("points"))points.add(new CbetaReferenceGeometry.V(p.get(0).doubleValue(),p.get(1).doubleValue(),p.get(2).doubleValue()));var q=CbetaReferenceGeometry.calculate(row.path("identity").asText(),points.get(0),points.get(1),points.get(2),points.get(3));String name=row.path("case").asText();
   if(Set.of("tiny-normal","collinear","zero-axis").contains(name)){assertNull(q.deviation());assertNotEquals("NONE",q.degeneracy());continue;}
   assertNotNull(q.deviation());eligible++;double upstream=Double.valueOf(row.path("deviationHex").asText());
   if(Double.doubleToRawLongBits(upstream)!=Double.doubleToRawLongBits(q.deviation()))differences.add(row.path("identity").asText()+"/"+name+": upstream="+Double.toHexString(upstream)+", native="+Double.toHexString(q.deviation())+", upstreamOutlier="+row.path("outlier")+", native="+CbetaReferenceGeometry.category(q.deviation()));
   // Exact score category at the SAME scalar, independently stated by upstream.
   assertEquals(row.path("outlier").asBoolean()?"OUTLIER":"NON_OUTLIER",CbetaReferenceGeometry.category(upstream));
   assertEquals(Double.doubleToRawLongBits(upstream),Double.doubleToRawLongBits(q.deviation()),"This pinned finite coordinate fixture only; not universal runtime parity");
  }
  assertEquals(15,eligible);System.out.println("V11_CROSS_RUNTIME_CHARACTERIZATION="+differences);
 }
}
