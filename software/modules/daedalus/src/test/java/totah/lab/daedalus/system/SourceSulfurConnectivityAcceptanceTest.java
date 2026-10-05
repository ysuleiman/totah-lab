package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.gaia.structure.*;
import totah.lab.gaia.graph.ResidueGraph;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.ref;

class SourceSulfurConnectivityAcceptanceTest {
    @TempDir Path temp;
    static RuleManifest model()throws Exception{return RuleRegistry.decode(Files.readAllBytes(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/ss-connectivity-v1/ATHENA.SULF.SS_CONNECTIVITY.rule.json")));}
    static Fixture source(boolean edge){var f=fixture("disulfide");return edge?f:new Fixture(new MolecularGraph(f.graph().atoms(),f.graph().bonds().stream().filter(b->!Set.of(b.firstAtomId(),b.secondAtomId()).equals(Set.of("a1","a2"))).toList(),f.graph().properties()),f.hydrogens());}
    static SystemStateView state(boolean edge){return system(List.of(source(edge).graph()),true,false);}
    static RuleRequest req(SystemStateView s)throws Exception{var m=model();return new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(ref(1,"a1"),ref(1,"a2")),List.of(),List.of(),4.5,0,100,100);}
    static List<EvidenceEnvelope> proof(SystemStateView s,boolean edge,String status)throws Exception{var c=coverage(s,source(edge));c.put("completeGraph",status);return List.of(envelope(s,"athena:group-source-coverage",c));}
    static SystemStateView structural(SystemStateView s,ConnectivityProvenance p,boolean addEdge,totah.lab.gaia.chemistry.BondOrder order){
        var bonds=new ArrayList<>(s.graph().structure().bonds());if(addEdge)bonds.add(new Bond(ref(1,"a1"),ref(1,"a2"),order));
        var structure=new Structure(s.graph().structure().getChains(),bonds,p);
        return new SystemStateView(s.identity(),ResidueGraph.from(structure),s.components(),s.sources(),s.cofactors(),s.charges(),s.frameQualified(),s.protonationQualified(),s.limitations());
    }
    static List<SystemGraphAnalyzer.Finding> run(SystemStateView s,List<EvidenceEnvelope> inputs,RuleRequest r)throws Exception {
        var before=SystemStateView.bytes(s.snapshot());var exchange=new EvidenceExchange();var restored=new ArrayList<EvidenceEnvelope>();
        for(var e:inputs){var bytes=exchange.encodeRecord(e);var decoded=(EvidenceEnvelope)exchange.decodeRecord(bytes);assertArrayEquals(bytes,exchange.encodeRecord(decoded));restored.add(decoded);}
        var result=RuleAnalyzers.evaluator(model(),r).analyze(s,restored,Map.of());
        assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));assertTrue(result.stream().allMatch(f->f.status()==EvidenceInterpretation.Status.NOT_EVALUATED));
        return result;
    }
    static List<SystemGraphAnalyzer.Finding> run(SystemStateView s,List<EvidenceEnvelope> inputs)throws Exception{return run(s,inputs,req(s));}
    static SystemGraphAnalyzer.Finding summary(List<SystemGraphAnalyzer.Finding> f){return f.stream().filter(x->x.id().equals("source-connectivity")).findFirst().orElseThrow();}
    static String assessment(List<SystemGraphAnalyzer.Finding> f){return summary(f).measurements().get("assessment");}
    @Test void listedEdgeWithoutCompletenessIsPositiveAndRetainsIdentity()throws Exception {
        var s=state(true);var result=run(s,List.of());assertEquals("SUPPORTED_PRESENT",assessment(result));
        var raw=summary(result).measurements().get("sourceAssertions");assertTrue(raw.contains("SINGLE"));assertTrue(raw.contains(source(true).graph().bonds().get(1).id()));
        assertFalse(summary(result).measurements().containsKey("distance"));
    }
    @Test void unknownSourceBondOrderIsNotRepaired()throws Exception {
        var s=structural(state(false),ConnectivityProvenance.EXPLICIT,true,totah.lab.gaia.chemistry.BondOrder.UNKNOWN);
        var r=run(s,List.of());assertEquals("SUPPORTED_PRESENT",assessment(r));assertTrue(summary(r).measurements().get("sourceAssertions").contains("UNKNOWN"));
    }
    @Test void independentCompleteGraphEstablishesNegative()throws Exception {var s=state(false);assertEquals("ABSENT_FALSE",assessment(run(s,proof(s,false,"SUPPORTED_PRESENT"))));}
    @TestFactory Stream<DynamicTest> incompleteCoverage(){return Stream.of("UNKNOWN_INCONCLUSIVE","NOT_EVALUATED","UNSUPPORTED","FAILED","ABSENT_FALSE").map(status->DynamicTest.dynamicTest(status,()->{var s=state(false);assertEquals("UNKNOWN_INCONCLUSIVE",assessment(run(s,proof(s,false,status))));}));}
    @Test void explicitAndPartialImportAloneAreNotAbsence()throws Exception {
        for(var p:ConnectivityProvenance.values()){var s=structural(state(false),p,false,totah.lab.gaia.chemistry.BondOrder.SINGLE);assertEquals("UNKNOWN_INCONCLUSIVE",assessment(run(s,List.of())));}
    }
    @Test void partialImportRetainsListedPositive()throws Exception {var s=structural(state(false),ConnectivityProvenance.PARTIAL,true,totah.lab.gaia.chemistry.BondOrder.SINGLE);assertEquals("SUPPORTED_PRESENT",assessment(run(s,List.of())));}
    @Test void inferredEdgeCannotBecomeAuthoritativePositiveOrCompleteNegative()throws Exception {var s=structural(state(false),ConnectivityProvenance.INFERRED,true,totah.lab.gaia.chemistry.BondOrder.SINGLE);assertEquals("UNKNOWN_INCONCLUSIVE",assessment(run(s,proof(s,false,"SUPPORTED_PRESENT"))));}
    @Test void conflictingAssertionsCoexist()throws Exception {
        var s=structural(state(false),ConnectivityProvenance.EXPLICIT,true,totah.lab.gaia.chemistry.BondOrder.UNKNOWN);var r=run(s,proof(s,false,"SUPPORTED_PRESENT"));
        assertEquals("UNKNOWN_INCONCLUSIVE",assessment(r));assertEquals(2,r.size());assertEquals("SUPPORTED_PRESENT",r.getFirst().measurements().get("assessment"));
        assertEquals("true",summary(r).measurements().get("conflictingAssertions"));assertTrue(summary(r).measurements().get("sourceAssertions").contains("COMPONENT_GRAPH"));
    }
    @Test void crossComponentAbsenceNeverInferred()throws Exception {
        var f=fixture("thiol");var s=system(List.of(f.graph(),f.graph()),true,false);var c=coverage(s,f);var a=envelope(s,"athena:group-source-coverage",c);
        var other=c.deepCopy();other.set("componentReference",JSON.valueToTree(s.components().get(1).identity()));var b=AromaticSystemAcceptanceTest.wrap(s,"athena:group-source-coverage",other,a.method(),"other-component-coverage");
        var m=model();var r=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(ref(1,"a1"),ref(2,"a1")),List.of(),List.of(),4.5,0,100,100);
        assertEquals("UNKNOWN_INCONCLUSIVE",assessment(run(s,List.of(a,b),r)));
    }
    @Test void crossComponentListedEdgeSurvives()throws Exception {
        var f=fixture("thiol");var raw=system(List.of(f.graph(),f.graph()),true,false);var bonds=new ArrayList<>(raw.graph().structure().bonds());bonds.add(new Bond(ref(1,"a1"),ref(2,"a1"),totah.lab.gaia.chemistry.BondOrder.SINGLE));
        var s=new SystemStateView(raw.identity(),ResidueGraph.from(new Structure(raw.graph().structure().getChains(),bonds,ConnectivityProvenance.EXPLICIT)),raw.components(),raw.sources(),raw.cofactors(),raw.charges(),true,false,raw.limitations());
        var m=model();var r=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(ref(1,"a1"),ref(2,"a1")),List.of(),List.of(),4.5,0,100,100);
        assertEquals("SUPPORTED_PRESENT",assessment(run(s,List.of(),r)));
    }
    @TestFactory Stream<DynamicTest> tampering(){return Stream.of("state","component","field","status","atom").map(kind->DynamicTest.dynamicTest(kind,()->{
        var s=state(false);var c=coverage(s,source(false));switch(kind){case "state"->((ObjectNode)c.get("stateBinding")).put("stateSha256","0".repeat(64));case "component"->((ObjectNode)c.get("componentReference")).put("id","missing");case "field"->c.put("fake",true);case "status"->c.put("completeGraph","TRUE");case "atom"->((ObjectNode)c.get("atomState")).putObject("missing");}
        assertThrows(IllegalArgumentException.class,()->run(s,List.of(envelope(s,"athena:group-source-coverage",c))));
    }));}
    @Test void hashTamperingFailsAtExistingEnvelopeBoundary()throws Exception {
        var s=state(false);var e=proof(s,false,"SUPPORTED_PRESENT").getFirst();
        assertThrows(IllegalArgumentException.class,()->new EvidenceEnvelope(e.reference(),e.evidenceType(),e.payloadFormat(),e.payloadVersion(),e.payloadBase64(),e.artifactPath(),"0".repeat(64),e.provenance(),e.method(),e.context(),e.subjects(),e.qualifications(),e.limitations(),e.recordedAt()));
    }
    @TestFactory Stream<DynamicTest> mappingCases(){return Stream.of("incomplete","ambiguous","badTarget").map(kind->DynamicTest.dynamicTest(kind,()->{
        var raw=state(false);var c=raw.components().getFirst();var map=new TreeMap<>(c.correspondenceAlternatives().getFirst());if(kind.equals("incomplete"))map.remove("a0");if(kind.equals("badTarget"))map.put("a1",ref(1,"MISSING"));
        var component=new SystemStateView.Component(c.identity(),c.chemistry(),kind.equals("ambiguous")?List.of(map,map):List.of(map),c.limitations());
        var s=new SystemStateView(raw.identity(),raw.graph(),List.of(component),raw.sources(),raw.cofactors(),raw.charges(),true,false,raw.limitations());
        if(kind.equals("badTarget"))assertThrows(IllegalArgumentException.class,()->run(s,proof(s,false,"SUPPORTED_PRESENT")));
        else assertEquals("UNKNOWN_INCONCLUSIVE",assessment(run(s,proof(s,false,"SUPPORTED_PRESENT"))));
    }));}
    @Test void boundRequestAndBudgetFailClosed()throws Exception {
        var s=state(false);var r=req(s);var m=model();var small=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),r.atoms(),List.of(),List.of(),4.5,0,2,100);
        assertEquals("UNKNOWN_INCONCLUSIVE",assessment(run(s,proof(s,false,"SUPPORTED_PRESENT"),small)));
        assertThrows(IllegalArgumentException.class,()->run(state(true),List.of(),r));
    }
    @Test void sourcePermutationAndRepeatedEvidenceAreDeterministic()throws Exception {
        var s=state(false);var inputs=proof(s,false,"SUPPORTED_PRESENT");assertEquals(run(s,inputs),run(s,List.of(inputs.getFirst(),inputs.getFirst())));
        var r=req(s);var reversed=new RuleRequest(s.binding(),r.manifestKey(),r.manifestSha256(),List.of(r.atoms().get(1),r.atoms().get(0)),List.of(),List.of(),4.5,0,100,100);
        assertEquals(run(s,inputs),run(s,inputs,reversed));
    }
    @Test void exactHistoricalWitnessChangesOnlyOnNewPath()throws Exception {
        var raw=SystemQualificationAcceptanceTest.state(0,false);
        var structure=new Structure(raw.graph().structure().getChains(),List.of(new Bond(RuleRegistryAcceptanceTest.atom(1,"C1"),RuleRegistryAcceptanceTest.atom(95,"H1"),totah.lab.gaia.chemistry.BondOrder.SINGLE)),
                new ConnectivityMetadata(ConnectivityProvenance.EXPLICIT,List.of("Synthetic imported listed edge only; no assertion of exhaustive sulfur connectivity")));
        var s=new SystemStateView(raw.identity(),ResidueGraph.from(structure),raw.components(),raw.sources(),Set.of(),raw.charges(),true,false,List.of("No pair-scoped completeness declaration"));
        var historical=ConnectivityCoverageCharacterizationTest.observe(ConnectivityProvenance.EXPLICIT);
        assertArrayEquals(SystemStateView.bytes(historical.get("sourceSnapshot")),SystemStateView.bytes(s.snapshot()));
        assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,historical.get("legacyStatus"));
        var m=model();var r=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(RuleRegistryAcceptanceTest.atom(17,"SG"),RuleRegistryAcceptanceTest.atom(48,"SG")),List.of(),List.of(),4.5,2,100,100);
        assertEquals("UNKNOWN_INCONCLUSIVE",assessment(run(s,List.of(),r)));
    }
    @Test void malformedTopologyFailsWithoutChangingInputs()throws Exception {
        var raw=state(false);var component=raw.components().getFirst();var bonds=new ArrayList<>(component.chemistry().bonds());bonds.add(bonds.getFirst());
        var graph=new MolecularGraph(component.chemistry().atoms(),bonds,component.chemistry().properties());
        var c=new SystemStateView.Component(component.identity(),graph,component.correspondenceAlternatives(),component.limitations());
        var s=new SystemStateView(raw.identity(),raw.graph(),List.of(c),raw.sources(),raw.cofactors(),raw.charges(),true,false,raw.limitations());var before=SystemStateView.bytes(s.snapshot());
        assertThrows(IllegalArgumentException.class,()->run(s,List.of()));assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));
    }
    @Test void conflictingCompletenessSourcesRemainPreservedAndNonnegative()throws Exception {
        var s=state(false);var complete=proof(s,false,"SUPPORTED_PRESENT").getFirst();var c=coverage(s,source(false));c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
        var unknown=AromaticSystemAcceptanceTest.wrap(s,"athena:group-source-coverage",c,complete.method(),"different-source");
        var a=run(s,List.of(complete,unknown));assertEquals("UNKNOWN_INCONCLUSIVE",assessment(a));assertEquals(a,run(s,List.of(unknown,complete)));
        assertEquals(2,JSON.readTree(summary(a).measurements().get("coverageSources")).size());
    }
    @Test void journalPreservesSourceAndInterpretation()throws Exception {
        var s=state(false);var inputs=proof(s,false,"SUPPORTED_PRESENT");var catalog=new EvidenceSnapshotCatalog(temp);
        var p=RuleRegistryAcceptanceTest.pipeline().run(catalog,Optional.empty(),s,inputs,Map.of(),List.of(RuleAnalyzers.evaluator(model(),req(s))),RuleRegistryAcceptanceTest.ref(ScientificReference.Kind.ACTIVITY,"ss-connectivity"),RuleRegistryAcceptanceTest.T);
        var history=catalog.read(p.catalogSnapshot()).orElseThrow().history();assertTrue(history.envelopes().containsKey(inputs.getFirst().reference()));
        assertTrue(history.interpretations().values().stream().anyMatch(i->"ABSENT_FALSE".equals(i.measurements().get("assessment"))));
    }
    public static void main(String[] args)throws Exception {var s=state(false);Files.write(Path.of(args[0]),SystemStateView.bytes(Map.of("withoutCoverage",run(s,List.of()),"complete",run(s,proof(s,false,"SUPPORTED_PRESENT")),"positive",run(state(true),List.of()))));}
}
