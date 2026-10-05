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

/** Raw mixed geometry, not chemical donor/ring or interaction classification. */
class ContinuousGeometryV3AcceptanceTest {
    static RuleManifest v3() throws Exception {
        var n=(ObjectNode)JSON.valueToTree(ContinuousGeometryV2AcceptanceTest.v2());
        n.put("version","3");n.put("implementationVersion","3");n.put("profile","ATHENA_CONTINUOUS_GEOMETRY_V3");
        var path=Path.of("software/qualification/b05-mixed-geometry-contract-20261005/DESIGN.txt");
        ((ArrayNode)n.get("referenceArtifacts")).add(node(new RuleManifest.Source(path.toString(),EvidenceExchange.sha256(Files.readAllBytes(path)),"Approved POINT_PAIR_GROUP definition")));
        return JSON.treeToValue(n,RuleManifest.class);
    }
    static JsonNode runV3(SystemStateView s,ObjectNode p,EvidenceEnvelope radius,int nodes,int pairs) throws Exception {
        var m=v3(); new RuleRegistry().register(m);var r=request(s,m,p,nodes,pairs);var inputs=new ArrayList<EvidenceEnvelope>();inputs.add(envelope(s,"athena:continuous-geometry-plan",p));if(radius!=null)inputs.add(radius);
        byte[] before=SystemStateView.bytes(s.snapshot());var collector=RuleAnalyzers.collector(m,r);
        var report=JSON.readTree(collector.analyze(s,inputs,Map.of()).getFirst().measurements().get("payload"));
        var e=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"geometry-fixture"),"result","athena:rule-measurements",SystemStateView.bytes(report),collector.method(),s.subject(),T,List.of());
        var exchange=new EvidenceExchange();inputs.add((EvidenceEnvelope)exchange.decodeRecord(exchange.encodeRecord(e)));
        var evaluated=RuleAnalyzers.evaluator(m,r).analyze(s,inputs,Map.of()).getFirst();assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluated.status());
        assertEquals(report,JSON.readTree(evaluated.measurements().get("payload")));assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));return report;
    }


    static double[][] face() {return new double[][]{{1,0,0},{0,1,0},{-1,-1,0},{0,0,3},{0,0,2}};}
    static ObjectNode mixed(SystemStateView s) {
        var p=plan(s);group(p,"arbitrary-group",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"));
        op(p,"POINT_PAIR_GROUP",ref(1,"X3"),ref(1,"X4")).put("groupId","arbitrary-group");return p;
    }
    static JsonNode report(double[][] xyz)throws Exception {var s=state(xyz);return runV3(s,mixed(s),null,100,100);}
    static JsonNode q(JsonNode r){return r.path("operations").get(0).path("quantities");}
    static void unavailable(JsonNode r,String name){assertEquals("UNKNOWN_INCONCLUSIVE",q(r).path(name).path("status").asText());assertTrue(q(r).path(name).path("value").isNull());assertFalse(r.path("operations").get(0).path("coverage").path("completeEnumeration").asBoolean());}
    @Test void analyticFaceAndChemicallyAnonymousPoints()throws Exception {
        var r=report(face());near(3,value(r,0,"firstCentroidDistanceAngstrom"));near(2,value(r,0,"secondCentroidDistanceAngstrom"));
        near(180,value(r,0,"firstSecondCentroidAngleDegrees"));near(0,value(r,0,"secondCentroidNormalAngleDegrees"));
        assertEquals(5,q(r).size());assertEquals("3",r.path("implementation").path("version").asText());
        assertTrue(r.path("operations").get(0).path("coverage").path("completeEnumeration").asBoolean());
        assertTrue(RuleAnalyzers.collector(v3(),request(state(face()),v3(),mixed(state(face())),100,100)).qualifies().isEmpty());
    }
    @Test void tupleReversalChangesVertexAndSwapsDistances()throws Exception {
        var s=state(face());var p=mixed(s);((ObjectNode)p.path("operations").get(0)).set("atoms",node(List.of(ref(1,"X4"),ref(1,"X3"))));
        var r=runV3(s,p,null,100,100);near(2,value(r,0,"firstCentroidDistanceAngstrom"));near(3,value(r,0,"secondCentroidDistanceAngstrom"));near(0,value(r,0,"firstSecondCentroidAngleDegrees"));
    }
    @Test void equalFirstDistanceDifferentDirectionAndHydrogenCoordinateRotation()throws Exception {
        var xyz=face();xyz[4]=new double[]{1,0,3};var r=report(xyz);
        near(3,value(r,0,"firstCentroidDistanceAngstrom"));near(Math.sqrt(10),value(r,0,"secondCentroidDistanceAngstrom"));
        near(Math.toDegrees(Math.acos(1/Math.sqrt(10))),value(r,0,"firstSecondCentroidAngleDegrees"));
        near(Math.toDegrees(Math.acos(3/Math.sqrt(10))),value(r,0,"secondCentroidNormalAngleDegrees"));
        // Make Q explicitly hydrogen in the supplied state; geometry must not change or assert a C-H bond.
        var atoms=new ArrayList<MolecularGraph.Atom>();for(int i=0;i<xyz.length;i++)atoms.add(atom("X"+i,i==4?"H":"C",0,false,xyz[i][0],xyz[i][1],xyz[i][2]));
        var s=system(List.of(new MolecularGraph(atoms,List.of(),Map.of())),true,false);
        assertEquals(q(r),q(runV3(s,mixed(s),null,100,100)));
    }
    @Test void rigidRotationTranslationAndPermutation()throws Exception {
        var expected=q(report(face()));var xyz=face();for(var p:xyz){double x=p[0],y=p[1],z=p[2];p[0]=z+10;p[1]=x+20;p[2]=y+30;}
        var r=report(xyz);for(String k:List.of("firstCentroidDistanceAngstrom","secondCentroidDistanceAngstrom","firstSecondCentroidAngleDegrees","secondCentroidNormalAngleDegrees"))near(Double.parseDouble(expected.path(k).path("value").asText()),value(r,0,k));
        xyz=face();var first=xyz[0];xyz[0]=xyz[2];xyz[2]=first;assertEquals(expected,q(report(xyz)));
    }
    @Test void normalSignInvarianceAcrossPlaneOrientation()throws Exception {
        var xyz=face();xyz[4]=new double[]{1,0,2};var a=report(xyz);
        for(var p:xyz)p[2]=-p[2];var b=report(xyz);
        assertEquals(value(a,0,"secondCentroidNormalAngleDegrees"),value(b,0,"secondCentroidNormalAngleDegrees"));
    }
    @TestFactory Stream<DynamicTest> degeneracyDoesNotEraseIndependentTruth(){return Stream.of("collinear","coincident","pq","qg").map(kind->DynamicTest.dynamicTest(kind,()->{
        var xyz=face();if(kind.equals("collinear")){xyz[0]=new double[]{-1,0,0};xyz[1]=new double[]{0,0,0};xyz[2]=new double[]{1,0,0};}
        if(kind.equals("coincident"))for(int i=0;i<3;i++)xyz[i]=new double[]{0,0,0};
        if(kind.equals("pq"))xyz[4]=xyz[3].clone();if(kind.equals("qg"))xyz[4]=new double[]{0,0,0};
        var r=report(xyz);assertEquals("SUPPORTED_PRESENT",q(r).path("centroidAngstrom").path("status").asText());
        if(kind.equals("pq")||kind.equals("qg"))unavailable(r,"firstSecondCentroidAngleDegrees");else near(180,value(r,0,"firstSecondCentroidAngleDegrees"));
        if(!kind.equals("pq"))unavailable(r,"secondCentroidNormalAngleDegrees");else near(0,value(r,0,"secondCentroidNormalAngleDegrees"));
    }));}
    @Test void nonuniqueNormalHasNoAngle()throws Exception {
        var s=state(new double[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1},{0,0,3},{0,0,2}});
        var p=plan(s);group(p,"sphere",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"),ref(1,"X3"),ref(1,"X4"),ref(1,"X5"));op(p,"POINT_PAIR_GROUP",ref(1,"X6"),ref(1,"X7")).put("groupId","sphere");
        unavailable(runV3(s,p,null,100,100),"secondCentroidNormalAngleDegrees");
    }
    @Test void budgetAndFrame()throws Exception {
        var s=state(face());var r=runV3(s,mixed(s),null,4,100);assertFalse(q(r).has("centroidAngstrom"));
        var u=new SystemStateView(s.identity(),s.graph(),s.components(),s.sources(),s.cofactors(),s.charges(),false,true,s.limitations());
        r=runV3(u,mixed(u),null,100,100);q(r).forEach(v->assertEquals("UNKNOWN_INCONCLUSIVE",v.path("status").asText()));
    }
    @TestFactory Stream<DynamicTest> invalidScope(){return Stream.of("missing","duplicate","empty","extra").map(kind->DynamicTest.dynamicTest(kind,()->{
        var s=state(face());var p=mixed(s);var o=(ObjectNode)p.path("operations").get(0);
        switch(kind){case "missing"->o.set("atoms",node(List.of(ref(1,"X3"),ref(1,"MISSING"))));case "duplicate"->o.set("atoms",node(List.of(ref(1,"X3"),ref(1,"X3"))));case "empty"->((ArrayNode)p.path("groups").get(0).path("atoms")).removeAll();case "extra"->o.put("fakeCentroid",true);}
        assertThrows(IllegalArgumentException.class,()->runV3(s,p,null,100,100));
    }));}
    @Test void olderVersionsKeepUnknownOperationUnsupported()throws Exception {
        var s=state(face());var p=mixed(s);
        for(var m:List.of(manifest(),ContinuousGeometryV2AcceptanceTest.v2())){
            // Older implementations include only recognized plan selections, not this unknown tuple.
            var r=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(ref(1,"X0"),ref(1,"X1"),ref(1,"X2")),List.of(),List.of(),0.1,0,100,100);
            var f=RuleAnalyzers.collector(m,r).analyze(s,List.of(envelope(s,"athena:continuous-geometry-plan",p)),Map.of()).getFirst();
            assertEquals("UNSUPPORTED",q(JSON.readTree(f.measurements().get("payload"))).path("measurement").path("status").asText());
        }
    }
    @Test void unknownV3TagAndExistingOperations()throws Exception {
        var s=state(face());var p=mixed(s);((ObjectNode)p.path("operations").get(0)).put("kind","FUTURE");
        // Keep both tuple atoms in a known explicit group to preserve exact request coverage.
        group(p,"other",ref(1,"X3"),ref(1,"X4"));assertEquals("UNSUPPORTED",q(runV3(s,p,null,100,100)).path("measurement").path("status").asText());
        p=ContinuousGeometryV2AcceptanceTest.plane(s);assertEquals(ContinuousGeometryV2AcceptanceTest.runV2(s,p,null,100,100).path("operations"),runV3(s,p,null,100,100).path("operations"));
    }
    @Test void evaluatorRejectsTamperedMeasurement()throws Exception {
        var s=state(face());var p=mixed(s);var m=v3();var r=request(s,m,p,100,100);var report=(ObjectNode)runV3(s,p,null,100,100);
        ((ObjectNode)q(report).path("firstCentroidDistanceAngstrom")).put("value","999");
        var e=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"geometry-fixture"),"result","athena:rule-measurements",SystemStateView.bytes(report),RuleAnalyzers.collector(m,r).method(),s.subject(),T,List.of());
        assertThrows(IllegalArgumentException.class,()->RuleAnalyzers.evaluator(m,r).analyze(s,List.of(envelope(s,"athena:continuous-geometry-plan",p),e),Map.of()));
    }
    public static void main(String[] args)throws Exception{Files.write(Path.of(args[0]),SystemStateView.bytes(report(face())));}
}
