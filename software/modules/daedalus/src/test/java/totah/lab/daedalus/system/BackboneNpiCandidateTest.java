package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;

class BackboneNpiCandidateTest {
    static RuleManifest rule(boolean cyclic)throws Exception {
        String id=cyclic?"ATHENA.INT.N_PI_STAR.CYCLIC_DIPEPTIDE_CANDIDATE":"ATHENA.INT.N_PI_STAR.ADJACENT_BACKBONE_CANDIDATE";
        var m=RuleRegistry.load(PeptideOmegaAcceptanceTest.ROOT).manifests().values().stream().filter(x->x.ruleId().equals(id)).findFirst().orElseThrow();
        var n=(ObjectNode)JSON.readTree(SystemStateView.bytes(m));n.put("qualification","QUALIFIED");return RuleRegistry.decode(SystemStateView.bytes(n));
    }
    static Fixture fixture(double distance,double angle,boolean cyclic,boolean proline,boolean degeneratePlane) {
        var f=PeptideOmegaAcceptanceTest.fixture(0,proline,cyclic);var h=new TreeMap<>(f.hydrogens());String third=cyclic?"n1":"t";
        if(!cyclic)h.put("t",2);
        double rad=Math.toRadians(angle);var points=Map.of("c2",new MolecularGraph.Coordinates(0,0,0),"o2",new MolecularGraph.Coordinates(1,0,0),"o1",new MolecularGraph.Coordinates(distance*Math.cos(rad),distance*Math.sin(rad),0),"a2",new MolecularGraph.Coordinates(degeneratePlane?2:0,degeneratePlane?0:1,0),third,new MolecularGraph.Coordinates(degeneratePlane?3:0,degeneratePlane?0:-1,0));
        return new Fixture(new MolecularGraph(f.graph().atoms().stream().map(a->new MolecularGraph.Atom(a.id(),a.id().equals("t")?"N":a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),points.getOrDefault(a.id(),a.coordinates()),a.properties())).toList(),f.graph().bonds(),Map.of()),h);
    }
    static PeptideOmegaAcceptanceTest.Execution prepare(Fixture f,boolean cyclic,boolean unknownH)throws Exception {
        var m=rule(cyclic);var g=PeptideOmegaAcceptanceTest.group(m);var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
        if(unknownH){var a=(ObjectNode)c.path("atomState").path("a1");a.put("hydrogenMode","UNKNOWN");a.putNull("implicitHydrogenCount");}
        var gr=B01FunctionalGroupAcceptanceTest.request(s,g);var gc=RuleAnalyzers.collector(g,gr,BACKEND);
        var payload=gc.analyze(s,List.of(envelope(s,"athena:group-source-coverage",c),envelope(s,"athena:group-definition",JSON.readTree(g.parameters().get("definition").value()))),Map.of()).getFirst().measurements().get("payload");
        var ge=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"g07"),"group","athena:group-identities",payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),gc.method(),s.subject(),T,List.of());
        var map=s.components().getFirst().correspondenceAlternatives().getFirst();var atoms=List.of(map.get("o1"),map.get("c2"),map.get("o2"),map.get("a2"),map.get(cyclic?"n1":"t"));
        var plan=ContinuousGeometryAcceptanceTest.plan(s);
        ContinuousGeometryAcceptanceTest.op(plan,"DISTANCE",atoms.get(0),atoms.get(1));
        ContinuousGeometryAcceptanceTest.op(plan,"ANGLE",atoms.get(0),atoms.get(1),atoms.get(2));
        ContinuousGeometryAcceptanceTest.group(plan,"acceptor-substituents",atoms.subList(2,5).toArray(AtomReference[]::new));
        var plane=ContinuousGeometryAcceptanceTest.op(plan,"POINT_PLANE",atoms.get(1));plane.remove("atoms");plane.set("atom",JSON.valueToTree(atoms.get(1)));plane.put("planeGroupId","acceptor-substituents");
        var r=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),atoms,List.of(),List.of(),4.5,0,1000,1000);
        return new PeptideOmegaAcceptanceTest.Execution(s,m,r,List.of(ge,envelope(s,"athena:continuous-geometry-plan",plan)));
    }
    @ParameterizedTest @CsvSource({"3.2,99,true","3.2,119,true","3.2001,109,false","3.0,98.999,false","3.0,119.001,false","3.0,109,true","3.0,90,false","4.0,109,false"})
    void publishedOperationalDistanceAndAngle(double distance,double angle,boolean positive)throws Exception {
        var result=PeptideOmegaAcceptanceTest.execute(prepare(fixture(distance,angle,false,false,false),false,false));
        assertEquals(positive?EvidenceInterpretation.Status.SUPPORTED_PRESENT:EvidenceInterpretation.Status.ABSENT_FALSE,result.status());
        assertEquals(distance,Double.parseDouble(result.measurements().get("distanceAngstrom")),1e-12);assertEquals(angle,Double.parseDouble(result.measurements().get("angleDegrees")),1e-12);
    }
    @Test void cyclicDipeptideAndProlineLikeBackbonesRetainExplicitRoleAlias()throws Exception {
        for(boolean cyclic:List.of(false,true))for(boolean proline:List.of(false,true)) {
            var e=prepare(fixture(3,109,cyclic,proline,false),cyclic,false);assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,PeptideOmegaAcceptanceTest.execute(e).status());
            if(cyclic){var report=JSON.readTree(e.inputs().getFirst().readPayload());for(var o:report.path("occurrences"))for(var r:o.path("roleCorrespondenceAlternatives"))assertEquals(r.path("N1"),r.path("N3"));}
        }
    }
    @Test void optionalPlaneDegeneracyDoesNotFabricatePlaneOrInvalidateIndependentScreen()throws Exception {
        var r=PeptideOmegaAcceptanceTest.execute(prepare(fixture(3,109,false,false,true),false,false));assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,r.status());
        var quantities=JSON.readTree(r.measurements().get("payload")).path("operations").get(2).path("quantities");
        assertFalse(quantities.has("perpendicularDistanceAngstrom"));var plane=quantities.path("measurement");
        assertEquals("UNKNOWN_INCONCLUSIVE",plane.path("status").asText());assertTrue(plane.path("value").isNull());
    }
    @Test void unknownStateAndZeroDistanceAngleAreNotNegative()throws Exception {
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,PeptideOmegaAcceptanceTest.execute(prepare(fixture(3,109,false,false,false),false,true)).status());
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,PeptideOmegaAcceptanceTest.execute(prepare(fixture(0,109,false,false,false),false,false)).status());
    }
    @Test void freeCarboxylEndCannotMasqueradeAsBackboneAmideAcceptor()throws Exception {
        var f=PeptideOmegaAcceptanceTest.fixture(0,false,false);
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,PeptideOmegaAcceptanceTest.execute(prepare(f,false,false)).status());
    }
    @Test void explicitHydrogenAndAtomPermutationPreserveResults()throws Exception {
        var f=fixture(3,109,true,true,false);var r=PeptideOmegaAcceptanceTest.execute(prepare(f,true,false));assertEquals(r.status(),PeptideOmegaAcceptanceTest.execute(prepare(explicit(f),true,false)).status());
        var atoms=new ArrayList<>(f.graph().atoms());Collections.reverse(atoms);assertEquals(r.status(),PeptideOmegaAcceptanceTest.execute(prepare(new Fixture(new MolecularGraph(atoms,f.graph().bonds(),Map.of()),f.hydrogens()),true,false)).status());
    }
    public static void main(String[] args)throws Exception {var rows=new ArrayList<Object>();for(boolean cyclic:List.of(false,true))for(double d:new double[]{3,4})rows.add(PeptideOmegaAcceptanceTest.execute(prepare(fixture(d,109,cyclic,true,false),cyclic,false)));Files.write(Path.of(args[0]),SystemStateView.bytes(rows));}
}
