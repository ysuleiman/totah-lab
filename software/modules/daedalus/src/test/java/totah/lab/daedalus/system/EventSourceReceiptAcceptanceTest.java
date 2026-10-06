package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.EventCoverageFixture.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;

class EventSourceReceiptAcceptanceTest {
    @TempDir Path temporary;
    @Test void explicitCurrentReceiptClosureIsVerifiedAndCannotBeBorrowed()throws Exception {
        var source=DirectAssessmentExecutionAcceptanceTest.run(temporary,"valid",true);
        assertEquals(1,source.calls());
        var envelopes=source.history().envelopes().values();
        var manifest=envelopes.stream().filter(e->e.evidenceType().equals("athena:rule-manifest")).reduce((a,b)->b).orElseThrow();
        var receipt=envelopes.stream().filter(e->e.evidenceType().equals("athena:rule-qualification-receipt")).findFirst().orElseThrow();
        var context=envelopes.stream().filter(e->e.evidenceType().equals("athena:rule-policy-context")).findFirst().orElseThrow();
        var f=new EventCoverageFixture();f.sourceManifest=RuleRegistry.decode(manifest.readPayload());f.sourceTime=AT;
        f.artifacts.addAll(envelopes);
        var b=f.state.binding();var key=f.key(b,"a","b","one");var event=f.event(b,key,EvidenceInterpretation.Status.SUPPORTED_PRESENT,false);
        ((ObjectNode)event.path("observations").get(0)).set("qualification",tree(Map.of("receipt",Map.of("reference",receipt.reference(),"sha256",receipt.payloadSha256()),"context",Map.of("reference",context.reference(),"sha256",context.payloadSha256()))));
        f.state(b,List.of(event));f.plan.put("operation","SIMPLE_PATHS");var p=f.plan.putObject("path");p.set("start",f.subject(b,"a"));p.set("end",f.subject(b,"b"));p.put("fromRole","from");p.put("toRole","to");p.put("maximumLength",1);p.putArray("coverage");
        assertEquals("SUPPORTED_PRESENT",f.result().path("result").path("assessment").asText());
        f.artifacts.remove(receipt);assertThrows(Exception.class,f::result,"historical catalog receipt is not an implicitly available input");
        f.artifacts.add(receipt);
        EventIntegrityAcceptanceTest.changeSet(f,n->((ObjectNode)n.path("events").get(0).path("observations").get(0).path("qualification").get("receipt")).put("sha256","0".repeat(64)));
        assertThrows(Exception.class,f::result);
    }
}
