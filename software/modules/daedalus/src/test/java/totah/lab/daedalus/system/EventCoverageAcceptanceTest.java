package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;
import static totah.lab.daedalus.system.EventCoverageFixture.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

class EventCoverageAcceptanceTest {
    @ParameterizedTest @EnumSource(EvidenceInterpretation.Status.class)
    void sixStatusesNeverCollapseIntoFalse(EvidenceInterpretation.Status status)throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");f.state(b,List.of(f.event(b,k,status,true)));f.projection("p",b,List.of(k),true);
        var p=f.result().path("projections").path("p");assertEquals(status==SUPPORTED_PRESENT?1:0,p.path("present").asInt());assertEquals(status==ABSENT_FALSE?1:0,p.path("absent").asInt());
        assertEquals(status==SUPPORTED_PRESENT||status==ABSENT_FALSE?1:0,p.path("eligible").asInt());
        assertTrue(p.path("states").elements().next().path("originalStatuses").toString().contains(status.name()));
        if(status!=SUPPORTED_PRESENT&&status!=ABSENT_FALSE)assertTrue(p.path("fraction").isNull());
    }
    @Test void absentRequiresBothEventAndProjectionCoverage()throws Exception {
        for(boolean eventCoverage:List.of(false,true))for(boolean projectionCoverage:List.of(false,true)) {
            var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");f.state(b,List.of(f.event(b,k,ABSENT_FALSE,eventCoverage)));f.projection("p",b,List.of(k),projectionCoverage);
            assertEquals(eventCoverage&&projectionCoverage?1:0,f.result().path("projections").path("p").path("absent").asInt());
        }
    }
    @Test void missingLegacyEntryRemainsUnknown()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");f.state(b,List.of());f.projection("p",b,List.of(k),true);
        var p=f.result().path("projections").path("p");assertEquals(0,p.path("eligible").asInt());assertTrue(p.path("fraction").isNull());
    }
    @Test void conflictingObservationsRemainUnresolvedAndVisible()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");var e=f.event(b,k,SUPPORTED_PRESENT,false);
        ((ArrayNode)e.path("observations")).add(f.event(b,k,ABSENT_FALSE,true).path("observations").get(0));f.state(b,List.of(e));f.projection("p",b,List.of(k),true);
        var p=f.result().path("projections").path("p");assertEquals(1,p.path("conflicting").asInt());assertEquals(0,p.path("eligible").asInt());assertTrue(p.toString().contains("ABSENT_FALSE"));
    }
    @Test void duplicateEvidenceAndRepeatedSelectionsAreIdempotent()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");var e=f.event(b,k,SUPPORTED_PRESENT,false);f.state(b,List.of(e,e.deepCopy()));f.projection("p",b,List.of(k,k.deepCopy()),true);
        ((ArrayNode)f.plan.get("states")).add(f.plan.get("states").get(0).deepCopy());f.artifacts.add(f.artifacts.get(0));
        assertEquals(1,f.result().path("projections").path("p").path("present").asInt());
    }
    @Test void alternativesRemainDistinctEvents()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var a=f.key(b,"a","b","one");var z=f.key(b,"a","b","two");f.state(b,List.of(f.event(b,a,SUPPORTED_PRESENT,false),f.event(b,z,ABSENT_FALSE,true)));
        f.projection("one",b,List.of(a),true);f.projection("two",b,List.of(z),true);var r=f.result();assertEquals(1,r.path("projections").path("one").path("present").asInt());assertEquals(1,r.path("projections").path("two").path("absent").asInt());
    }
    @Test void commonStateCooccurrenceExcludesAsymmetricMissingness()throws Exception {
        var f=new EventCoverageFixture();f.plan.put("operation","COOCCURRENCE_COUNTS");var b=f.state.binding();
        for(int i=0;i<3;i++) {
            var s=new SystemStateView.Binding(ref(ScientificReference.Kind.SUBJECT,"state"+i),b.stateSha256(),b.chemicalSha256(),b.coordinateSha256(),b.correspondenceSha256());
            var a=f.key(s,"a","b","one");var z=f.key(s,"b","c","one");f.state(s,List.of(f.event(s,a,i==1?ABSENT_FALSE:SUPPORTED_PRESENT,true),f.event(s,z,i==2?FAILED:SUPPORTED_PRESENT,true)));
            f.projection("a",s,List.of(a),true);f.projection("z",s,List.of(z),true);
        }
        var c=f.result().path("cooccurrence");assertEquals(2,c.path("eligible").asInt());assertEquals(1,c.path("joint").asInt());assertEquals(1,c.path("excludedStates").size());assertEquals(1,c.path("contingency").path("11").asInt());assertEquals(1,c.path("contingency").path("01").asInt());
    }
    @Test void independentEnsemblesAreNotPairedByIndex()throws Exception {
        var f=new EventCoverageFixture();f.plan.put("operation","COOCCURRENCE_COUNTS");var b=f.state.binding();
        for(int i=0;i<2;i++){var s=new SystemStateView.Binding(ref(ScientificReference.Kind.SUBJECT,"independent"+i),b.stateSha256(),b.chemicalSha256(),b.coordinateSha256(),b.correspondenceSha256());var k=f.key(s,"a","b","one");f.state(s,List.of(f.event(s,k,SUPPORTED_PRESENT,false)));f.projection("p"+i,s,List.of(k),true);}
        assertEquals(0,f.result().path("cooccurrence").path("eligible").asInt());assertTrue(f.result().path("cooccurrence").path("fraction").isNull());
    }
    @Test void explicitRoutingNeverUsesInheritedOrUnselectedEvidence()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");f.state(b,List.of(f.event(b,k,SUPPORTED_PRESENT,false)));f.projection("p",b,List.of(k),true);var inputs=f.inputs();
        var missing=new ArrayList<>(inputs);missing.remove(0);assertThrows(Exception.class,()->EventAssessmentInputs.resolve(f.request(),f.state,missing));
        var routed=EventAssessmentInputs.resolve(f.request(),f.state,inputs);assertFalse(routed.isEmpty());
        assertThrows(Exception.class,()->EventAssessmentInputs.resolve(f.request(),f.state,List.of()));
        Collections.reverse(inputs);assertEquals(routed,EventAssessmentInputs.resolve(f.request(),f.state,inputs));
    }
    @Test void hashTamperingFailsClosed()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();f.state(b,List.of());f.projection("p",b,List.of(),false);var inputs=f.inputs();
        ((ObjectNode)f.plan.get("states").get(0).get("eventSet")).put("sha256","0".repeat(64));assertThrows(Exception.class,f::result);
    }
    @Test void evidenceRoundtripAndInputImmutability(@org.junit.jupiter.api.io.TempDir Path dir)throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");f.state(b,List.of(f.event(b,k,SUPPORTED_PRESENT,false)));f.projection("p",b,List.of(k),true);
        var inputs=f.inputs();var bytes=inputs.stream().map(e->{try{return e.readPayload();}catch(Exception x){throw new IllegalStateException(x);}}).toList();
        var catalog=new EvidenceSnapshotCatalog(dir);var pub=pipeline().run(catalog,Optional.empty(),f.state,inputs,Map.of(),List.of(RuleAnalyzers.evaluator(f.manifest,f.request())),ref(ScientificReference.Kind.ACTIVITY,"event-persist"),T);
        var history=catalog.read(pub.catalogSnapshot()).orElseThrow().history();for(int i=0;i<inputs.size();i++)assertArrayEquals(bytes.get(i),history.envelopes().get(inputs.get(i).reference()).readPayload());
        assertTrue(history.interpretations().values().stream().anyMatch(i->i.measurements().getOrDefault("payload","").contains("athena-event-analysis/1")));
    }
    public static void main(String[] args)throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");f.state(b,List.of(f.event(b,k,SUPPORTED_PRESENT,false)));f.projection("p",b,List.of(k),true);
        var results=new TreeMap<String,Object>();results.put("counts",f.result());f.plan.put("operation","COOCCURRENCE_COUNTS");results.put("cooccurrence",f.result());
        results.put("paths",EventPathAcceptanceTest.path("a","c",true,true,4).result());
        f.plan.put("operation","UNPAIRED");var member=(ObjectNode)f.plan.path("projections").get(0).path("members").get(0);member.set("feature",f.subject(b,"a"));((com.fasterxml.jackson.databind.node.ArrayNode)member.get("coverage")).removeAll();results.put("unpaired",f.result());
        Files.writeString(Path.of(args[0]),canonical(results));
    }
}
