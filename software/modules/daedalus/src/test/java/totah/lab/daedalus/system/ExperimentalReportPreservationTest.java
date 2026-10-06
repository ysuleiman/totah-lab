package totah.lab.daedalus.system;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.mnemosyne.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/** V19–V22: external evidence preservation; deliberately no scientific metric evaluator. */
class ExperimentalReportPreservationTest {
    @TempDir Path temp;
    static final EvidenceExchange EXCHANGE=new EvidenceExchange();
    static final Instant TIME=Instant.parse("2026-10-06T00:00:00Z");
    static ScientificReference ref(ScientificReference.Kind k,String id){return new ScientificReference(k,"external-validation-fixture",id,"1");}
    static EvidenceEnvelope report(String capability,String id,String content,String state) {
        byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
        return new EvidenceEnvelope(ref(EVIDENCE_ENVELOPE,id),"external-validation:"+capability,"application/octet-stream","source-format-uninterpreted",
                Optional.of(Base64.getEncoder().encodeToString(bytes)),Optional.empty(),EvidenceExchange.sha256(bytes),
                new Observation.Provenance(ref(SOURCE,"synthetic-source"),ref(ARTIFACT,id),ref(RECEIPT,"fixture-ingestion"),ref(METHOD,"external-tool-version"),"synthetic source locator; not experimental results",List.of()),
                ref(METHOD,"opaque-ingestion"),ref(CONTEXT,state),List.of(new EvidenceSubject(ref(CONTEXT,state),"coordinate-state",state,List.of())),
                List.of("source bytes preserved without parsing"),List.of("No Athena-native validation, metric interpretation or positive structural certification"),TIME);
    }
    static EvidenceInterpretation assessment(EvidenceEnvelope e,String id,EvidenceInterpretation.Status status) throws Exception {
        return new EvidenceInterpretation(ref(EVIDENCE_INTERPRETATION,id),List.of(new EvidenceInterpretation.Input(e.reference(),EXCHANGE.contentDigest(e))),
                ref(METHOD,"no-metric-evaluator"),Map.of(),e.subjects(),status,Map.of(),List.of("Opaque external report; native metric was not evaluated"),List.of(),Optional.empty(),TIME);
    }
    static EvidenceExchange.Snapshot snapshot(String id,EvidenceHistory h) throws Exception {
        return EXCHANGE.snapshot(ref(SNAPSHOT,id),ref(ACTIVITY,"ingestion"),TIME,Optional.empty(),h);
    }
    @ParameterizedTest @ValueSource(strings={"V19","V20","V21","V22"})
    void sourceBytesAndProvenanceSurviveAdmissionCatalogAndQueries(String capability) throws Exception {
        var e=report(capability,"report","SYNTHETIC\u0000 unknown report\r\nπ","state-a");var h=new EvidenceHistory().append(e).append(assessment(e,"not-evaluated",EvidenceInterpretation.Status.NOT_EVALUATED));
        var s=snapshot(capability,h);var pin=new EvidenceAdmission.Pin(s.manifest().reference(),EvidenceExchange.sha256(EXCHANGE.encode(s)));
        var catalog=new EvidenceSnapshotCatalog(temp);assertEquals(EvidenceSnapshotCatalog.Status.STORED,catalog.seed(s,pin).status());
        assertEquals(EvidenceSnapshotCatalog.Status.REPLAY,catalog.seed(s,pin).status());
        var view=new EvidenceQueries(temp).evidence(pin.reference());assertEquals(e,view.envelopes().getFirst());assertArrayEquals(e.readPayload(),view.envelopes().getFirst().readPayload());
        assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,view.interpretations().getFirst().status());assertTrue(view.interpretations().getFirst().measurements().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"V19","V20","V21","V22"})
    void contradictoryReportsAndFailedInterpretationsNeverOverwriteSource(String capability) throws Exception {
        var a=report(capability,"a","SYNTHETIC external assessment A","same-state");var b=report(capability,"b","SYNTHETIC contradictory external assessment B","same-state");
        byte[] original=EXCHANGE.encodeRecord(a);var h=new EvidenceHistory().append(a).append(b);
        h=h.append(assessment(a,"unsupported",EvidenceInterpretation.Status.UNSUPPORTED)).append(assessment(a,"failed",EvidenceInterpretation.Status.FAILED));
        var restored=EXCHANGE.decode(EXCHANGE.encode(snapshot(capability,h))).history();assertEquals(2,restored.envelopes().size());assertEquals(2,restored.interpretations().size());
        assertArrayEquals(original,EXCHANGE.encodeRecord(restored.envelopes().get(a.reference())));
        var stable=h;assertThrows(IllegalArgumentException.class,()->stable.append(report(capability,"a","changed bytes","same-state")));
    }
    @ParameterizedTest @ValueSource(strings={"V19","V20","V21","V22"})
    void stateAndMethodAttributionAreNotImplicitlyMerged(String capability) throws Exception {
        var a=report(capability,"a","same bytes","model-a");var b=report(capability,"b","same bytes","model-b");
        var h=EXCHANGE.decode(EXCHANGE.encode(snapshot(capability,new EvidenceHistory().append(a).append(b)))).history();
        assertEquals(2,h.envelopes().size());assertNotEquals(a.context(),b.context());assertEquals(a.payloadSha256(),b.payloadSha256());
        assertNotEquals(EXCHANGE.contentDigest(a),EXCHANGE.contentDigest(b));
    }
    @ParameterizedTest @ValueSource(strings={"V19","V20","V21","V22"})
    void missingAndTamperedExternalArtifactsFailWithoutInventingMetricResults(String capability) throws Exception {
        Path file=temp.resolve("source.bin");var inline=report(capability,"report","original external bytes","state");
        var external=new EvidenceEnvelope(inline.reference(),inline.evidenceType(),inline.payloadFormat(),inline.payloadVersion(),Optional.empty(),Optional.of(file.toString()),inline.payloadSha256(),inline.provenance(),inline.method(),inline.context(),inline.subjects(),inline.qualifications(),inline.limitations(),TIME);
        var s=snapshot(capability,new EvidenceHistory().append(external));var pin=new EvidenceAdmission.Pin(s.manifest().reference(),EvidenceExchange.sha256(EXCHANGE.encode(s)));
        var catalog=new EvidenceSnapshotCatalog(Files.createDirectory(temp.resolve("catalog")));assertThrows(java.io.IOException.class,()->catalog.seed(s,pin));
        Files.write(file,inline.readPayload());assertEquals(EvidenceSnapshotCatalog.Status.STORED,catalog.seed(s,pin).status());
        Files.writeString(file,"tampered");assertThrows(java.io.IOException.class,()->catalog.read(pin));
        assertArrayEquals(inline.readPayload(),report(capability,"report","original external bytes","state").readPayload());
    }
    public static void main(String[] args) throws Exception {
        var h=new EvidenceHistory();for(String id:List.of("V19","V20","V21","V22")){
            var e=report(id,id,"SYNTHETIC source bytes for "+id,"explicit-state");h=h.append(e).append(assessment(e,id,EvidenceInterpretation.Status.NOT_EVALUATED));
        }
        Files.write(Path.of(args[0]),EXCHANGE.encode(snapshot("four-reports",h)));
    }
}
