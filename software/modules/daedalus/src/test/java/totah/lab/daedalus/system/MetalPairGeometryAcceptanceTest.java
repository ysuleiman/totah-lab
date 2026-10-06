package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.SystemStateView;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest.*;

/** I14's raw pair descriptor; never metal donor typing, oxidation-state inference or coordination. */
class MetalPairGeometryAcceptanceTest {
    static SystemStateView state(String element,double distance,boolean frame) {
        var metal=new MolecularGraph(List.of(atom("M",element,Set.of("Na","K").contains(element)?1:2,false,0,0,0)),List.of(),Map.of("source","synthetic supplied ion state; not inferred oxidation state"));
        var partner=new MolecularGraph(List.of(atom("O","O",0,false,distance,0,0),atom("C","C",0,false,distance+1.2,0,0)),List.of(bond("C","O",MolecularGraph.BondOrder.DOUBLE)),Map.of("source","supplied partner carbonyl; no metal binding assertion"));
        var s=system(List.of(metal,partner),false,false);
        return new SystemStateView(s.identity(),s.graph(),s.components(),s.sources(),s.cofactors(),s.charges(),frame,false,s.limitations());
    }
    static JsonNode report(SystemStateView s,int budget)throws Exception {
        var p=plan(s);group(p,"explicit-metal",ref(1,"M"));group(p,"explicit-partner",ref(2,"O"),ref(2,"C"));
        op(p,"PAIR_MATRIX").put("firstGroupId","explicit-metal").put("secondGroupId","explicit-partner").put("hydrogenScope","ALL_EXPLICIT").put("topologyPolicy","RETAIN_ALL");
        return ContinuousGeometryV3AcceptanceTest.runV3(s,p,null,100,budget);
    }
    static JsonNode pairs(JsonNode report){return report.path("operations").get(0).path("quantities").path("pairs");}
    @ParameterizedTest @ValueSource(strings={"Zn","Mg","Ca","Fe","Cu","Co","Mn","Ni","Na","K"})
    void exactSourceMetalAndPartnerIdsDistancesAndNoSilentRadii(String element)throws Exception {
        var s=state(element,2.5,true);var r=report(s,100);var rows=pairs(r).path("value");assertEquals(2,rows.size());
        var distances=new TreeSet<Double>();for(var row:rows){distances.add(row.path("distanceAngstrom").asDouble());assertTrue(row.path("gapAngstrom").isNull());assertTrue(row.path("firstRadius").isNull());assertTrue(row.path("secondRadius").isNull());assertEquals("NOT_EVALUATED",row.path("topologyStatus").asText());}
        assertEquals(new TreeSet<>(List.of(2.5,3.7)),distances);assertEquals("SUPPORTED_PRESENT",pairs(r).path("status").asText());
        assertEquals(element,s.components().getFirst().chemistry().atoms().getFirst().element());assertFalse(r.toString().contains("COORDINATION_PRESENT"));
    }
    @Test void farPairRemainsMeasuredRatherThanSilentlyAbsent()throws Exception {
        var r=report(state("Zn",30,true),100);assertEquals(2,pairs(r).path("value").size());assertTrue(pairs(r).path("value").get(0).path("distanceAngstrom").asDouble()>=30);assertEquals("SUPPORTED_PRESENT",pairs(r).path("status").asText());
    }
    @Test void incompletePairTraversalAndUnqualifiedFrameRemainInconclusive()throws Exception {
        var partial=report(state("Zn",2.5,true),1);assertEquals("UNKNOWN_INCONCLUSIVE",pairs(partial).path("status").asText());assertEquals(1,pairs(partial).path("value").size());
        var frame=report(state("Zn",2.5,false),100);assertEquals("UNKNOWN_INCONCLUSIVE",pairs(frame).path("status").asText());assertEquals(2,pairs(frame).path("value").size());
    }
    public static void main(String[] args)throws Exception {var out=new ArrayList<JsonNode>();for(String metal:List.of("Zn","Mg","Fe","Cu"))out.add(report(state(metal,2.5,true),100));Files.write(Path.of(args[0]),SystemStateView.bytes(out));}
}
