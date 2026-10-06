package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.WaterBridgeFixtures.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Explicit adversarial witnesses for the named acceptance-matrix rows. */
class WaterBridgeMatrixClosureTest {
    @Test void anotherSelectedEndpointCannotBeAnInternalNonwaterTransit()throws Exception {
        var fs=new ArrayList<>(chain(3));fs.set(2,endpoint(6,true));
        var f=new WaterBridgeFixtures(fs,true,Map.of());
        ((ArrayNode)f.plan.path("first")).add(node(ref(3,"o")));
        ((ArrayNode)f.plan.path("waters")).remove(1);
        f.coverage(SUPPORTED_PRESENT);var r=f.result();
        // All four successive legs exist, but the central alcohol is not an internal water.
        long positive=0;for(var leg:r.path("legs"))if(leg.path("assessment").asText().equals("SUPPORTED_PRESENT"))positive++;
        assertTrue(positive>=4);assertTrue(r.path("paths").isEmpty());assertEquals("ABSENT_FALSE",r.path("assessment").asText());
    }
    @Test void distinctHOrientationsRetainPositiveAndNegativeLegs()throws Exception {
        var f=new WaterBridgeFixtures(1,false);f.coverage(SUPPORTED_PRESENT);var r=f.result();var statuses=new TreeMap<String,String>();
        for(var leg:r.path("legs"))if(leg.path("donor").path("residueNumber").asInt()==2&&leg.path("acceptor").path("residueNumber").asInt()==3)
            statuses.put(leg.path("hydrogen").path("atomName").asText(),leg.path("assessment").asText());
        assertEquals(Map.of("h1","SUPPORTED_PRESENT","h2","ABSENT_FALSE"),statuses);
    }
    @Test void missingIndependentlyRequiredRoleReportCannotBecomeCoveredNegative()throws Exception {
        var f=new WaterBridgeFixtures(1,false);
        f.artifacts.removeIf(e->{try{var n=JSON.readTree(e.readPayload());return n.path("definition").path("groupId").asText().equals("ATHENA.PERCEPTION.DONOR.ALCOHOL")&&n.path("componentReference").equals(node(f.state.components().get(0).identity()));}catch(Exception ex){throw new RuntimeException(ex);}});
        f.coverage(SUPPORTED_PRESENT);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());
    }
}
