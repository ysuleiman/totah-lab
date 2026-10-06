package totah.lab.mnemosyne;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

class OpaqueEvidenceAcceptanceTest {
    @TempDir Path temp;
    private static final Instant T=Instant.parse("2026-10-04T00:00:00Z");
    private final EvidenceExchange exchange=new EvidenceExchange();
    static ScientificReference ref(ScientificReference.Kind k,String id){return new ScientificReference(k,"opaque-test",id,"1");}
    static EvidenceEnvelope envelope(String id,byte[] bytes) {
        var subjects=new ArrayList<EvidenceSubject>();
        for(var kind:List.of("atom","residue","fragment","molecule","component","transformation","coordinate-state","system","future:unknown"))
            subjects.add(new EvidenceSubject(ref(CONTEXT,"state"),kind,"source:unresolved",List.of()));
        subjects.add(new EvidenceSubject(ref(CONTEXT,"state"),"collection","set",List.copyOf(subjects)));
        return new EvidenceEnvelope(ref(EVIDENCE_ENVELOPE,id),"future:unknown","future/binary","99",Optional.of(Base64.getEncoder().encodeToString(bytes)),Optional.empty(),EvidenceExchange.sha256(bytes),
                new Observation.Provenance(ref(SOURCE,"original"),ref(ARTIFACT,"artifact"),ref(RECEIPT,"receipt"),ref(METHOD,"source-method"),"original locator",List.of()),
                ref(METHOD,"method"),ref(CONTEXT,"context"),subjects,List.of("uninterpreted"),List.of("unknown chemistry"),T);
    }
    private EvidenceAdmission.Pin store(EvidenceSnapshotCatalog catalog,String id,EvidenceHistory history,Optional<EvidenceAdmission.Pin> parent)throws Exception {
        var s=exchange.snapshot(ref(SNAPSHOT,id),ref(ACTIVITY,"write"),T,parent.map(EvidenceAdmission.Pin::reference),history);
        var pin=new EvidenceAdmission.Pin(s.manifest().reference(),EvidenceExchange.sha256(exchange.encode(s)));
        var r=parent.isEmpty()?catalog.seed(s,pin):catalog.append(s,new EvidenceAdmission.Expectation(parent.get(),pin));
        assertNotEquals(EvidenceSnapshotCatalog.Status.CONFLICT,r.status());return pin;
    }
    private EvidenceInterpretation interpretation(String id,EvidenceEnvelope source,EvidenceInterpretation.Status status,Optional<ScientificReference> prior)throws Exception {
        return new EvidenceInterpretation(ref(EVIDENCE_INTERPRETATION,id),List.of(new EvidenceInterpretation.Input(source.reference(),exchange.contentDigest(source))),
                ref(METHOD,id),Map.of("parameter","explicit"),source.subjects(),status,Map.of("observation","preserved"),List.of("attributed finding"),List.of(),prior,T);
    }
    @Test void unknownBinaryPayloadAndEverySubjectKindSurviveDurableReadBack()throws Exception {
        byte[] raw={0,(byte)255,10,13,42};var source=envelope("source",raw);var catalog=new EvidenceSnapshotCatalog(temp);
        var pin=store(catalog,"root",new EvidenceHistory().append(source),Optional.empty());
        var restored=new EvidenceQueries(temp).evidence(pin.reference()).envelopes().getFirst();
        assertEquals(source,restored);assertArrayEquals(raw,restored.readPayload());
        raw[0]=12;var copy=restored.readPayload();copy[0]=99;assertEquals(0,restored.readPayload()[0]);
        assertEquals(10,restored.subjects().size());assertEquals(9,restored.subjects().getLast().members().size());
        assertThrows(UnsupportedOperationException.class,()->restored.subjects().clear());
    }
    @Test void contradictoryObservationsAndAllAssessmentStatesCoexist()throws Exception {
        var a=envelope("positive","yes".getBytes());var b=envelope("negative","no".getBytes());
        var h=new EvidenceHistory().append(a).append(b);
        for(var status:EvidenceInterpretation.Status.values())h=h.append(interpretation(status.name(),a,status,Optional.empty()));
        var c=new EvidenceSnapshotCatalog(temp);var pin=store(c,"root",h,Optional.empty());var restored=c.read(pin).orElseThrow();
        assertEquals(2,restored.history().envelopes().size());assertEquals(6,restored.history().interpretations().size());
        assertNotEquals(EvidenceInterpretation.Status.NOT_EVALUATED,EvidenceInterpretation.Status.ABSENT_FALSE);
        assertEquals(h.interpretations(),new EvidenceQueries(temp).evidence(pin.reference()).interpretations().stream().collect(java.util.stream.Collectors.toMap(EvidenceInterpretation::reference,x->x)));
    }
    @Test void newInterpretationDoesNotChangeEarlierEvidenceOrSibling()throws Exception {
        var e=envelope("e",new byte[]{1,2});var old=interpretation("old",e,EvidenceInterpretation.Status.UNSUPPORTED,Optional.empty());
        var h=new EvidenceHistory().append(e).append(old);byte[] original=exchange.encodeRecord(e);
        var c=new EvidenceSnapshotCatalog(temp);var root=store(c,"root",h,Optional.empty());
        var next=interpretation("new",e,EvidenceInterpretation.Status.SUPPORTED_PRESENT,Optional.of(old.reference()));
        var left=store(c,"left",h.append(next),Optional.of(root));
        var right=store(c,"right",h.append(interpretation("other",e,EvidenceInterpretation.Status.ABSENT_FALSE,Optional.empty())),Optional.of(root));
        assertArrayEquals(original,exchange.encodeRecord(c.read(left).orElseThrow().history().envelopes().get(e.reference())));
        assertEquals(1,c.read(root).orElseThrow().history().interpretations().size());
        assertFalse(c.read(right).orElseThrow().history().interpretations().containsKey(next.reference()));
        assertEquals(old,c.read(left).orElseThrow().history().interpretations().get(old.reference()));
    }
    @Test void legacyV1V2BytesRemainExactAndNewEnvelopeUsesV3()throws Exception {
        for(String version:List.of("v1","v2"))try(var stream=getClass().getResourceAsStream("/evidence-v3-compatibility/"+version+".json")) {
            assertNotNull(stream);var bytes=stream.readAllBytes();assertArrayEquals(bytes,exchange.encode(exchange.decode(bytes)));
        }
        var e=envelope("e",new byte[]{0});var snapshot=exchange.snapshot(ref(SNAPSHOT,"new"),ref(ACTIVITY,"run"),T,Optional.empty(),new EvidenceHistory().append(e));
        assertEquals(EvidenceExchange.EVIDENCE_SCHEMA,snapshot.manifest().schema());assertEquals(e,exchange.decodeRecord(exchange.encodeRecord(e)));
    }
    @Test void changedRecordCannotOverwriteAndWrongInputDigestFails()throws Exception {
        var e=envelope("e",new byte[]{1});var h=new EvidenceHistory().append(e);
        assertThrows(IllegalArgumentException.class,()->h.append(envelope("e",new byte[]{2})));
        var bad=new EvidenceInterpretation(ref(EVIDENCE_INTERPRETATION,"bad"),List.of(new EvidenceInterpretation.Input(e.reference(),"0".repeat(64))),ref(METHOD,"m"),Map.of(),e.subjects(),EvidenceInterpretation.Status.FAILED,Map.of(),List.of("failed"),List.of(),Optional.empty(),T);
        assertThrows(IllegalArgumentException.class,()->h.append(bad));assertEquals(e,h.envelopes().get(e.reference()));
    }
    @Test void externalArtifactMustVerifyAndReadBackDetectsChangedBytes()throws Exception {
        Path file=temp.resolve("source.bin");Files.write(file,new byte[]{1,2});var e=envelope("external",new byte[]{1,2});
        var external=new EvidenceEnvelope(e.reference(),e.evidenceType(),e.payloadFormat(),e.payloadVersion(),Optional.empty(),Optional.of(file.toString()),e.payloadSha256(),e.provenance(),e.method(),e.context(),e.subjects(),e.qualifications(),e.limitations(),T);
        Path dir=Files.createDirectory(temp.resolve("catalog"));var catalog=new EvidenceSnapshotCatalog(dir);var pin=store(catalog,"root",new EvidenceHistory().append(external),Optional.empty());
        assertArrayEquals(new byte[]{1,2},catalog.read(pin).orElseThrow().history().envelopes().get(e.reference()).readPayload());
        Files.write(file,new byte[]{3});assertThrows(java.io.IOException.class,()->catalog.read(pin));
        try(var files=Files.list(dir)){assertEquals(1,files.count());}
    }
    @Test void missingArtifactNeverClaimsDurablePreservation()throws Exception {
        var e=envelope("external",new byte[]{1});var external=new EvidenceEnvelope(e.reference(),e.evidenceType(),e.payloadFormat(),e.payloadVersion(),Optional.empty(),Optional.of(temp.resolve("missing").toString()),e.payloadSha256(),e.provenance(),e.method(),e.context(),e.subjects(),e.qualifications(),e.limitations(),T);
        assertThrows(java.io.IOException.class,()->store(new EvidenceSnapshotCatalog(temp),"root",new EvidenceHistory().append(external),Optional.empty()));
        try(var paths=Files.list(temp)){assertEquals(0,paths.count());}
    }
}
