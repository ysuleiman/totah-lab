package totah.lab.daedalus.system;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.WaterBridgeFixtures.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

class WaterBridgeAcceptanceTest {
    @ParameterizedTest @ValueSource(ints={1,2,3}) void explicitWaterPaths(int n)throws Exception {
        var f=new WaterBridgeFixtures(n,n>1);f.coverage(SUPPORTED_PRESENT);var r=f.result();assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText(),r.path("reasons").toString());assertTrue(r.path("complete").asBoolean(),r.path("reasons").toString());assertEquals(n,r.path("paths").get(0).path("waters").size());
    }
    @Test void noWaterWithoutInventoryIsUnknown()throws Exception {var f=new WaterBridgeFixtures(0,false);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());}
    @Test void completeEmptyModeledInventoryIsOnlyProfileNegative()throws Exception {var f=new WaterBridgeFixtures(0,false);f.coverage(SUPPORTED_PRESENT);assertEquals("ABSENT_FALSE",f.result().path("assessment").asText());}
    @Test void positiveSurvivesMissingInventory()throws Exception {var f=new WaterBridgeFixtures(1,false);var r=f.result();assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText());assertFalse(r.path("complete").asBoolean());}
    @ParameterizedTest @ValueSource(strings={"H","charge","graph"}) void unresolvedWaterIdentityBlocksNegative(String mode)throws Exception {var f=new WaterBridgeFixtures(chain(1),false,Map.of(1,mode));f.coverage(SUPPORTED_PRESENT);var r=f.result();assertEquals("UNKNOWN_INCONCLUSIVE",r.path("assessment").asText());assertFalse(r.path("complete").asBoolean());}
    @Test void completeInventoryNeverProvidesHydrogenOrientation()throws Exception {var fs=new ArrayList<>(chain(1));fs.set(1,water(3,false));var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());}
    @Test void inventoryDoesNotOverrideKnownNonwaterCharge()throws Exception {var fs=new ArrayList<>(chain(1));fs.set(1,charge(fs.get(1),"o",1));var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);var r=f.result();assertNotEquals("SUPPORTED_PRESENT",r.path("assessment").asText());assertEquals("UNSUPPORTED",r.path("waterIdentities").get(0).path("assessment").asText());}
    @Test void omittedPossiblyWaterOxygenDefeatsCoverageAssertion()throws Exception {var f=new WaterBridgeFixtures(1,false);((ArrayNode)f.plan.get("waters")).removeAll();f.coverage(SUPPORTED_PRESENT);var r=f.result();assertEquals("UNKNOWN_INCONCLUSIVE",r.path("assessment").asText());assertTrue(r.path("reasons").toString().contains("possibly-water"));}
    @Test void knownCarbonBoundEndpointOxygensDoNotBlockInventory()throws Exception {var f=new WaterBridgeFixtures(1,false);f.coverage(SUPPORTED_PRESENT);assertTrue(f.result().path("complete").asBoolean());}
    @Test void wrongSharedOrientationFailsDespiteCloseOxygens()throws Exception {var fs=new ArrayList<>(chain(1));fs.set(1,position(position(fs.get(1),"h1",3,1,0),"h2",3,-1,0));var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);assertEquals("ABSENT_FALSE",f.result().path("assessment").asText());}
    @ParameterizedTest @ValueSource(strings={"candidate","nodes","hops"}) void truncatedSearchIsNeverNegative(String bound)throws Exception {var f=new WaterBridgeFixtures(2,true);f.coverage(SUPPORTED_PRESENT);if(bound.equals("candidate"))f.budget=1;if(bound.equals("nodes"))f.nodes=1;if(bound.equals("hops"))f.hops=1;var r=f.result();assertNotEquals("ABSENT_FALSE",r.path("assessment").asText());assertFalse(r.path("complete").asBoolean());}
    @Test void contradictoryCoverageCannotSupportAbsence()throws Exception {var f=new WaterBridgeFixtures(0,false);f.coverage(SUPPORTED_PRESENT);f.coverage(ABSENT_FALSE);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());}
    @Test void duplicateSelectedWaterIsMalformed()throws Exception {var f=new WaterBridgeFixtures(1,false);((ArrayNode)f.plan.get("waters")).add(f.plan.get("waters").get(0).deepCopy());assertThrows(Exception.class,f::result);}
    @Test void incorrectWaterOrderProfileIsRejected()throws Exception {var f=new WaterBridgeFixtures(1,false);f.plan.put("maximumWaters",2);assertThrows(Exception.class,f::result);}
    @Test void provenanceTamperingFailsClosed()throws Exception {var f=new WaterBridgeFixtures(1,false);f.coverage(SUPPORTED_PRESENT);((ObjectNode)f.plan.path("inventoryCoverage").get(0)).put("sha256","0".repeat(64));assertThrows(Exception.class,f::result);}
    @Test void changedCoordinateBindingIsRejected()throws Exception {var f=new WaterBridgeFixtures(1,false);((ObjectNode)f.plan.get("stateBinding")).put("coordinateSha256","0".repeat(64));assertThrows(Exception.class,f::result);}
    @Test void reorderedArtifactInputsReplayIdentically()throws Exception {var f=new WaterBridgeFixtures(1,false);f.coverage(SUPPORTED_PRESENT);var before=f.result();Collections.reverse(f.artifacts);var after=f.result();
        // The explicit plan's bytes intentionally pin its declared order; scientific results do not change.
        assertEquals(before.path("paths"),after.path("paths"));assertEquals(before.path("legs"),after.path("legs"));assertEquals(before.path("assessment"),after.path("assessment"));}
    public static void main(String[] args)throws Exception {var results=new TreeMap<String,Object>();for(int n:List.of(1,2,3)){var f=new WaterBridgeFixtures(n,n>1);f.coverage(SUPPORTED_PRESENT);results.put("water"+n,f.result());}Files.write(Path.of(args[0]),totah.lab.athena.system.SystemStateView.bytes(results));}
}
