package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.gaia.geometry.*;
import totah.lab.gaia.structure.*;
import totah.lab.gaia.graph.ResidueGraph;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.envelope;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.JSON;


import static totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest.*;

/** Opt-in centroid preservation, never plane qualification from centroid availability. */
class ContinuousGeometryV2AcceptanceTest {
    static RuleManifest v2() throws Exception {
        var n=(ObjectNode)JSON.valueToTree(manifest());
        n.put("version","2");n.put("implementationVersion","2");n.put("profile","ATHENA_CONTINUOUS_GEOMETRY_V2");
        var gate=Path.of("software/qualification/p06-centroid-characterization-20261005/REVIEW_GATE.txt");
        var source=new RuleManifest.Source(gate.toString(),EvidenceExchange.sha256(Files.readAllBytes(gate)),"User-approved opt-in centroid/plane separation");
        ((ArrayNode)n.get("referenceArtifacts")).add(node(source));
        return JSON.treeToValue(n,RuleManifest.class);
    }
    static JsonNode runV2(SystemStateView s,ObjectNode p,EvidenceEnvelope radius,int nodes,int pairs) throws Exception {
        var m=v2(); new RuleRegistry().register(m);var r=request(s,m,p,nodes,pairs);var inputs=new ArrayList<EvidenceEnvelope>();inputs.add(envelope(s,"athena:continuous-geometry-plan",p));if(radius!=null)inputs.add(radius);
        byte[] before=SystemStateView.bytes(s.snapshot());var collector=RuleAnalyzers.collector(m,r);
        var report=JSON.readTree(collector.analyze(s,inputs,Map.of()).getFirst().measurements().get("payload"));
        var e=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"geometry-fixture"),"result","athena:rule-measurements",SystemStateView.bytes(report),collector.method(),s.subject(),T,List.of());
        var exchange=new EvidenceExchange();inputs.add((EvidenceEnvelope)exchange.decodeRecord(exchange.encodeRecord(e)));
        var evaluated=RuleAnalyzers.evaluator(m,r).analyze(s,inputs,Map.of()).getFirst();assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluated.status());
        assertEquals(report,JSON.readTree(evaluated.measurements().get("payload")));assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));return report;
    }

    static ObjectNode plane(SystemStateView s) {
        var p=plan(s);group(p,"selection",s.atoms().keySet().stream().sorted().toArray(AtomReference[]::new));
        op(p,"PLANE").put("groupId","selection");return p;
    }
    static JsonNode report(double[][] xyz)throws Exception {
        var s=state(xyz);return runV2(s,plane(s),null,100,100);
    }
    @TestFactory Stream<DynamicTest> centroidWithoutPlane() {
        return Stream.of(new double[][]{{0,0,0},{2,0,0},{4,0,0}},new double[][]{{2,0,0},{2,0,0},{2,0,0}},
                new double[][]{{2,0,0}},new double[][]{{0,0,0},{4,0,0}}).map(xyz->DynamicTest.dynamicTest(Arrays.deepToString(xyz),()->{
            var r=report(xyz);var o=r.path("operations").get(0);var q=o.path("quantities");
            near(2,value(r,0,"centroidAngstrom").get(0));
            assertEquals("SUPPORTED_PRESENT",q.path("centroidAngstrom").path("status").asText());
            assertEquals("UNKNOWN_INCONCLUSIVE",q.path("measurement").path("status").asText());
            assertFalse(o.path("coverage").path("completeEnumeration").asBoolean());
            assertEquals("UNKNOWN_INCONCLUSIVE",o.path("coverage").path("normalUniquenessStatus").asText());
            assertEquals(Set.of("centroidAngstrom","measurement"),new HashSet<>(q.properties().stream().map(Map.Entry::getKey).toList()));
            assertEquals("2",r.path("implementation").path("version").asText());
        }));
    }
    @Test void ordinaryPlaneAndOtherMeasurementsUnchanged()throws Exception {
        var s=state(new double[][]{{0,0,0},{3,0,0},{0,3,0}});var p=plane(s);
        op(p,"DISTANCE",ref(1,"X0"),ref(1,"X1"));
        assertEquals(run(s,p).get("operations"),runV2(s,p,null,100,100).get("operations"));
    }
    @Test void budgetDoesNotEmitPartialCentroid()throws Exception {
        var s=state(new double[][]{{0,0,0},{2,0,0},{4,0,0}});
        var r=runV2(s,plane(s),null,2,100);
        assertFalse(r.path("operations").get(0).path("quantities").has("centroidAngstrom"));
        assertTrue(r.path("operations").get(0).path("coverage").path("budgetExceeded").asBoolean());
    }
    @Test void missingAtomAndEmptySelectionFailBeforeMeasurement()throws Exception {
        var s=state(new double[][]{{0,0,0}});var p=plane(s);
        ((ArrayNode)p.path("groups").get(0).get("atoms")).add(node(ref(1,"ZZ_MISSING")));
        assertThrows(IllegalArgumentException.class,()->runV2(s,p,null,100,100));
        ((ArrayNode)p.path("groups").get(0).get("atoms")).removeAll();
        assertThrows(IllegalArgumentException.class,()->runV2(s,p,null,100,100));
    }
    @Test void unqualifiedFrameRetainsInconclusiveCentroid()throws Exception {
        var b=state(new double[][]{{0,0,0},{2,0,0},{4,0,0}});
        var s=new SystemStateView(b.identity(),b.graph(),b.components(),b.sources(),b.cofactors(),b.charges(),false,true,b.limitations());
        var r=runV2(s,plane(s),null,100,100);
        near(2,value(r,0,"centroidAngstrom").get(0));
        assertEquals("UNKNOWN_INCONCLUSIVE",r.path("operations").get(0).path("quantities").path("centroidAngstrom").path("status").asText());
    }
    @Test void permutationsAndTranslation()throws Exception {
        var a=report(new double[][]{{0,0,0},{2,0,0},{4,0,0}});
        var b=report(new double[][]{{4,0,0},{0,0,0},{2,0,0}});
        assertEquals(a.path("operations").get(0).path("quantities"),b.path("operations").get(0).path("quantities"));
        var c=report(new double[][]{{10,20,30},{12,20,30},{14,20,30}});
        near(12,value(c,0,"centroidAngstrom").get(0));near(20,value(c,0,"centroidAngstrom").get(1));near(30,value(c,0,"centroidAngstrom").get(2));
    }
    @Test void incompleteCoordinatesCannotBecomePartialSelection() {
        assertThrows(RuntimeException.class,()->state(new double[][]{{0,0,0},{Double.NaN,0,0},{4,0,0}}));
    }
    @Test void profileAndImplementationMustAgree()throws Exception {
        var n=(ObjectNode)JSON.valueToTree(v2());n.put("implementationVersion","1");
        assertThrows(IllegalArgumentException.class,()->new RuleRegistry().register(JSON.treeToValue(n,RuleManifest.class)));
    }
    public static void main(String[] args)throws Exception {
        if(args.length>1)Files.write(Path.of(args[1]),SystemStateView.bytes(v2()));
        Files.write(Path.of(args[0]),SystemStateView.bytes(report(new double[][]{{0,0,0},{2,0,0},{4,0,0}})));
    }
}
