package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.EventCoverageFixture.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

class EventIntegrityAcceptanceTest {
    static EventCoverageFixture positive()throws Exception {var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");f.state(b,List.of(f.event(b,k,SUPPORTED_PRESENT,false)));f.projection("p",b,List.of(k),true);return f;}
    static void changeSet(EventCoverageFixture f,java.util.function.Consumer<ObjectNode> edit)throws Exception {
        var old=f.artifacts.stream().filter(e->e.evidenceType().equals("athena:typed-event-set")).findFirst().orElseThrow();
        var node=(ObjectNode)JSON.readTree(old.readPayload());edit.accept(node);var replacement=f.add("replacement-set","athena:typed-event-set",totah.lab.athena.system.SystemStateView.bytes(node));
        ((ObjectNode)f.plan.path("states").get(0)).set("eventSet",replacement);
    }
    @ParameterizedTest @ValueSource(strings={"schema","state","profile","definition","role-state","duplicate-role","unknown-field"})
    void validHashesCannotHideInvalidBindings(String failure)throws Exception {
        var f=positive();changeSet(f,n->{switch(failure){
            case "schema"->n.put("schema","unknown/1");
            case "state"->((ObjectNode)n.get("stateBinding")).put("chemicalSha256","0".repeat(64));
            case "profile"->((ObjectNode)n.path("profiles").get(0)).put("implementationVersion","wrong");
            case "definition"->((ObjectNode)n.path("events").get(0).get("key")).put("definitionSha256","0".repeat(64));
            case "role-state"->((ObjectNode)n.path("events").get(0).path("key").path("roles").get(0).path("entity").get("state")).put("id","foreign");
            case "duplicate-role"->((ObjectNode)n.path("events").get(0).path("key").path("roles").get(1)).put("role","from");
            case "unknown-field"->n.put("inferMissingNegatives",true);
        }});assertThrows(Exception.class,f::result);
    }
    @Test void conflictingCoverageNeverEstablishesAbsence()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var k=f.key(b,"a","b","one");var e=f.event(b,k,ABSENT_FALSE,true);
        ((ArrayNode)e.path("observations").get(0).path("coverage")).add(f.source(b,hash(k),ABSENT_FALSE,true));f.state(b,List.of(e));f.projection("p",b,List.of(k),true);assertEquals(0,f.result().path("projections").path("p").path("eligible").asInt());
    }
    @Test void unsupportedProfileInputIsPreservedRatherThanSilentlyTyped()throws Exception {
        var f=positive();changeSet(f,n->((ObjectNode)n.path("profiles").get(0)).put("implementationId","unregistered"));assertThrows(Exception.class,f::finding);
    }
    @Test void inputOrderPermutationHasByteIdenticalOutput()throws Exception {
        var f=positive();var in=f.inputs();var evaluator=RuleAnalyzers.evaluator(f.manifest,f.request());var expected=evaluator.analyze(f.state,in,Map.of()).getFirst().measurements().get("payload");
        Collections.reverse(in);assertEquals(expected,evaluator.analyze(f.state,in,Map.of()).getFirst().measurements().get("payload"));
    }
    @Test void partialLegacySourceKeepsFailureAndUnknownSeparate()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var a=f.key(b,"a","b","one"); // individual statuses retained in provenance
        f.state(b,List.of(f.event(b,a,FAILED,false)));f.projection("p",b,List.of(a),false);String text=f.result().toString();assertTrue(text.contains("FAILED"));assertTrue(text.contains("UNKNOWN_INCONCLUSIVE"));assertFalse(text.contains("\"absent\":1"));
    }
    @Test void multipleProfilesRemainSeparatelyAttributedInOneState()throws Exception {
        var f=new EventCoverageFixture();var b=f.state.binding();var first=f.sourceManifest;var a=f.key(b,"a","b","one");var e1=f.event(b,a,SUPPORTED_PRESENT,false);
        f.sourceManifest=qualified(RuleRegistry.decode(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/SULF.SS.001--ATHENA_SOURCE_MEASUREMENTS.rule.json"))));
        var z=f.key(b,"b","c","one");var e2=f.event(b,z,SUPPORTED_PRESENT,false);f.state(b,List.of(e1,e2));
        var firstPin=f.add("first-profile","athena:event-source",totah.lab.athena.system.SystemStateView.bytes(first));
        changeSet(f,n->((ArrayNode)n.get("profiles")).add(tree(Map.of("definitionSha256",RuleRegistry.digest(first),"implementationId",first.implementationId(),"implementationVersion",first.implementationVersion(),"manifest",firstPin))));
        f.projection("p",b,List.of(a,z),false);var result=f.result();assertEquals(2,result.path("distinctEventCountsByState").elements().next().path("present").asInt());
        assertTrue(result.toString().contains(RuleRegistry.digest(first)));assertTrue(result.toString().contains(RuleRegistry.digest(f.sourceManifest)));
    }
    @Test void displayStringCollisionCannotDropExplicitInput()throws Exception {
        var f=positive();var template=f.artifacts.getFirst();
        var a=new ScientificReference(ScientificReference.Kind.EVIDENCE_ENVELOPE,"n, id=x","y","1");
        var b=new ScientificReference(ScientificReference.Kind.EVIDENCE_ENVELOPE,"n","x, id=y","1");assertNotEquals(a,b);assertEquals(a.toString(),b.toString());
        for(var reference:List.of(a,b))f.artifacts.add(new EvidenceEnvelope(reference,template.evidenceType(),template.payloadFormat(),template.payloadVersion(),template.payloadBase64(),template.artifactPath(),template.payloadSha256(),template.provenance(),template.method(),template.context(),template.subjects(),template.qualifications(),template.limitations(),template.recordedAt()));
        var routed=EventAssessmentInputs.resolve(f.request(),f.state,f.inputs());
        assertTrue(routed.stream().anyMatch(e->e.reference().equals(a)));assertTrue(routed.stream().anyMatch(e->e.reference().equals(b)));
    }
}
