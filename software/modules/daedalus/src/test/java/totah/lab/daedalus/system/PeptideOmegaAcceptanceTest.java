package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

class PeptideOmegaAcceptanceTest {
    static final Path ROOT=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/peptide-geometry-v1");
    static RuleManifest rule() throws Exception {
        var m=RuleRegistry.load(ROOT).manifests().values().stream().filter(x->x.ruleId().equals("ATHENA.VAL.PEPTIDE_OMEGA.SOURCE_ALPHA_LINK")).findFirst().orElseThrow();
        var n=(ObjectNode)JSON.readTree(SystemStateView.bytes(m));n.put("qualification","QUALIFIED");return RuleRegistry.decode(SystemStateView.bytes(n));
    }
    static RuleManifest group(RuleManifest m)throws Exception{return RuleRegistry.decode(m.parameters().get("sourceManifest").value().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static Fixture fixture(double omega, boolean proline,boolean cyclic) {
        String a="n1:N a1:C c1:C o1:O n2:N a2:C c2:C o2:O";
        String b="n1:a1:SINGLE a1:c1:SINGLE c1:o1:DOUBLE c1:n2:SINGLE n2:a2:SINGLE a2:c2:SINGLE c2:o2:DOUBLE";
        String h="n1:"+(cyclic?1:2)+" a1:2 c1:0 o1:0 n2:"+(proline?0:1)+" a2:"+(proline?1:2)+" c2:0 o2:0";
        if(cyclic)b+=" c2:n1:SINGLE";else {a+=" t:O";b+=" c2:t:SINGLE";h+=" t:1";}
        if(proline){a+=" p:C q:C r:C";b+=" a2:p:SINGLE p:q:SINGLE q:r:SINGLE r:n2:SINGLE";h+=" p:2 q:2 r:2";}
        var f=F07SourceIdentityTest.fixture(a,b,h);double angle=Math.toRadians(omega);
        var points=Map.of("a1",new MolecularGraph.Coordinates(1,0,0),"c1",new MolecularGraph.Coordinates(0,0,0),"n2",new MolecularGraph.Coordinates(0,0,1),"a2",new MolecularGraph.Coordinates(Math.cos(angle),Math.sin(angle),1));
        return new Fixture(new MolecularGraph(f.graph().atoms().stream().map(x->new MolecularGraph.Atom(x.id(),x.element(),x.isotope(),x.formalCharge(),x.explicitHydrogens(),x.aromatic(),x.stereochemistry(),points.getOrDefault(x.id(),x.coordinates()),x.properties())).toList(),f.graph().bonds(),f.graph().properties()),f.hydrogens());
    }
    record Execution(SystemStateView state,RuleManifest manifest,RuleRequest request,List<EvidenceEnvelope> inputs) { }
    static Execution prepare(Fixture f,boolean missingH) throws Exception {
        var m=rule();var g=group(m);var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
        if(missingH){var a=(ObjectNode)c.path("atomState").path("a1");a.put("hydrogenMode","UNKNOWN");a.putNull("implicitHydrogenCount");}
        var gr=B01FunctionalGroupAcceptanceTest.request(s,g);var collector=RuleAnalyzers.collector(g,gr,BACKEND);
        var report=collector.analyze(s,List.of(envelope(s,"athena:group-source-coverage",c),envelope(s,"athena:group-definition",JSON.readTree(g.parameters().get("definition").value()))),Map.of()).getFirst().measurements().get("payload");
        var ge=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"v12"),"group","athena:group-identities",report.getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),s.subject(),T,List.of());
        var map=s.components().getFirst().correspondenceAlternatives().getFirst();var atoms=List.of(map.get("a1"),map.get("c1"),map.get("n2"),map.get("a2"));
        var plan=ContinuousGeometryAcceptanceTest.plan(s);ContinuousGeometryAcceptanceTest.op(plan,"DIHEDRAL",atoms.toArray(AtomReference[]::new));
        var r=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),atoms,List.of(),List.of(),4.5,0,1000,1000);
        return new Execution(s,m,r,List.of(ge,envelope(s,"athena:continuous-geometry-plan",plan)));
    }
    static SystemGraphAnalyzer.Finding execute(Execution e) throws Exception {
        var original=SystemStateView.bytes(e.state().snapshot());var c=RuleAnalyzers.collector(e.manifest(),e.request());
        var payload=c.analyze(e.state(),e.inputs(),Map.of()).getFirst().measurements().get("payload");
        var raw=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"v12"),"measure","athena:rule-measurements",payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),c.method(),e.state().subject(),T,List.of());
        var exchange=new EvidenceExchange();raw=(EvidenceEnvelope)exchange.decodeRecord(exchange.encodeRecord(raw));var inputs=new ArrayList<>(e.inputs());inputs.add(raw);
        var result=RuleAnalyzers.evaluator(e.manifest(),e.request()).analyze(e.state(),inputs,Map.of()).getFirst();
        assertArrayEquals(original,SystemStateView.bytes(e.state().snapshot()));assertTrue(c.qualifies().isEmpty());return result;
    }
    @ParameterizedTest @ValueSource(doubles={0,29.999,30,30.001,90,149.999,150,150.001,180,-30,-90,-150})
    void analyticWindowAndBoundary(double omega) throws Exception {
        var result=execute(prepare(fixture(omega,false,false),false));double actual=Double.parseDouble(result.measurements().get("omegaDegrees"));assertEquals(Math.abs(omega),Math.abs(actual),1e-10);
        boolean twisted=Math.abs(omega)>30&&Math.abs(omega)<150;
        assertEquals(twisted?EvidenceInterpretation.Status.SUPPORTED_PRESENT:EvidenceInterpretation.Status.ABSENT_FALSE,result.status());
    }
    @Test void prolineAndCyclicPeptideAreNotExcludedOrDeclaredBiologicallyInvalid() throws Exception {
        for(boolean proline:List.of(false,true))for(boolean cyclic:List.of(false,true)) {
            var r=execute(prepare(fixture(0,proline,cyclic),false));assertEquals("CIS_WINDOW",r.measurements().get("conformation"));assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,r.status());
        }
    }
    @Test void unknownSourceHAndDegenerateCoordinatesNeverBecomeNegative() throws Exception {
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,execute(prepare(fixture(0,false,false),true)).status());
        var f=fixture(0,false,false);var atoms=f.graph().atoms().stream().map(a->new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(0,0,0),a.properties())).toList();
        var r=execute(prepare(new Fixture(new MolecularGraph(atoms,f.graph().bonds(),Map.of()),f.hydrogens()),false));assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,r.status());
    }
    @Test void permutationExplicitHAndRigidCoordinatesPreserveConformation() throws Exception {
        var f=fixture(90,true,true);var expected=execute(prepare(f,false));assertEquals(expected.status(),execute(prepare(explicit(f),false)).status());
        var atoms=new ArrayList<>(f.graph().atoms().stream().map(a->{var p=a.coordinates();return new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(p.z()+7,p.x()+11,p.y()-9),a.properties());}).toList());Collections.reverse(atoms);
        assertEquals(expected.measurements().get("conformation"),execute(prepare(new Fixture(new MolecularGraph(atoms,f.graph().bonds(),Map.of()),f.hydrogens()),false)).measurements().get("conformation"));
    }
    @Test void missingOrConflictingSelectedReportsFailClosed() throws Exception {
        var e=prepare(fixture(0,false,false),false);var c=RuleAnalyzers.collector(e.manifest(),e.request());
        assertThrows(IllegalArgumentException.class,()->c.analyze(e.state(),List.of(e.inputs().get(1)),Map.of()));
        var doubled=new ArrayList<>(e.inputs());doubled.add(e.inputs().getFirst());assertThrows(IllegalArgumentException.class,()->c.analyze(e.state(),doubled,Map.of()));
    }
    @Test void rawMeasurementTamperingFailsAndUnknownRoleTupleIsInconclusive() throws Exception {
        var e=prepare(fixture(90,false,false),false);var collector=RuleAnalyzers.collector(e.manifest(),e.request());
        var payload=JSON.readTree(collector.analyze(e.state(),e.inputs(),Map.of()).getFirst().measurements().get("payload"));
        ((ObjectNode)payload.path("operations").get(0).path("quantities").path("torsionDegrees")).put("value","0.0");
        var bad=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"v12"),"tampered","athena:rule-measurements",SystemStateView.bytes(payload),collector.method(),e.state().subject(),T,List.of());
        var inputs=new ArrayList<>(e.inputs());inputs.add(bad);
        assertThrows(IllegalArgumentException.class,()->RuleAnalyzers.evaluator(e.manifest(),e.request()).analyze(e.state(),inputs,Map.of()));
        var tuple=new ArrayList<>(e.request().atoms());Collections.reverse(tuple);
        var r=new RuleRequest(e.state().binding(),e.manifest().key(),RuleRegistry.digest(e.manifest()),tuple,List.of(),List.of(),4.5,0,1000,1000);
        var plan=ContinuousGeometryAcceptanceTest.plan(e.state());ContinuousGeometryAcceptanceTest.op(plan,"DIHEDRAL",tuple.toArray(AtomReference[]::new));
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,execute(new Execution(e.state(),e.manifest(),r,List.of(e.inputs().getFirst(),envelope(e.state(),"athena:continuous-geometry-plan",plan)))).status());
    }
    @Test void unqualifiedDefinitionNeverActivatesItself() throws Exception {
        var e=prepare(fixture(90,false,false),false);var n=(ObjectNode)JSON.readTree(SystemStateView.bytes(e.manifest()));n.put("qualification","NOT_EVALUATED");var m=RuleRegistry.decode(SystemStateView.bytes(n));
        var r=new RuleRequest(e.state().binding(),m.key(),RuleRegistry.digest(m),e.request().atoms(),List.of(),List.of(),4.5,0,1000,1000);
        assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,execute(new Execution(e.state(),m,r,e.inputs())).status());
    }
    @Test void inputsRawGeometryAndFindingSurviveExistingQualificationPipeline(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        var e=prepare(fixture(90,true,true),false);var pipeline=pipeline();var catalog=new EvidenceSnapshotCatalog(dir);
        var collector=RuleAnalyzers.collector(e.manifest(),e.request());
        var payload=collector.analyze(e.state(),e.inputs(),Map.of()).getFirst().measurements().get("payload");
        var raw=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"v12"),"persistent","athena:rule-measurements",payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),e.state().subject(),T,List.of());
        var inputs=new ArrayList<>(e.inputs());inputs.add(raw);
        var result=pipeline.run(catalog,Optional.empty(),e.state(),inputs,Map.of(),List.of(RuleAnalyzers.evaluator(e.manifest(),e.request())),ref(ScientificReference.Kind.ACTIVITY,"v12-persist"),T);
        var history=catalog.read(result.catalogSnapshot()).orElseThrow().history();
        for(var input:inputs)assertArrayEquals(input.readPayload(),history.envelopes().get(input.reference()).readPayload());
        assertTrue(history.interpretations().values().stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT&&"TWISTED_OUTSIDE_WINDOWS".equals(i.measurements().get("conformation"))));
    }
    public static void main(String[] args)throws Exception {var rows=new ArrayList<Object>();for(double w:new double[]{0,30,90,150,180})rows.add(execute(prepare(fixture(w,true,true),false)));Files.write(Path.of(args[0]),SystemStateView.bytes(rows));}
}
