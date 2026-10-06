package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.WaterBridgeFixtures.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

class WaterBridgeDomainTest {
    static Stream<String> donors()throws Exception {var f=new WaterBridgeFixtures(1,false);var n=JSON.readTree(f.manifest.parameters().get("roles").value()).get("donors");var out=new ArrayList<String>();n.fieldNames().forEachRemaining(out::add);return out.stream();}
    static Stream<String> acceptors()throws Exception {var f=new WaterBridgeFixtures(1,false);var n=JSON.readTree(f.manifest.parameters().get("roles").value()).get("acceptors");var out=new ArrayList<String>();n.fieldNames().forEachRemaining(out::add);return out.stream();}
    static Fixture renamed(Fixture f,String anchor,double x,boolean donor){
        var original=explicit(f);var aa=new ArrayList<MolecularGraph.Atom>();var bb=new ArrayList<MolecularGraph.Bond>();var h=new TreeMap<String,Integer>();
        for(var a:original.graph().atoms()){String id=a.id().equals(anchor)?"o":a.id();double px=x+10+aa.size(),py=3;
            if(a.id().equals(anchor)){px=x;py=0;}else if(donor&&a.id().startsWith(anchor+"H")){px=x+1;py=0;}
            aa.add(new MolecularGraph.Atom(id,a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(px,py,0),a.properties()));h.put(id,0);}
        for(var b:original.graph().bonds())bb.add(new MolecularGraph.Bond(b.id(),b.firstAtomId().equals(anchor)?"o":b.firstAtomId(),b.secondAtomId().equals(anchor)?"o":b.secondAtomId(),b.order(),b.aromatic(),b.stereochemistry(),b.properties()));
        return new Fixture(new MolecularGraph(aa,bb,Map.of()),h);
    }
    @ParameterizedTest @MethodSource("donors") void everyApprovedNonwaterDonorToWater(String name)throws Exception {
        var row=ChemicalRoleAcceptanceTest.cases().stream().filter(r->r[0].equals(name)).findFirst().orElseThrow();var fs=new ArrayList<>(chain(1));fs.set(0,renamed(ChemicalRoleAcceptanceTest.chemical(row[1]),row[2],0,true));
        var f=new WaterBridgeFixtures(fs,false,Map.of());var r=f.result();assertTrue(StreamSupport.stream(r.path("legs").spliterator(),false).anyMatch(l->l.path("donorClass").asText().equals(name)&&l.path("acceptorClass").asText().equals("WATER.NEUTRAL.EXPLICIT_H2")&&l.path("assessment").asText().equals("SUPPORTED_PRESENT")),name+": "+r.path("reasons"));
    }
    @ParameterizedTest @MethodSource("acceptors") void waterToEveryApprovedNonwaterAcceptor(String name)throws Exception {
        var row=HbondCandidateFixtures.acceptor(name);var fs=new ArrayList<>(chain(1));fs.set(2,renamed(ChemicalRoleAcceptanceTest.chemical(row[0]),row[1],6,false));var f=new WaterBridgeFixtures(fs,false,Map.of());var r=f.result();
        assertTrue(StreamSupport.stream(r.path("legs").spliterator(),false).anyMatch(l->l.path("donorClass").asText().equals("WATER.NEUTRAL.EXPLICIT_H2")&&l.path("acceptorClass").asText().equals(name)&&l.path("assessment").asText().equals("SUPPORTED_PRESENT")),name+": "+r.path("reasons"));
    }
    @ParameterizedTest @CsvSource({"3.5,180,SUPPORTED_PRESENT","3.50001,180,ABSENT_FALSE","3.4,130,SUPPORTED_PRESENT","3.5,130,SUPPORTED_PRESENT","3.4,130.00001,SUPPORTED_PRESENT","3.4,129.99999,ABSENT_FALSE"})
    void sourceWindowBoundaries(double da,double angle,String expected)throws Exception {
        var fs=new ArrayList<>(chain(1));double cos=Math.cos(Math.toRadians(angle));double ha=cos+Math.sqrt(da*da-1+cos*cos);
        // These representable coordinates produce exactly 3.5 and 130 in the shared geometry evaluator.
        double x=da==3.5&&angle==130?5.782033266609426:4-ha*cos,y=da==3.5&&angle==130?2.12374454760418:ha*Math.sin(Math.toRadians(angle));
        fs.set(2,position(fs.get(2),"o",x,y,0));var f=new WaterBridgeFixtures(fs,false,Map.of());var r=f.result();
        var leg=StreamSupport.stream(r.path("legs").spliterator(),false).filter(l->l.path("donor").path("residueNumber").asInt()==2&&l.path("hydrogen").path("atomName").asText().equals("h1")&&l.path("acceptor").path("residueNumber").asInt()==3).findFirst().orElseThrow();assertEquals(expected,leg.path("assessment").asText());
    }
    @Test void isotopeLabelsRemainSourceMetadata()throws Exception {var fs=new ArrayList<>(chain(1));var w=fs.get(1);var aa=w.graph().atoms().stream().map(a->new MolecularGraph.Atom(a.id(),a.element(),a.element().equals("H")?2:18,a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),a.coordinates(),a.properties())).toList();fs.set(1,new Fixture(new MolecularGraph(aa,w.graph().bonds(),Map.of()),w.hydrogens()));var f=new WaterBridgeFixtures(fs,false,Map.of());assertEquals("SUPPORTED_PRESENT",f.result().path("assessment").asText());}
    @ParameterizedTest @ValueSource(strings={"hydroxide","hydronium","peroxide","alcohol","metal","sharedH","chargedH","doubleH"})
    void exactExclusionsNeverBecomeWaterFromInventory(String kind)throws Exception {
        Fixture w=water(3,true);var aa=new ArrayList<>(w.graph().atoms());var bb=new ArrayList<>(w.graph().bonds());var hh=new TreeMap<>(w.hydrogens());
        if(kind.equals("hydroxide")){aa.removeIf(a->a.id().equals("h2"));bb.removeIf(b->b.secondAtomId().equals("h2"));hh.remove("h2");w=charge(new Fixture(new MolecularGraph(aa,bb,Map.of()),hh),"o",-1);}
        else if(kind.equals("hydronium")){aa.add(atom("h3","H",0,false,3,0,1));bb.add(bond("o","h3",MolecularGraph.BondOrder.SINGLE));hh.put("h3",0);w=charge(new Fixture(new MolecularGraph(aa,bb,Map.of()),hh),"o",1);}
        else if(kind.equals("chargedH"))w=charge(w,"h1",1);
        else if(kind.equals("doubleH")){bb.set(0,bond("o","h1",MolecularGraph.BondOrder.DOUBLE));w=new Fixture(new MolecularGraph(aa,bb,Map.of()),hh);}
        else {String element=kind.equals("metal")?"Zn":kind.equals("peroxide")?"O":"C";aa.add(atom("extra",element,0,false,3,-1,0));bb.add(bond(kind.equals("sharedH")?"h1":"o","extra",MolecularGraph.BondOrder.SINGLE));hh.put("extra",element.equals("C")?3:element.equals("O")?1:0);w=new Fixture(new MolecularGraph(aa,bb,Map.of()),hh);}
        var fs=new ArrayList<>(chain(1));fs.set(1,w);var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);var r=f.result();assertNotEquals("SUPPORTED_PRESENT",r.path("assessment").asText());assertEquals("UNSUPPORTED",r.path("waterIdentities").get(0).path("assessment").asText(),kind+": "+r.path("waterIdentities"));
    }
    @Test void knownNonneutralExcludesEvenWhenHydrogenStateUnknown()throws Exception {var fs=new ArrayList<>(chain(1));fs.set(1,charge(fs.get(1),"o",1));var f=new WaterBridgeFixtures(fs,false,Map.of(1,"H"));f.coverage(SUPPORTED_PRESENT);assertEquals("ABSENT_FALSE",f.result().path("assessment").asText());}
    @Test void degenerateWaterHDoesNotGainEligibilityFromCoverage()throws Exception {var fs=new ArrayList<>(chain(1));fs.set(1,position(fs.get(1),"h1",3,0,0));var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());}
    @Test void nonfiniteCoordinatesFailAtSourceConstruction(){assertThrows(IllegalArgumentException.class,()->new totah.lab.gaia.geometry.Point3D(Double.NaN,0,0));}
    @Test void explicitHydrogenTuplesAreNotCollapsed()throws Exception {var fs=new ArrayList<>(chain(1));fs.set(1,position(fs.get(1),"h2",4,0,0));var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);var r=f.result();assertTrue(r.path("paths").size()>=2);var ids=new HashSet<String>();r.path("paths").forEach(p->ids.add(p.path("eventKeys").toString()));assertEquals(r.path("paths").size(),ids.size());}
    @Test void reversingEndpointQueryPreservesWaterWitnesses()throws Exception {var f=new WaterBridgeFixtures(2,true);var first=f.plan.get("first").deepCopy();f.plan.set("first",f.plan.get("second"));f.plan.set("second",first);f.coverage(SUPPORTED_PRESENT);var r=f.result();assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText());assertEquals(2,r.path("paths").get(0).path("waters").size());}
    @Test void oneWaterIsNotAnI13Path()throws Exception {var f=new WaterBridgeFixtures(1,true);f.coverage(SUPPORTED_PRESENT);assertEquals("ABSENT_FALSE",f.result().path("assessment").asText());}
    @Test void twoWaterPathIsNotI12()throws Exception {var f=new WaterBridgeFixtures(2,false);f.coverage(SUPPORTED_PRESENT);assertEquals("ABSENT_FALSE",f.result().path("assessment").asText());}
    @Test void waterCountBoundExcludesLongerPathsWithoutClaimOutsideBound()throws Exception {var f=new WaterBridgeFixtures(3,true);f.plan.put("maximumWaters",2);f.coverage(SUPPORTED_PRESENT);assertEquals("ABSENT_FALSE",f.result().path("assessment").asText());}
    @Test void unknownWaterElsewhereDoesNotEraseValidWitness()throws Exception {var fs=new ArrayList<>(chain(2));fs.set(2,water(30,false));fs.set(3,endpoint(6,false));var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);var r=f.result();assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText());assertFalse(r.path("complete").asBoolean());}
    @Test void sourceStateMismatchCannotCombineAlternativeOrientations()throws Exception {var f=new WaterBridgeFixtures(1,false);var other=new WaterBridgeFixtures(2,true);f.artifacts.add(other.artifacts.get(0));assertThrows(Exception.class,f::result);}
    @Test void unsupportedSulfurEndpointDoesNotBecomeEligible()throws Exception {var fs=new ArrayList<>(chain(1));fs.set(0,renamed(B01FunctionalGroupAcceptanceTest.fixture("thiol"),"a1",0,true));var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());}
    @ParameterizedTest @ValueSource(strings={"frame","ambiguous"}) void unresolvedFrameOrCorrespondenceStaysUnknown(String kind)throws Exception {var f=new WaterBridgeFixtures(chain(1),false,Map.of(-1,kind));f.coverage(SUPPORTED_PRESENT);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());}
    @ParameterizedTest @ValueSource(strings={"DD","AA","bifurcated"}) void independentDonorDirectionsDoNotConstrainPathDirection(String kind)throws Exception {
        var fs=new ArrayList<>(chain(1));
        if(kind.equals("DD"))fs.set(2,position(endpoint(6,true),"h",5,0,0));
        else if(kind.equals("AA")){fs.set(0,endpoint(0,false));fs.set(1,position(fs.get(1),"h2",2,0,0));}
        else {fs.set(0,position(endpoint(3,false),"o",3,0.2,0));fs.set(1,water(0,true));fs.set(2,position(endpoint(3,false),"o",3,-0.2,0));}
        var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);var r=f.result();assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText());
        if(kind.equals("bifurcated")){var path=r.path("paths").get(0).path("eventKeys");var hs=new HashSet<String>();for(var leg:r.path("legs"))for(var key:path)if(EventCoverageFixture.hash(leg.get("eventKey")).equals(key.asText()))hs.add(leg.get("hydrogen").toString());assertEquals(1,hs.size(),"same-H bifurcated candidate is preserved");}
    }
    @Test void directHydrogenBondDoesNotPruneWaterBridge()throws Exception {
        var fs=new ArrayList<>(chain(1));fs.set(1,position(position(position(fs.get(1),"o",1.8,0.8,0),"h1",2.6320502943,0.2452998038,0),"h2",1.8,1.8,0));fs.set(2,endpoint(3,false));
        var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);assertEquals("SUPPORTED_PRESENT",f.result().path("assessment").asText());
        var old=HbondCandidateFixtures.manifest();var inputs=new ArrayList<EvidenceEnvelope>();for(var e:f.artifacts)if(e.evidenceType().equals("athena:group-identities")){var n=JSON.readTree(e.readPayload());if(!n.path("definition").path("groupId").asText().equals("ATHENA.GROUP.WATER.NEUTRAL_H2")&&!n.path("componentReference").equals(node(f.state.components().get(1).identity())))inputs.add(e);}
        var req=new RuleRequest(f.state.binding(),old.key(),RuleRegistry.digest(old),List.of(),List.of(new totah.lab.gaia.structure.ResidueId("A",1,null)),List.of(new totah.lab.gaia.structure.ResidueId("A",3,null)),4.5,2,1000,10000);
        var direct=RuleAnalyzers.collector(old,req,BACKEND).analyze(f.state,inputs,Map.of()).getFirst();assertEquals("SUPPORTED_PRESENT",JSON.readTree(direct.measurements().get("payload")).path("assessment").asText());
    }
    @Test void evidenceOutsideExplicitPlanIsNotConsumed()throws Exception {var f=new WaterBridgeFixtures(1,false);var inputs=f.inputs();var other=new WaterBridgeFixtures(0,false);inputs.add(other.artifacts.get(0));
        // Different source under the same reference is an integrity conflict even when unselected.
        assertThrows(Exception.class,()->RuleAnalyzers.collector(f.manifest,f.request(),BACKEND).analyze(f.state,inputs,Map.of()));}
    @Test void requestedRadiusDoesNotCutOffWaterInventory()throws Exception {var f=new WaterBridgeFixtures(2,true);f.coverage(SUPPORTED_PRESENT);var r=f.request();var tiny=new RuleRequest(r.state(),r.manifestKey(),r.manifestSha256(),List.of(),List.of(),List.of(),0.1,r.maximumHops(),r.maximumNodes(),r.maximumCandidates());var result=RuleAnalyzers.collector(f.manifest,tiny,BACKEND).analyze(f.state,f.inputs(),Map.of()).getFirst();assertEquals("SUPPORTED_PRESENT",JSON.readTree(result.measurements().get("payload")).path("assessment").asText());}
    @Test void conflictingChemistrySourcesRemainUnresolved()throws Exception {
        var f=new WaterBridgeFixtures(1,false);var other=new WaterBridgeFixtures(chain(1),false,Map.of(1,"H"));
        var e=other.artifacts.stream().filter(x->{try{var n=JSON.readTree(x.readPayload());return n.path("definition").path("groupId").asText().equals("ATHENA.GROUP.WATER.NEUTRAL_H2")&&n.path("componentReference").equals(node(f.state.components().get(1).identity()));}catch(Exception ex){throw new RuntimeException(ex);}}).findFirst().orElseThrow();
        f.add("independent-contradictory-water-report",e.evidenceType(),e.readPayload(),e.method());f.coverage(SUPPORTED_PRESENT);var r=f.result();assertEquals("UNKNOWN_INCONCLUSIVE",r.path("assessment").asText());assertTrue(r.path("reasons").toString().contains("conflicting"));
    }
    @Test void identicalEvidenceIsIdempotent()throws Exception {var f=new WaterBridgeFixtures(1,false);f.coverage(SUPPORTED_PRESENT);var before=f.result();f.artifacts.add(f.artifacts.get(0));var after=f.result();assertEquals(before.path("paths"),after.path("paths"));assertEquals(before.path("legs"),after.path("legs"));}
    @Test void copiedPositiveMeasurementCannotOverrideRecomputation()throws Exception {var f=new WaterBridgeFixtures(1,false);var inputs=f.inputs();var collector=RuleAnalyzers.collector(f.manifest,f.request(),BACKEND);var n=(ObjectNode)JSON.readTree(collector.analyze(f.state,inputs,Map.of()).getFirst().measurements().get("payload"));n.put("complete",true);
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"water-forged"),"raw","athena:rule-measurements",SystemStateView.bytes(n),collector.method(),f.state.subject(),T,List.of("synthetic tampering witness")));assertThrows(Exception.class,()->RuleAnalyzers.evaluator(f.manifest,f.request()).analyze(f.state,inputs,Map.of()));}
    @Test void unselectedIndependentEvidenceDoesNotAffectResult()throws Exception {var f=new WaterBridgeFixtures(1,false);var inputs=f.inputs();var expected=RuleAnalyzers.collector(f.manifest,f.request(),BACKEND).analyze(f.state,inputs,Map.of()).getFirst();inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"unselected"),"unrelated","athena:event-source",new byte[]{1,2,3},ref(ScientificReference.Kind.METHOD,"unselected"),f.state.subject(),T,List.of()));assertEquals(expected,RuleAnalyzers.collector(f.manifest,f.request(),BACKEND).analyze(f.state,inputs,Map.of()).getFirst());}
    @Test void positiveWitnessSurvivesTraversalTruncation()throws Exception {
        var fs=new ArrayList<>(chain(2));fs.set(2,water(3,true));fs.set(3,endpoint(6,false));
        var f=new WaterBridgeFixtures(fs,false,Map.of());f.nodes=3;f.coverage(SUPPORTED_PRESENT);var r=f.result();
        assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText());assertFalse(r.path("complete").asBoolean());
        assertTrue(r.path("eventAnalysis").get(0).path("truncated").asBoolean());
    }
    @Test void simpleWaterPathsNeverRepeatWatersOrTransitOtherEndpoints()throws Exception {
        var f=new WaterBridgeFixtures(3,true);f.coverage(SUPPORTED_PRESENT);var r=f.result();assertFalse(r.path("paths").isEmpty());
        for(var p:r.path("paths")){var unique=new HashSet<String>();for(var w:p.path("waters")){assertTrue(unique.add(w.toString()));assertTrue(f.plan.path("waters").toString().contains(w.toString()));}assertEquals(p.path("waters").size()+1,p.path("eventKeys").size());}
    }
    @Test void carboxylatePartnerRemainsOutsideRoleDomain()throws Exception {
        var fs=new ArrayList<>(chain(1));fs.set(2,renamed(B01FunctionalGroupAcceptanceTest.fixture("carboxylate"),"a3",6,false));
        var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());
    }
    @Test void alteredScientificDefinitionCannotReplayApprovedWaterRule()throws Exception {
        var f=new WaterBridgeFixtures(1,false);var n=(ObjectNode)JSON.valueToTree(f.manifest);((ObjectNode)n.path("parameters").path("angle")).put("value","129");
        assertThrows(Exception.class,()->new RuleRegistry().register(RuleRegistry.decode(SystemStateView.bytes(n))));
    }
    @Test void pyridiniumPartnerRemainsOutsideReviewedDomain()throws Exception {
        var fs=new ArrayList<>(chain(1));fs.set(0,renamed(ChemicalRoleAcceptanceTest.chemical("pyridinium"),"a0",0,true));
        var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());
    }
    @Test void incompleteGraphWithZeroVisibleHDoesNotConcludeNonwater()throws Exception {
        var fs=new ArrayList<>(chain(1));var w=water(3,false);fs.set(1,new Fixture(w.graph(),Map.of("o",0)));
        var f=new WaterBridgeFixtures(fs,false,Map.of(1,"graph"));f.coverage(SUPPORTED_PRESENT);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());
    }
    @ParameterizedTest @ValueSource(strings={"HA","DA"}) void coincidentLegAtomsCannotSupportAbsence(String kind)throws Exception {
        var fs=new ArrayList<>(chain(1));fs.set(2,position(fs.get(2),"o",kind.equals("HA")?4:3,0,0));
        var f=new WaterBridgeFixtures(fs,false,Map.of());f.coverage(SUPPORTED_PRESENT);assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());
    }
    @Test void disconnectedSourceWaterCanShareAComponentContainer()throws Exception {
        var first=endpoint(0,true);var w=water(3,true);var aa=new ArrayList<>(first.graph().atoms());var bb=new ArrayList<>(first.graph().bonds());var hs=new TreeMap<>(first.hydrogens());
        for(var a:w.graph().atoms()){aa.add(new MolecularGraph.Atom("w_"+a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),a.coordinates(),a.properties()));hs.put("w_"+a.id(),w.hydrogens().get(a.id()));}
        for(var b:w.graph().bonds())bb.add(new MolecularGraph.Bond("w_"+b.id(),"w_"+b.firstAtomId(),"w_"+b.secondAtomId(),b.order(),b.aromatic(),b.stereochemistry(),b.properties()));
        var f=new WaterBridgeFixtures(List.of(new Fixture(new MolecularGraph(aa,bb,Map.of()),hs),endpoint(6,false)),false,Map.of());
        ((ArrayNode)f.plan.path("waters")).add(node(ref(1,"w_o")));f.coverage(SUPPORTED_PRESENT);var r=f.result();
        assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText());assertTrue(r.path("complete").asBoolean());
    }
}
