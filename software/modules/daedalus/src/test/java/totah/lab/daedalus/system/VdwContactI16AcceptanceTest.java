package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.system.rules.RuleAnalyzers;
import totah.lab.athena.system.SystemStateView;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.JSON;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.envelope;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

/** I16 only: attributed surface descriptors, never a clash/energy interpretation. */
class VdwContactI16AcceptanceTest {
    @TempDir Path temp;
    static ObjectNode pairPlan(SystemStateView s) {
        var p=plan(s);group(p,"a",ref(1,"X0"));group(p,"b",ref(1,"X1"),ref(1,"X2"));matrix(p,"PAIR_MATRIX","a","b");return p;
    }
    static EvidenceEnvelope selectedRadii(SystemStateView s,ObjectNode p,ObjectNode payload) {
        var e=envelope(s,"athena:radius-assignments",payload);
        p.set("radiusAssignmentReference",node(Map.of("reference",e.reference(),"payloadSha256",e.payloadSha256())));return e;
    }
    static JsonNode mixed(String unavailable)throws Exception {
        var s=state(new double[][]{{0,0,0},{2,0,0},{8,0,0}});var p=pairPlan(s);
        var original=radii(s,p,1);var radius=(ObjectNode)JSON.readTree(original.readPayload());
        var entries=(ArrayNode)radius.get("assignments");
        if(unavailable.equals("MISSING"))entries.remove(2);
        else {var item=(ObjectNode)entries.get(2);item.put("status",unavailable);item.putNull("radius");}
        var e=selectedRadii(s,p,radius);var report=run(s,p,e,100,100);var rows=value(report,0,"pairs");
        assertEquals(2,rows.size());near(2,rows.get(0).get("distanceAngstrom"));near(0,rows.get(0).get("gapAngstrom"));
        near(8,rows.get(1).get("distanceAngstrom"));assertTrue(rows.get(1).get("gapAngstrom").isNull());assertTrue(rows.get(1).get("overlapAngstrom").isNull());
        assertEquals("UNKNOWN_INCONCLUSIVE",rows.get(1).get("radiusStatus").asText());
        assertEquals("UNKNOWN_INCONCLUSIVE",report.get("operations").get(0).get("coverage").get("radiusCoverage").asText());
        assertEquals(node(Map.of("reference",e.reference(),"payloadSha256",e.payloadSha256())),report.get("radiusAssignmentReference"));
        assertArrayEquals(SystemStateView.bytes(radius),e.readPayload());assertFalse(report.toString().contains("ABSENT_FALSE"));return report;
    }
    @TestFactory Stream<DynamicTest> mixedUnavailableRadiiNeverEraseDistances() {
        return Stream.of("MISSING","UNKNOWN_INCONCLUSIVE","UNSUPPORTED","FAILED","NOT_EVALUATED").map(status->DynamicTest.dynamicTest(status,()->mixed(status)));
    }
    @Test void modelChangeAgainstPinnedEnvelopeFailsAfterPreservation()throws Exception {
        var s=state(new double[][]{{0,0,0},{2,0,0},{8,0,0}});var p=pairPlan(s);var original=radii(s,p,1);
        var changed=(ObjectNode)JSON.readTree(original.readPayload());((ObjectNode)changed.get("model")).put("version","unapproved-other-model");
        var replacement=envelope(s,"athena:radius-assignments",changed); // Intentionally retain the original plan pin.
        var pe=envelope(s,"athena:continuous-geometry-plan",p);var m=manifest();var request=request(s,m,p,100,100);
        var catalog=new EvidenceSnapshotCatalog(temp);var collector=RuleAnalyzers.collector(m,request);
        var published=pipeline().run(catalog,Optional.empty(),s,List.of(pe,replacement),Map.of(),List.of(collector),ref(ScientificReference.Kind.ACTIVITY,"i16-pin-failure"),T);
        var history=catalog.read(published.catalogSnapshot()).orElseThrow().history();
        assertEquals(pe,history.envelopes().get(pe.reference()));assertEquals(replacement,history.envelopes().get(replacement.reference()));
        assertTrue(history.interpretations().values().stream().anyMatch(i->i.evaluator().equals(collector.method())&&i.status()==EvidenceInterpretation.Status.FAILED));
    }
    @Test void truncatedPairScopeNeverSuppliesExhaustiveMinimum()throws Exception {
        var s=state(new double[][]{{0,0,0},{9,0,0},{1,0,0}});var p=pairPlan(s);matrix(p,"GROUP_MINIMUM","a","b");
        var r=run(s,p,radii(s,p,1),100,1);near(9,value(r,1,"observedMinimum"));assertTrue(value(r,1,"completeMinimum").isNull());
        for(var op:r.get("operations")){var c=op.get("coverage");assertEquals(2,c.get("requestedPairs").asInt());assertEquals(1,c.get("evaluatedPairs").asInt());assertFalse(c.get("completeEnumeration").asBoolean());assertTrue(c.get("budgetExceeded").asBoolean());}
        assertFalse(r.toString().contains("ABSENT_FALSE"));
    }
    @Test void noGlobalHydrogenExclusionOrRadiusFallback()throws Exception {
        var s=system(List.of(methanol()),true,false);var p=plan(s);group(p,"all",s.atoms().keySet().toArray(totah.lab.gaia.structure.AtomReference[]::new));var op=matrix(p,"PAIR_MATRIX","all","all");
        assertEquals(3,value(run(s,p),0,"pairs").size());op.put("hydrogenScope","HEAVY_ONLY");var rows=value(run(s,p),0,"pairs");assertEquals(1,rows.size());assertTrue(rows.get(0).get("gapAngstrom").isNull());assertTrue(rows.get(0).get("firstRadius").isNull());
    }
    public static void main(String[] args)throws Exception {
        var reports=new ArrayList<JsonNode>();for(var status:List.of("MISSING","UNKNOWN_INCONCLUSIVE","UNSUPPORTED","FAILED","NOT_EVALUATED"))reports.add(mixed(status));
        Files.write(Path.of(args[0]),SystemStateView.bytes(reports));
    }
}
