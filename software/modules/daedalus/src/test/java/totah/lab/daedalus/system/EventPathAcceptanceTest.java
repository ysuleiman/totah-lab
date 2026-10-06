package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.mnemosyne.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;
import static totah.lab.daedalus.system.EventCoverageFixture.*;

class EventPathAcceptanceTest {
    static EventCoverageFixture path(String from,String to,boolean covered,boolean qualified,int length)throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var keys=List.of(f.key(b,"a","b","one"),f.key(b,"b","c","one"),f.key(b,"c","a","one"));
        var events=new ArrayList<ObjectNode>();for(var k:keys){var e=f.event(b,k,SUPPORTED_PRESENT,false);if(!qualified)((ObjectNode)e.path("observations").get(0)).putNull("qualification");events.add(e);}f.state(b,events);
        f.plan.put("operation","SIMPLE_PATHS");var p=f.plan.putObject("path");p.set("start",f.subject(b,from));p.set("end",f.subject(b,to));p.put("fromRole","from");p.put("toRole","to");p.put("maximumLength",length);var coverage=p.putArray("coverage");
        if(covered)coverage.add(f.source(b,hash(Map.of("events",new TreeSet<>(keys.stream().map(EventCoverageFixture::hash).toList()),"fromRole","from","toRole","to")),SUPPORTED_PRESENT,true));
        return f;
    }
    @Test void directedCyclicEvidenceYieldsSimplePathsOnly()throws Exception {
        var f=path("a","c",true,true,4);var r=f.result().path("result");assertEquals(1,r.path("paths").size());assertEquals(2,r.path("paths").get(0).size());assertTrue(r.path("complete").asBoolean());assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText());
    }
    @Test void noPathRequiresCompleteCoverage()throws Exception {
        assertEquals("ABSENT_FALSE",path("a","d",true,true,4).result().path("result").path("assessment").asText());
        assertEquals("UNKNOWN_INCONCLUSIVE",path("a","d",false,true,4).result().path("result").path("assessment").asText());
    }
    @Test void unqualifiedPositiveCannotBecomeTypedPath()throws Exception {
        var r=path("a","c",true,false,4).result().path("result");assertTrue(r.path("paths").isEmpty());assertEquals("UNKNOWN_INCONCLUSIVE",r.path("assessment").asText());
    }
    @Test void shorterExplicitScopeIsNotGlobalNoPath()throws Exception {
        var r=path("a","c",true,true,1).result().path("result");assertEquals("ABSENT_FALSE",r.path("assessment").asText());assertEquals(1,r.path("requestedMaximumLength").asInt());
    }
    @Test void requestTraversalCapMakesNoPathInconclusive()throws Exception {
        var f=path("a","d",true,true,20);var r=f.result().path("result");assertTrue(r.path("truncated").asBoolean());assertFalse(r.path("complete").asBoolean());assertEquals("UNKNOWN_INCONCLUSIVE",r.path("assessment").asText());
    }
    @ParameterizedTest @ValueSource(strings={"SUPPORTED_PRESENT","ABSENT_FALSE","FAILED","MISSING"})
    void unpairedPreservesPartnerCoverage(String status)throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");
        f.state(b,status.equals("MISSING")?List.of():List.of(f.event(b,k,EvidenceInterpretation.Status.valueOf(status),true)));f.projection("partners",b,List.of(k),true);f.plan.put("operation","UNPAIRED");
        ((ObjectNode)f.plan.path("projections").get(0).path("members").get(0)).set("feature",f.subject(b,"a"));
        var member=(ObjectNode)f.plan.path("projections").get(0).path("members").get(0);
        ((ArrayNode)member.get("coverage")).removeAll().add(f.source(b,hash(Map.of("projection","partners","events",new TreeSet<>(List.of(hash(k))),"feature",f.subject(b,"a"),"profileDefinitionSha256",k.get("definitionSha256").asText())),SUPPORTED_PRESENT,true));
        String expected=status.equals("SUPPORTED_PRESENT")?"ABSENT_FALSE":status.equals("ABSENT_FALSE")?"SUPPORTED_PRESENT":"UNKNOWN_INCONCLUSIVE";
        assertEquals(expected,f.result().path("assessment").asText());
    }
    @Test void separateDuplicateEventRecordsMergeContradictoryAssertions()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");f.state(b,List.of(f.event(b,k,SUPPORTED_PRESENT,false),f.event(b,k,ABSENT_FALSE,true)));f.projection("p",b,List.of(k),true);
        assertEquals(1,f.result().path("projections").path("p").path("conflicting").asInt());
    }
    @Test void duplicatedRecordsDoNotMultiplyDistinctEventCount()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");var e=f.event(b,k,SUPPORTED_PRESENT,false);f.state(b,List.of(e,e.deepCopy()));f.projection("p",b,List.of(k),true);
        var raw=f.result().path("distinctEventCountsByState").elements().next();assertEquals(1,raw.path("selected").asInt());assertEquals(1,raw.path("present").asInt());
    }
    @Test void unqualifiedNegativeCannotEstablishNoPath()throws Exception {
        var f=path("a","d",true,true,4);
        EventIntegrityAcceptanceTest.changeSet(f,n->{
            var e=(ObjectNode)n.path("events").get(0);
            try{var negative=f.event(f.state.binding(),(ObjectNode)e.get("key"),ABSENT_FALSE,true);negative.path("observations").forEach(o->((ObjectNode)o).putNull("qualification"));e.set("observations",negative.get("observations"));}catch(Exception x){throw new IllegalStateException(x);}
        });
        assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("result").path("assessment").asText());
    }
    @Test void undirectedEdgesPermitReverseTraversal()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");k.put("direction","UNDIRECTED");f.state(b,List.of(f.event(b,k,SUPPORTED_PRESENT,false)));
        f.plan.put("operation","SIMPLE_PATHS");var p=f.plan.putObject("path");p.set("start",f.subject(b,"b"));p.set("end",f.subject(b,"a"));p.put("fromRole","from");p.put("toRole","to");p.put("maximumLength",1);p.putArray("coverage");
        assertEquals(1,f.result().path("result").path("paths").size());
    }
    @Test void candidateBudgetTruncationIsNotAnAbsence()throws Exception {
        var f=path("a","d",true,true,4);var old=f.request();
        var request=new totah.lab.athena.system.rules.RuleRequest(old.state(),old.manifestKey(),old.manifestSha256(),List.of(),List.of(),List.of(),4.5,8,100,1);
        var r=JSON.readTree(totah.lab.athena.system.rules.RuleAnalyzers.evaluator(f.manifest,request).analyze(f.state,f.inputs(),Map.of()).getFirst().measurements().get("payload")).path("result");
        assertTrue(r.path("truncated").asBoolean());assertEquals("UNKNOWN_INCONCLUSIVE",r.path("assessment").asText());
    }
    @Test void emptyPartnerScopeRequiresFeatureAndProfileBoundCoverage()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();f.state(b,List.of());f.projection("p",b,List.of(),false);f.plan.put("operation","UNPAIRED");
        var m=(ObjectNode)f.plan.path("projections").get(0).path("members").get(0);m.set("feature",f.subject(b,"a"));assertEquals("UNKNOWN_INCONCLUSIVE",f.result().path("assessment").asText());
        ((ArrayNode)m.get("coverage")).add(f.source(b,hash(Map.of("projection","p","events",new TreeSet<String>(),"feature",m.get("feature"),"profileDefinitionSha256",totah.lab.athena.system.rules.RuleRegistry.digest(f.sourceManifest))),SUPPORTED_PRESENT,true));
        assertEquals("SUPPORTED_PRESENT",f.result().path("assessment").asText());
    }
}
