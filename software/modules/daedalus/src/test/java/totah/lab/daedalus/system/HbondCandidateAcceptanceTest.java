package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.HbondCandidateFixtures.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.JSON;
import totah.lab.athena.system.SystemStateView;

class HbondCandidateAcceptanceTest {
    static List<String> acceptors() throws Exception {
        var list=new ArrayList<String>();JSON.readTree(manifest().parameters().get("roles").value()).get("acceptors").fieldNames().forEachRemaining(list::add);return list;
    }
    @TestFactory Stream<DynamicTest> allReviewedClassPairs() throws Exception {
        var cases=new ArrayList<DynamicTest>();
        for(var row:ChemicalRoleAcceptanceTest.cases())if(row[0].startsWith("DONOR."))for(var a:acceptors()) {
            var donor=row[0];
            for(var far:List.of(false,true))cases.add(DynamicTest.dynamicTest(donor+"/"+a+"/"+far,()->{
                var report=collect(sample(donor,a,far?3.51:3.0,180,true,"complete"));var p=pair(report,donor,a);
                assertEquals(donor.equals("DONOR.PYRIDINIUM_NH")?"UNKNOWN_INCONCLUSIVE":far?"ABSENT_FALSE":"SUPPORTED_PRESENT",p.get("assessment").asText(),p.toString());
                if(!donor.equals("DONOR.PYRIDINIUM_NH")){assertTrue(p.get("complete").asBoolean(),p.toString());assertFalse(p.get("candidates").isEmpty());}
            }));
        }return cases.stream();
    }
    @Test void boundariesAndReportedOnlyAcceptorGeometry() throws Exception {
        for(double distance:new double[]{3.499999,3.5,3.500001})for(double angle:new double[]{129.999,130,130.001,180}) {
            var p=pair(collect(sample("DONOR.ALCOHOL","ALCOHOL_O",distance,angle,true,"complete")),"DONOR.ALCOHOL","ALCOHOL_O");
            var tuple=p.get("candidates").get(0);var ops=tuple.get("geometry").get("measurements").get("operations");
            double measuredD=Double.parseDouble(ops.get(0).get("quantities").get("distanceAngstrom").get("value").asText());
            double measuredAngle=Double.parseDouble(ops.get(2).get("quantities").get("angleDegrees").get("value").asText());
            assertEquals(distance,measuredD,1e-12);assertEquals(angle,measuredAngle,1e-12);
            assertEquals(measuredD<=3.5&&measuredAngle>=130?"SUPPORTED_PRESENT":"ABSENT_FALSE",p.get("assessment").asText());
            assertTrue(ops.get(1).get("quantities").has("distanceAngstrom"));assertEquals(5,ops.size());
        }
    }
    @Test void missingHydrogenAndIncompleteSourceAreNeverNegative() throws Exception {
        for(var mode:List.of("complete","incomplete","unknownH"))for(boolean explicit:List.of(false,true)) {
            if(mode.equals("complete")&&explicit)continue;
            var p=pair(collect(sample("DONOR.AMINE_PRIMARY","ETHER_O",5,180,explicit,mode)),"DONOR.AMINE_PRIMARY","ETHER_O");
            assertEquals("UNKNOWN_INCONCLUSIVE",p.get("assessment").asText());
        }
    }
    @Test void everyExplicitHydrogenIsRetained() throws Exception {
        var p=pair(collect(sample("DONOR.AMMONIUM_PRIMARY","ETHER_O",3,180,true,"complete")),"DONOR.AMMONIUM_PRIMARY","ETHER_O");
        assertEquals(3,p.get("candidates").size());
        assertEquals(3,java.util.stream.StreamSupport.stream(p.get("candidates").spliterator(),false).map(n->n.get("hydrogen").toString()).distinct().count());
    }
    @Test void reportedHydrogenDistanceDoesNotIntroduceAnUnapprovedCutoff() throws Exception {
        var p=pair(collect(sample("DONOR.ALCOHOL","ETHER_O",3.5,130.001,true,"complete")),"DONOR.ALCOHOL","ETHER_O");
        assertEquals("SUPPORTED_PRESENT",p.get("assessment").asText());
        var ha=p.get("candidates").get(0).get("geometry").get("measurements").get("operations").get(1).get("quantities").get("distanceAngstrom").get("value");
        assertTrue(Double.parseDouble(ha.asText())>2.5);
    }
    @Test void coincidentDonorAndAcceptorIsInconclusive() throws Exception {
        var p=pair(collect(sample("DONOR.ALCOHOL","ETHER_O",0,180,true,"complete")),"DONOR.ALCOHOL","ETHER_O");
        assertEquals("UNKNOWN_INCONCLUSIVE",p.get("assessment").asText());
    }
    public static void main(String[] args) throws Exception {
        var reports=new TreeMap<String,JsonNode>();
        for(var row:ChemicalRoleAcceptanceTest.cases())if(row[0].startsWith("DONOR."))for(var a:acceptors())reports.put(row[0]+"/"+a,collect(sample(row[0],a,3,180,true,"complete")));
        Files.write(Path.of(args[0]),SystemStateView.bytes(reports));
    }
}
