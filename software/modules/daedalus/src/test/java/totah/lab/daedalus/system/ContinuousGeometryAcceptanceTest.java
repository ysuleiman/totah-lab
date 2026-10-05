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


/** Raw geometry witnesses; never biological or interaction training labels. */
class ContinuousGeometryAcceptanceTest {
    @TempDir Path temp;
    static JsonNode node(Object value){return JSON.valueToTree(value); }
    static RuleManifest manifest() throws Exception {
        var source = new RuleManifest.Source("software/qualification/continuous-geometry-contract-20261005/DESIGN.txt",
                EvidenceExchange.sha256(Files.readAllBytes(Path.of("software/qualification/continuous-geometry-contract-20261005/DESIGN.txt"))), "Approved continuous geometry contract");
        return new RuleManifest("athena-rule/2", "ATHENA.GEOMETRY.CONTINUOUS", "1", "ATHENA_CONTINUOUS_GEOMETRY_V1",
                RuleManifest.Family.ENVIRONMENT, "GEOMETRY", "athena.geometry", "1", SystemGraphCertificate.Status.NOT_EVALUATED,
                false, List.of(), List.of("explicit atoms; attributed radii only"), List.of("explicit complete source frame"),
                Map.of("geometry", "athena-continuous-geometry/1"), List.of(EvidenceInterpretation.Status.values()),
                Map.of("planeNormalRelativeGapTolerance", new RuleManifest.Parameter("1.0E-10", "dimensionless", "Numerical eigengap guard; not empirical planarity")),
                List.of(source), List.of(source), List.of("raw measurements only; no interaction, energy or potency assertion"),
                new RuleManifest.NegativeCoverage("1", "raw explicit geometry", List.of("COMPLETE_EXPLICIT_SCOPE", "QUALIFIED_FRAME", "FINITE_COORDINATES"), "No continuous quantity establishes ABSENT_FALSE", "UNKNOWN_INCONCLUSIVE"));
    }
    static SystemStateView state(double[][] xyz) {
        var atoms = new ArrayList<MolecularGraph.Atom>(); for (int i=0;i<xyz.length;i++) atoms.add(atom("X"+i,"C",0,false,xyz[i][0],xyz[i][1],xyz[i][2]));
        return system(List.of(new MolecularGraph(atoms,List.of(),Map.of())),true,false);
    }
    static ObjectNode plan(SystemStateView s) {
        var p=JSON.createObjectNode();p.put("schema","athena-continuous-geometry-plan/1");p.set("stateBinding",node(s.binding()));p.put("coordinateUnit","ANGSTROM");
        p.set("coordinateSourceReferences",node(s.sources()));p.putArray("groups");p.putArray("operations");p.putNull("radiusAssignmentReference");p.set("sourceReferences",node(s.sources()));p.putArray("limitations").add("synthetic engineering fixture");return p;
    }
    static void group(ObjectNode p,String id,AtomReference... atoms) {var g=((ArrayNode)p.get("groups")).addObject();g.put("id",id);g.set("atoms",node(Arrays.stream(atoms).sorted().toList()));g.set("sourceReferences",p.get("sourceReferences"));}
    static ObjectNode op(ObjectNode p,String kind,AtomReference... atoms) {var o=((ArrayNode)p.get("operations")).addObject();o.put("id","op"+p.get("operations").size());o.put("kind",kind);if(atoms.length>0)o.set("atoms",node(List.of(atoms)));return o;}
    static RuleRequest request(SystemStateView s,RuleManifest m,ObjectNode p,int nodes,int pairs) throws Exception {
        var selected=new TreeSet<AtomReference>();for(var g:p.get("groups"))for(var a:g.get("atoms"))selected.add(JSON.treeToValue(a,AtomReference.class));
        for(var o:p.get("operations")){if(o.has("atoms"))for(var a:o.get("atoms"))selected.add(JSON.treeToValue(a,AtomReference.class));if(o.has("atom"))selected.add(JSON.treeToValue(o.get("atom"),AtomReference.class));}
        return new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.copyOf(selected),List.of(),List.of(),0.1,0,nodes,pairs);
    }
    static JsonNode run(SystemStateView s,ObjectNode p,EvidenceEnvelope radius,int nodes,int pairs) throws Exception {
        var m=manifest(); new RuleRegistry().register(m);var r=request(s,m,p,nodes,pairs);var inputs=new ArrayList<EvidenceEnvelope>();inputs.add(envelope(s,"athena:continuous-geometry-plan",p));if(radius!=null)inputs.add(radius);
        byte[] before=SystemStateView.bytes(s.snapshot());var collector=RuleAnalyzers.collector(m,r);
        var report=JSON.readTree(collector.analyze(s,inputs,Map.of()).getFirst().measurements().get("payload"));
        var e=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"geometry-fixture"),"result","athena:rule-measurements",SystemStateView.bytes(report),collector.method(),s.subject(),T,List.of());
        var exchange=new EvidenceExchange();inputs.add((EvidenceEnvelope)exchange.decodeRecord(exchange.encodeRecord(e)));
        var evaluated=RuleAnalyzers.evaluator(m,r).analyze(s,inputs,Map.of()).getFirst();assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluated.status());
        assertEquals(report,JSON.readTree(evaluated.measurements().get("payload")));assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));return report;
    }
    static JsonNode run(SystemStateView s,ObjectNode p)throws Exception{return run(s,p,null,1000,1000);}
    static JsonNode value(JsonNode report,int i,String name){return report.get("operations").get(i).get("quantities").get(name).get("value");}
    static void near(double n,JsonNode v){assertEquals(n,Double.parseDouble(v.asText()),1e-10);}
    static ObjectNode matrix(ObjectNode p,String kind,String a,String b){var o=op(p,kind);o.put("firstGroupId",a);o.put("secondGroupId",b);o.put("hydrogenScope","ALL_EXPLICIT");if(kind.equals("PAIR_MATRIX"))o.put("topologyPolicy","FLAG_BONDED_AND_1_3");return o;}
    static EvidenceEnvelope radii(SystemStateView s,ObjectNode p,double value){var root=JSON.createObjectNode();root.put("schema","athena-radius-assignments/1");root.set("stateBinding",node(s.binding()));root.put("unit","ANGSTROM");var model=root.putObject("model");model.put("id","synthetic-explicit-radii");model.put("version","1");model.set("sourceReferences",node(s.sources()));var assignments=root.putArray("assignments");for(var a:new TreeSet<>(s.atoms().keySet())){var v=assignments.addObject();v.set("atom",node(a));v.put("status","SUPPORTED_PRESENT");v.put("radius",Double.toString(value));v.set("sourceReferences",node(s.sources()));v.putArray("reasons").add("hand-specified arithmetic oracle");}root.set("sourceReferences",node(s.sources()));root.putArray("limitations").add("not a scientific radius model");var e=envelope(s,"athena:radius-assignments",root);p.set("radiusAssignmentReference",node(Map.of("reference",e.reference(),"payloadSha256",e.payloadSha256())));return e;}

    @Test void orderedTuplesAndContinuousNoncontacts()throws Exception{
        var s=state(new double[][]{{0,0,0},{3,0,0},{3,4,0},{3,4,5},{30,0,0}});var p=plan(s);
        op(p,"DISTANCE",ref(1,"X0"),ref(1,"X2"));op(p,"ANGLE",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"));op(p,"DIHEDRAL",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"),ref(1,"X3"));op(p,"VECTOR",ref(1,"X0"),ref(1,"X4"));
        var r=run(s,p);near(5,value(r,0,"distanceAngstrom"));near(90,value(r,1,"angleDegrees"));near(90,value(r,2,"torsionDegrees"));near(30,value(r,3,"lengthAngstrom"));assertEquals("1.0",value(r,3,"unitVector").get(0).asText());
    }
    @Test void gapsOverlapsNoncontactsAndRadiiAbsent()throws Exception{
        var s=state(new double[][]{{0,0,0},{2,0,0},{4,0,0},{30,0,0}});var p=plan(s);group(p,"a",ref(1,"X0"));group(p,"b",ref(1,"X1"),ref(1,"X2"),ref(1,"X3"));matrix(p,"PAIR_MATRIX","a","b");
        var raw=run(s,p);var rows=value(raw,0,"pairs");assertEquals(3,rows.size());assertTrue(rows.get(0).get("gapAngstrom").isNull());
        var r=run(s,p,radii(s,p,2),100,100);rows=value(r,0,"pairs");near(-2,rows.get(0).get("gapAngstrom"));near(0,rows.get(1).get("gapAngstrom"));near(26,rows.get(2).get("gapAngstrom"));near(-26,rows.get(2).get("overlapAngstrom"));
    }
    @Test void overlappingGroupsSelfPairsAndTiedMinimum()throws Exception{
        var s=state(new double[][]{{-1,0,0},{0,0,0},{1,0,0}});var p=plan(s);group(p,"a",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"));matrix(p,"PAIR_MATRIX","a","a");matrix(p,"GROUP_MINIMUM","a","a");
        var r=run(s,p);var rows=value(r,0,"pairs");assertEquals(3,rows.size());for(var row:rows)assertEquals(2,row.get("groupMemberships").size());assertEquals(3,r.get("operations").get(0).get("coverage").get("omittedSelfPairs").asInt());near(1,value(r,1,"completeMinimum"));assertEquals(2,value(r,1,"tiedPairs").size());
        var partial=run(s,p,null,100,1);assertTrue(value(partial,1,"completeMinimum").isNull());near(1,value(partial,1,"observedMinimum"));assertFalse(partial.get("operations").get(1).get("coverage").get("completeEnumeration").asBoolean());
    }
    @Test void explicitHydrogenAndBondFlags()throws Exception{
        var s=system(List.of(methanol()),true,false);var p=plan(s);group(p,"a",s.atoms().keySet().toArray(AtomReference[]::new));var o=matrix(p,"PAIR_MATRIX","a","a");var r=run(s,p);var flags=value(r,0,"pairs").toString();assertTrue(flags.contains("BONDED"));assertTrue(flags.contains("ONE_THREE"));o.put("hydrogenScope","HEAVY_ONLY");var heavy=run(s,p);assertEquals(1,value(heavy,0,"pairs").size());assertEquals(4,heavy.get("operations").get(0).get("coverage").get("omittedHydrogenPairs").asInt());
    }
    @Test void unknownConnectivityIsNotNonbonded()throws Exception{
        var original=system(List.of(methanol()),true,false);var s=new SystemStateView(original.identity(),ResidueGraph.from(new Structure(original.graph().structure().getChains())),original.components(),original.sources(),original.cofactors(),original.charges(),true,true,original.limitations());var p=plan(s);group(p,"a",s.atoms().keySet().toArray(AtomReference[]::new));matrix(p,"PAIR_MATRIX","a","a");for(var row:value(run(s,p),0,"pairs"))assertEquals("UNKNOWN_INCONCLUSIVE",row.get("topologyStatus").asText());
    }
    @Test void zeroVectorAndCollinearTorsionAreNonnegative()throws Exception{
        var s=state(new double[][]{{0,0,0},{0,0,0},{1,0,0},{2,0,0}});var p=plan(s);op(p,"VECTOR",ref(1,"X0"),ref(1,"X1"));op(p,"DIHEDRAL",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"),ref(1,"X3"));var r=run(s,p);near(0,value(r,0,"lengthAngstrom"));assertTrue(value(r,0,"unitVector").isNull());assertFalse(r.toString().contains("ABSENT_FALSE"));assertEquals("UNKNOWN_INCONCLUSIVE",r.get("operations").get(1).get("quantities").get("measurement").get("status").asText());
    }
    @Test void planesResidualsAndBothOffsets()throws Exception{
        var s=state(new double[][]{{-1,-1,0},{1,-1,0},{1,1,0},{-1,1,0},{0,0,3},{2,0,3},{2,2,3},{0,2,3}});var p=plan(s);group(p,"a",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"),ref(1,"X3"));group(p,"b",ref(1,"X4"),ref(1,"X5"),ref(1,"X6"),ref(1,"X7"));op(p,"PLANE").put("groupId","a");var point=op(p,"POINT_PLANE");point.set("atom",node(ref(1,"X6")));point.put("planeGroupId","a");var pair=op(p,"PLANE_PAIR");pair.put("firstGroupId","a");pair.put("secondGroupId","b");var r=run(s,p);near(0,value(r,0,"rmsDistanceAngstrom"));assertEquals(List.of("0.0","4.0","4.0"),JSON.convertValue(value(r,0,"covarianceEigenvaluesAngstrom2"),List.class));near(3,value(r,1,"perpendicularDistanceAngstrom"));near(Math.sqrt(8),value(r,1,"inPlaneOffsetAngstrom"));near(0,value(r,2,"unsignedNormalAngleDegrees"));near(Math.sqrt(2),value(r,2,"secondOntoFirstOffsetAngstrom"));near(Math.sqrt(2),value(r,2,"firstOntoSecondOffsetAngstrom"));
    }
    @Test void isotropicNormalCannotCertifyDirectionalGeometry()throws Exception{
        var s=state(new double[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}});var p=plan(s);group(p,"a",s.atoms().keySet().toArray(AtomReference[]::new));op(p,"PLANE").put("groupId","a");var point=op(p,"POINT_PLANE");point.set("atom",node(ref(1,"X0")));point.put("planeGroupId","a");var r=run(s,p);near(0,value(r,0,"smallestEigenvalueGapRatio"));near(Math.sqrt(1.0/3),value(r,0,"rmsDistanceAngstrom"));assertEquals("UNKNOWN_INCONCLUSIVE",r.get("operations").get(0).get("coverage").get("normalUniquenessStatus").asText());assertTrue(value(r,1,"perpendicularDistanceAngstrom").isNull());
    }
    @TestFactory Stream<DynamicTest> inputFailures(){return Stream.of("binding","extra","unit","duplicate","missingAtom","radiiPin","noncanonicalRadius").map(kind->DynamicTest.dynamicTest(kind,()->{
        var s=state(new double[][]{{0,0,0},{1,0,0}});var p=plan(s);op(p,"DISTANCE",ref(1,"X0"),ref(1,"X1"));EvidenceEnvelope radius=null;
        switch(kind){case "binding"->p.putObject("stateBinding");case "extra"->p.put("invented",true);case "unit"->p.put("coordinateUnit","NANOMETER");case "duplicate"->((ArrayNode)p.get("operations")).add(p.get("operations").get(0));case "missingAtom"->((ArrayNode)p.get("operations").get(0).get("atoms")).set(0,node(ref(1,"MISSING")));case "radiiPin"->{radius=radii(s,p,1);((ObjectNode)p.get("radiusAssignmentReference")).put("payloadSha256","0".repeat(64));}case "noncanonicalRadius"->{var e=radii(s,p,1);var payload=(ObjectNode)JSON.readTree(e.readPayload());((ObjectNode)payload.get("assignments").get(0)).put("radius","NaN");radius=envelope(s,"athena:radius-assignments",payload);p.set("radiusAssignmentReference",node(Map.of("reference",radius.reference(),"payloadSha256",radius.payloadSha256())));}}
        var selected=radius;assertThrows(Exception.class,()->run(s,p,selected,100,100));
    }));}
    @Test void unknownOperationSurvivesAndNodeBudgetDoesNotClaimAbsence()throws Exception{var s=state(new double[][]{{0,0,0},{1,0,0}});var p=plan(s);op(p,"FUTURE").put("uninterpreted","preserve me");var r=run(s,p);assertEquals("preserve me",r.get("plan").get("operations").get(0).get("uninterpreted").asText());assertEquals("UNSUPPORTED",r.get("operations").get(0).get("quantities").get("measurement").get("status").asText());op(p,"DISTANCE",ref(1,"X0"),ref(1,"X1"));r=run(s,p,null,1,100);assertTrue(r.get("operations").get(1).get("coverage").get("budgetExceeded").asBoolean());assertFalse(r.toString().contains("ABSENT_FALSE"));}
    @Test void frameUnqualifiedRetainsNumbersWithoutQualification()throws Exception{var base=state(new double[][]{{0,0,0},{5,0,0}});var s=new SystemStateView(base.identity(),base.graph(),base.components(),base.sources(),base.cofactors(),base.charges(),false,true,base.limitations());var p=plan(s);op(p,"DISTANCE",ref(1,"X0"),ref(1,"X1"));var r=run(s,p);near(5,value(r,0,"distanceAngstrom"));assertEquals("UNKNOWN_INCONCLUSIVE",r.get("operations").get(0).get("quantities").get("distanceAngstrom").get("status").asText());}
    @Test void diagnosticsDefensiveAndLegacyResultsIdentical(){var points=List.of(new Point3D(0,0,0),new Point3D(2,0,0),new Point3D(0,2,0));var old=Plane3D.fit(points);var d=Plane3D.fitWithDiagnostics(points);assertEquals(old.centroid(),d.plane().centroid());assertEquals(old.normal(),d.plane().normal());assertThrows(UnsupportedOperationException.class,()->d.covarianceEigenvalues().add(1.0));assertThrows(IllegalArgumentException.class,()->Plane3D.fitWithDiagnostics(List.of(new Point3D(0,0,0),new Point3D(1,0,0),new Point3D(2,0,0))));}

    @Test void rotationsTranslationsAndPermutationsPreserveMeasurements()throws Exception {
        double[][] xyz={{0,0,0},{3,0,0},{3,4,0},{3,4,5}};
        JsonNode baseline=null;
        for(int variant=0;variant<3;variant++) {
            var aa=new ArrayList<MolecularGraph.Atom>();
            for(int i=0;i<xyz.length;i++) {var p=xyz[i];aa.add(atom("X"+i,"C",0,false,variant==0?p[0]:-p[1]+10,variant==0?p[1]:p[0]-7,variant==0?p[2]:p[2]+3));}
            if(variant==2)Collections.reverse(aa);
            var state=system(List.of(new MolecularGraph(aa,List.of(),Map.of())),true,false);var plan=plan(state);
            op(plan,"DISTANCE",ref(1,"X0"),ref(1,"X2"));op(plan,"DIHEDRAL",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"),ref(1,"X3"));
            group(plan,"plane",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"));op(plan,"PLANE").put("groupId","plane");
            var report=run(state,plan);
            if(baseline==null)baseline=report;else{assertEquals(value(baseline,0,"distanceAngstrom"),value(report,0,"distanceAngstrom"));assertEquals(value(baseline,1,"torsionDegrees"),value(report,1,"torsionDegrees"));near(Double.parseDouble(value(baseline,2,"rmsDistanceAngstrom").asText()),value(report,2,"rmsDistanceAngstrom"));}
        }
    }
    @Test void fullFrameCofactorAndFarEnvironmentRemainExplicit()throws Exception {
        var original=system(List.of(methane(0),methane(3),methane(30)),true,false);
        var residues=original.graph().structure().getChains().getFirst().residues();
        var renamed=List.of(new Residue("LIG",1,residues.get(0).getAtoms()),new Residue("SAM",2,residues.get(1).getAtoms()),new Residue("CYS",203,residues.get(2).getAtoms()));
        var graph=ResidueGraph.from(new Structure(List.of(new Chain("A",renamed)),List.of(),ConnectivityProvenance.EXPLICIT));
        var state=new SystemStateView(original.identity(),graph,List.of(),original.sources(),Set.of(new ResidueId("A",2,' ')),new totah.lab.athena.interaction.perception.FormalChargeAssignments(Map.of()),true,false,List.of("synthetic full frame; residue labels are not chemistry rules"));
        var p=plan(state);group(p,"ligand",ref(1,"C"));group(p,"environment",ref(2,"C"),ref(203,"C"));matrix(p,"PAIR_MATRIX","ligand","environment");var r=run(state,p);
        var rows=value(r,0,"pairs");assertEquals(2,rows.size());near(3,rows.get(0).get("distanceAngstrom"));near(30,rows.get(1).get("distanceAngstrom"));assertEquals(203,rows.get(1).get("secondAtom").get("residueNumber").asInt());
    }
    @Test void unknownPlanAndFailedEvaluationSurviveCatalog()throws Exception {
        var state=state(new double[][]{{0,0,0},{5,0,0}});var plan=plan(state);op(plan,"FUTURE").put("unknownEvidence","retain");var manifest=manifest();var req=request(state,manifest,plan,100,100);
        var input=envelope(state,"athena:continuous-geometry-plan",plan);var collector=RuleAnalyzers.collector(manifest,req);var catalog=new EvidenceSnapshotCatalog(temp);
        var collected=pipeline().run(catalog,Optional.empty(),state,List.of(input),Map.of(),List.of(collector),ref(ScientificReference.Kind.ACTIVITY,"geometry-collect"),T);
        var report=(ObjectNode)run(state,plan);((ObjectNode)report.get("operations").get(0).get("quantities").get("measurement")).put("status","SUPPORTED_PRESENT");
        var bad=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"geometry"),"tampered","athena:rule-measurements",SystemStateView.bytes(report),collector.method(),state.subject(),T,List.of());
        var evaluated=pipeline().run(catalog,Optional.of(collected.catalogSnapshot()),state,List.of(input,bad),Map.of(),List.of(RuleAnalyzers.evaluator(manifest,req)),ref(ScientificReference.Kind.ACTIVITY,"geometry-evaluate"),T);
        var history=catalog.read(evaluated.catalogSnapshot()).orElseThrow().history();assertEquals(input,history.envelopes().get(input.reference()));assertEquals(bad,history.envelopes().get(bad.reference()));assertTrue(history.interpretations().values().stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.FAILED));
    }
    public static void main(String[] args)throws Exception{var s=state(new double[][]{{0,0,0},{1,0,0},{0,1,0},{20,0,0}});var p=plan(s);group(p,"a",ref(1,"X0"),ref(1,"X1"),ref(1,"X2"));group(p,"b",ref(1,"X3"));op(p,"PLANE").put("groupId","a");matrix(p,"PAIR_MATRIX","a","b");var report=run(s,p);Files.writeString(Path.of(args[0]),report.toString()+"\n");
        if(args.length>1){var m=manifest();var req=request(s,m,p,1000,1000);var collector=RuleAnalyzers.collector(m,req);var input=envelope(s,"athena:continuous-geometry-plan",p);var catalog=new EvidenceSnapshotCatalog(Path.of(args[1]));
            var collected=pipeline().run(catalog,Optional.empty(),s,List.of(input),Map.of(),List.of(collector),ref(ScientificReference.Kind.ACTIVITY,"geometry-replay"),T);
            assertEquals(input,catalog.read(collected.catalogSnapshot()).orElseThrow().history().envelopes().get(input.reference()));
        }}
}
