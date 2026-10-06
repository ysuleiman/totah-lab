package totah.lab.daedalus.system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.system.*;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.graph.*;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;
import static totah.lab.athena.system.SystemGraphCertificate.*;

public class SystemQualificationAcceptanceTest {
    @TempDir Path temp;
    static final Instant T=Instant.parse("2026-10-04T00:00:00Z");
    static ScientificReference ref(ScientificReference.Kind kind,String id){return new ScientificReference(kind,"system-fixture",id,"1");}
    static SystemStateView state(double shift,boolean fullChemistry) {
        var atoms=new ArrayList<Residue>();
        atoms.add(new Residue("LIG",1,List.of(Atom.builder().name("C1").element(Element.C).autoDockType("C").position(new Point3D(0,0,0)).build())));
        atoms.add(new Residue("CYS",17,List.of(Atom.builder().name("SG").element(Element.S).autoDockType("SA").position(new Point3D(3+shift,0,0)).build())));
        atoms.add(new Residue("CYS",48,List.of(Atom.builder().name("SG").element(Element.S).autoDockType("SA").position(new Point3D(6+shift,0,0)).build())));
        atoms.add(new Residue("GLY",92,List.of(Atom.builder().name("CA").element(Element.C).autoDockType("C").position(new Point3D(9+shift,0,0)).build())));
        atoms.add(new Residue("HOH",95,List.of(Atom.builder().name("H1").element(Element.H).autoDockType("HD").position(new Point3D(0,0,1)).build())));
        var structure=new Structure(List.of(new Chain("A",atoms)));
        var molecular=new MolecularGraph(List.of(new MolecularGraph.Atom("C1","C",null,0,0,false,"UNSPECIFIED",new MolecularGraph.Coordinates(0,0,0),Map.of())),List.of(),Map.of());
        var component=new SystemStateView.Component(ref(SUBJECT,"ligand"),molecular,List.of(Map.of("C1",new AtomReference("A",1,' ',"C1"))),List.of());
        return new SystemStateView(ref(CONTEXT,"state"),ResidueGraph.from(structure),List.of(component),List.of(ref(SOURCE,"fixture")),Set.of(),FormalChargeAssignments.EMPTY,true,fullChemistry,List.of("synthetic only"));
    }
    static SystemQualificationPipeline pipeline(){var b=new OclMolecularBackend();return new SystemQualificationPipeline(new SystemGraphValidation(b,b,b));}
    static EvidenceEnvelope unknown(SystemStateView s){return SystemQualificationPipeline.envelope(ref(ACTIVITY,"source"),"opaque","future:quantum",new byte[]{0,-1,7},ref(METHOD,"future"),s.subject(),T,List.of("uninterpreted"));}
    static SystemGraphAnalyzer finding(String method,EvidenceInterpretation.Status status,boolean fail) {
        return new SystemGraphAnalyzer(){
            public ScientificReference method(){return ref(METHOD,method);}
            public Set<Capability> requires(){return Set.of(Capability.DISTANCE_QUERIES);}
            public Set<Capability> qualifies(){return Set.of(Capability.HBOND_ANALYSIS);}
            public Set<String> evidenceTypes(){return Set.of("athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> e,Map<String,String> c)throws Exception {
                if(fail)throw new Exception("synthetic evaluator failure");
                return List.of(new Finding("same-motif",List.of(s.subject()),status,Map.of("measurement","original"),List.of("synthetic contradictory methods"),List.of()));
            }
        };
    }
    SystemQualificationPipeline.Published run(SystemStateView s,List<SystemGraphAnalyzer> analyzers,String id,Optional<EvidenceAdmission.Pin> parent,Map<String,String> config)throws Exception {
        return pipeline().run(new EvidenceSnapshotCatalog(temp),parent,s,List.of(unknown(s)),config,analyzers,ref(ACTIVITY,id),T);
    }
    @Test void validStateCertificateAndPartialChemistryHaveSeparateCapabilities()throws Exception {
        var s=state(0,false);var result=run(s,List.of(ExistingSystemAnalyzers.distances()),"run",Optional.empty(),Map.of());
        assertSame(s,result.state());assertEquals(Status.QUALIFIED,result.certificate().capabilities().get(Capability.DISTANCE_QUERIES).status());
        assertEquals(Status.QUALIFIED,result.certificate().capabilities().get(Capability.GRAPH_TRANSFORMATIONS).status());
        assertEquals(Status.CONDITIONAL,result.certificate().capabilities().get(Capability.HBOND_ANALYSIS).status());
        assertEquals(Status.NOT_EVALUATED,result.certificate().capabilities().get(Capability.ENERGETICS).status());
        assertThrows(IllegalStateException.class,()->result.certificate().require(Capability.HBOND_ANALYSIS,s,Map.of(),result.certificate().evidenceSnapshot(),result.certificate().analyzers(),false));
        assertDoesNotThrow(()->result.certificate().require(Capability.HBOND_ANALYSIS,s,Map.of(),result.certificate().evidenceSnapshot(),result.certificate().analyzers(),true));
        assertThrows(UnsupportedOperationException.class,()->result.certificate().capabilities().clear());
    }
    @Test void secondShellTraversalIsGeneralAndDoesNotAssertChemicalEdges() {
        var s=state(0,false);var start=new ResidueId("A",1,null);
        var one=s.neighborhood(start,AtomDistanceCriterion.heavyAtomsWithin(3),1,100);
        var two=s.neighborhood(start,AtomDistanceCriterion.heavyAtomsWithin(3),2,100);
        assertFalse(one.visited().stream().anyMatch(a->a.residueNumber()==48));assertTrue(two.visited().stream().anyMatch(a->a.residueNumber()==48));
        assertTrue(two.covalent().isEmpty());assertTrue(two.interactions().isEmpty());assertFalse(two.complete());
        assertTrue(s.neighborhood(start,AtomDistanceCriterion.heavyAtomsWithin(3),5,100).complete());
        assertFalse(s.neighborhood(start,AtomDistanceCriterion.heavyAtomsWithin(3),5,1).complete());
        var all=s.neighborhood(start,AtomDistanceCriterion.allAtomsWithin(3),5,100);
        assertTrue(all.visited().stream().anyMatch(a->a.atomName().equals("H1")));
        assertFalse(two.visited().stream().anyMatch(a->a.atomName().equals("H1")));
    }
    @Test void indexedQueriesMatchBruteForceIncludingBoundaryAndCycleTraversal() {
        var s=state(0,false);var expected=new TreeSet<String>();var entries=new ArrayList<>(s.atoms().entrySet());
        for(int i=0;i<entries.size();i++)for(int j=i+1;j<entries.size();j++) {
            var a=entries.get(i);var b=entries.get(j);
            if(a.getValue().isHeavyAtom()&&b.getValue().isHeavyAtom()&&a.getValue().getPosition().distance(b.getValue().getPosition())<=6)
                expected.add(a.getKey()+"|"+b.getKey());
        }
        var actual=new TreeSet<String>();s.graph().atomProximities(AtomDistanceCriterion.heavyAtomsWithin(6)).forEach(p->p.atomPairs().forEach(a->actual.add(a.first()+"|"+a.second())));
        assertEquals(expected,actual);
        var traversal=s.neighborhood(new ResidueId("A",1,null),AtomDistanceCriterion.heavyAtomsWithin(6),20,100);
        assertEquals(traversal.visited().size(),new HashSet<>(traversal.visited()).size());assertTrue(traversal.complete());
    }
    @Test void failedAnalyzerLeavesInputsIntactAndOtherCapabilitiesUsable()throws Exception {
        var s=state(0,false);var seen=new boolean[]{false};var delegate=finding("failure",EvidenceInterpretation.Status.FAILED,true);
        var checker=new SystemGraphAnalyzer(){
            public ScientificReference method(){return delegate.method();}public Set<Capability> requires(){return delegate.requires();}
            public Set<Capability> qualifies(){return delegate.qualifies();}public Set<String> evidenceTypes(){return delegate.evidenceTypes();}
            public List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> e,Map<String,String> c)throws Exception {
                var catalog=new EvidenceSnapshotCatalog(temp);seen[0]=catalog.entries().size()==1;
                assertTrue(catalog.read(catalog.entries().getFirst().pin()).orElseThrow().history().envelopes().containsKey(unknown(s).reference()));
                return delegate.analyze(state,e,c);
            }
        };
        var result=run(s,List.of(checker),"failure-run",Optional.empty(),Map.of());assertTrue(seen[0]);
        var view=new EvidenceQueries(temp).evidence(result.catalogSnapshot().reference());
        assertTrue(view.interpretations().stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.FAILED));
        assertTrue(view.interpretations().stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.UNSUPPORTED));
        assertArrayEquals(new byte[]{0,-1,7},view.envelopes().stream().filter(e->e.reference().equals(unknown(s).reference())).findFirst().orElseThrow().readPayload());
        assertEquals(Status.QUALIFIED,result.certificate().capabilities().get(Capability.DISTANCE_QUERIES).status());
        assertEquals(Status.FAILED,result.certificate().capabilities().get(Capability.HBOND_ANALYSIS).status());
    }
    @Test void contradictoryMotifsAndRulesetUpgradePreserveOldCertificate()throws Exception {
        var s=state(0,false);var a=run(s,List.of(finding("a",EvidenceInterpretation.Status.SUPPORTED_PRESENT,false),finding("b",EvidenceInterpretation.Status.ABSENT_FALSE,false)),"old",Optional.empty(),Map.of());
        var catalog=new EvidenceSnapshotCatalog(temp);byte[] old=new EvidenceExchange().encode(catalog.read(a.catalogSnapshot()).orElseThrow());
        var b=run(s,List.of(finding("rule-v2",EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,false)),"new",Optional.of(a.catalogSnapshot()),Map.of());
        var v=new EvidenceQueries(temp).evidence(b.catalogSnapshot().reference());
        assertTrue(v.interpretations().stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT));
        assertTrue(v.interpretations().stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.ABSENT_FALSE));
        assertNotEquals(a.certificate().reference(),b.certificate().reference());assertArrayEquals(old,new EvidenceExchange().encode(catalog.read(a.catalogSnapshot()).orElseThrow()));
    }
    @Test void cutoffChangesAnalysisNeverDeletesEvidenceAndReplayIsIdempotent()throws Exception {
        var s=state(0,false);var a=run(s,List.of(ExistingSystemAnalyzers.distances()),"a",Optional.empty(),Map.of("contactCutoffAngstrom","3"));
        var replay=run(s,List.of(ExistingSystemAnalyzers.distances()),"a",Optional.empty(),Map.of("contactCutoffAngstrom","3"));assertEquals(a.certificate(),replay.certificate());
        var b=run(s,List.of(ExistingSystemAnalyzers.distances()),"b",Optional.of(a.catalogSnapshot()),Map.of("contactCutoffAngstrom","1"));
        var x=new EvidenceQueries(temp).evidence(a.catalogSnapshot().reference());var y=new EvidenceQueries(temp).evidence(b.catalogSnapshot().reference());
        assertTrue(y.envelopes().containsAll(x.envelopes()));
        var old=x.interpretations().stream().filter(i->i.evaluator().id().equals("distances")).findFirst().orElseThrow();
        var newer=y.interpretations().stream().filter(i->i.evaluator().id().equals("distances")&&i.configuration().get("contactCutoffAngstrom").equals("1")).findFirst().orElseThrow();
        assertNotEquals(old.measurements(),newer.measurements());
    }
    @Test void staleStateConfigurationEvidenceAndRulesetAreRejected()throws Exception {
        var s=state(0,false);var r=run(s,List.of(),"r",Optional.empty(),Map.of());var c=r.certificate();
        assertThrows(IllegalArgumentException.class,()->c.require(Capability.DISTANCE_QUERIES,state(0.2,false),Map.of(),c.evidenceSnapshot(),c.analyzers(),false));
        assertThrows(IllegalArgumentException.class,()->c.require(Capability.DISTANCE_QUERIES,s,Map.of("new","config"),c.evidenceSnapshot(),c.analyzers(),false));
        assertThrows(IllegalArgumentException.class,()->c.require(Capability.DISTANCE_QUERIES,s,Map.of(),new EvidenceAdmission.Pin(c.evidenceSnapshot().reference(),"0".repeat(64)),c.analyzers(),false));
        assertThrows(IllegalArgumentException.class,()->c.require(Capability.DISTANCE_QUERIES,s,Map.of(),c.evidenceSnapshot(),List.of(),false));
    }
    @Test void unresolvedAndAmbiguousCorrespondenceSurviveWithoutGlobalRejection()throws Exception {
        var original=state(0,false);var component=original.components().getFirst();
        var ambiguous=new SystemStateView.Component(component.identity(),component.chemistry(),List.of(Map.of("C1",new AtomReference("missing",99,' ',"X")),Map.of()),List.of("unresolved alternatives"));
        var s=new SystemStateView(original.identity(),original.graph(),List.of(ambiguous),original.sources(),Set.of(),original.charges(),true,false,List.of("missing mapping"));
        var result=run(s,List.of(),"unresolved",Optional.empty(),Map.of());
        assertEquals(Status.QUALIFIED,result.certificate().capabilities().get(Capability.DISTANCE_QUERIES).status());
        assertEquals(Status.CONDITIONAL,result.certificate().capabilities().get(Capability.GRAPH_TRANSFORMATIONS).status());
    }
    @Test void chemistryAndCorrespondenceChangesInvalidateCertificate()throws Exception {
        var s=state(0,false);var result=run(s,List.of(),"binding",Optional.empty(),Map.of());var c=result.certificate();
        var component=s.components().getFirst();var atom=component.chemistry().atoms().getFirst();
        var charged=new MolecularGraph(List.of(new MolecularGraph.Atom(atom.id(),atom.element(),atom.isotope(),1,atom.explicitHydrogens(),atom.aromatic(),atom.stereochemistry(),atom.coordinates(),atom.properties())),component.chemistry().bonds(),component.chemistry().properties());
        var changedChemistry=new SystemStateView(s.identity(),s.graph(),List.of(new SystemStateView.Component(component.identity(),charged,component.correspondenceAlternatives(),List.of())),s.sources(),s.cofactors(),s.charges(),true,false,s.limitations());
        var changedMapping=new SystemStateView(s.identity(),s.graph(),List.of(new SystemStateView.Component(component.identity(),component.chemistry(),List.of(Map.of()),List.of())),s.sources(),s.cofactors(),s.charges(),true,false,s.limitations());
        assertNotEquals(s.binding().chemicalSha256(),changedChemistry.binding().chemicalSha256());
        assertNotEquals(s.binding().correspondenceSha256(),changedMapping.binding().correspondenceSha256());
        for(var other:List.of(changedChemistry,changedMapping))assertThrows(IllegalArgumentException.class,()->c.require(Capability.DISTANCE_QUERIES,other,Map.of(),c.evidenceSnapshot(),c.analyzers(),false));
    }
    @Test void newRunNeverAttributesItsMeasurementsToAnInheritedState()throws Exception {
        var s=state(0,false);var first=run(s,List.of(ExistingSystemAnalyzers.distances()),"old-state",Optional.empty(),Map.of());
        // New explicit observations have their own immutable identity. No mutation of old input.
        var next=pipeline().run(new EvidenceSnapshotCatalog(temp),Optional.of(first.catalogSnapshot()),state(0.5,false),List.of(),Map.of(),List.of(ExistingSystemAnalyzers.distances()),ref(ACTIVITY,"new-state"),T);
        var view=new EvidenceQueries(temp).evidence(next.catalogSnapshot().reference());
        var newFindings=view.interpretations().stream().filter(i->i.reference().id().startsWith("new-state/")).toList();
        assertEquals(1,newFindings.size());
        assertEquals(List.of("new-state/state"),newFindings.getFirst().inputs().stream().map(i->i.reference().id()).toList());
        assertTrue(view.envelopes().stream().anyMatch(e->e.reference().id().equals("old-state/state")));
    }
    @Test void existingInteractionAndClashAdaptersPreserveCoverageLimits()throws Exception {
        var s=state(0,false);var result=run(s,List.of(ExistingSystemAnalyzers.clashes(),ExistingSystemAnalyzers.interactions(Set.of(new ResidueId("A",1,null)))),"adapters",Optional.empty(),Map.of());
        var view=new EvidenceQueries(temp).evidence(result.catalogSnapshot().reference());
        var interactions=view.interpretations().stream().filter(i->i.evaluator().id().startsWith("interactions-")).findFirst().orElseThrow();
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,interactions.status());
        assertTrue(interactions.measurements().containsKey("profile"));
        assertTrue(view.interpretations().stream().anyMatch(i->i.evaluator().id().equals("clashes")&&i.measurements().containsKey("clashes")));
    }
    @Test void failedChemicalValidationBlocksChemicalCapabilitiesButPreservesGeometryAndEvidence()throws Exception {
        var backend=new OclMolecularBackend();
        MolecularSanitizer rejection=(graph,policy)->new MolecularSanitizer.Result(graph,false,new BackendEvidence("synthetic","1","reject",Map.of(),List.of(),List.of("explicit rejected chemistry")));
        var p=new SystemQualificationPipeline(new SystemGraphValidation(rejection,backend,backend));var s=state(0,true);
        var result=p.run(new EvidenceSnapshotCatalog(temp),Optional.empty(),s,List.of(unknown(s)),Map.of(),List.of(),ref(ACTIVITY,"bad-chemistry"),T);
        for(var capability:List.of(Capability.GRAPH_TRANSFORMATIONS,Capability.INTERACTION_TYPING,Capability.HBOND_ANALYSIS))
            assertEquals(Status.FAILED,result.certificate().capabilities().get(capability).status());
        assertEquals(Status.QUALIFIED,result.certificate().capabilities().get(Capability.DISTANCE_QUERIES).status());
        assertTrue(new EvidenceQueries(temp).evidence(result.catalogSnapshot().reference()).envelopes().contains(unknown(s)));
    }
    public static void main(String[] args)throws Exception {
        var t=new SystemQualificationAcceptanceTest();t.temp=Files.createDirectory(Path.of(args[0]));
        var result=t.run(state(0,false),List.of(ExistingSystemAnalyzers.distances()),"deterministic",Optional.empty(),Map.of());
        Files.write(t.temp.resolve("certificate.json"),SystemStateView.bytes(result.certificate()),StandardOpenOption.CREATE_NEW);
    }
}
