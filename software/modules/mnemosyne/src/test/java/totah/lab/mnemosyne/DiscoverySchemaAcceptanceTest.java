package totah.lab.mnemosyne;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

class DiscoverySchemaAcceptanceTest {
    @TempDir Path temporary;
    private final EvidenceExchange exchange = new EvidenceExchange();
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");
    private ScientificReference ref(ScientificReference.Kind kind, String id) { return new ScientificReference(kind, "synthetic-schema", id, "1"); }
    private Path fixture(String name) { return Path.of(System.getProperty("basedir"), "src/test/resources/discovery-v1", name); }
    private Observation observation() throws IOException { return (Observation) exchange.decodeRecord(Files.readAllBytes(fixture("observation.json"))); }
    private DiscoveryDescription description(String id, Instant time) throws IOException {
        var o = observation();
        return new DiscoveryDescription(ref(DISCOVERY_DESCRIPTION, id), o.reference(), exchange.contentDigest(o),
                List.of(new DiscoveryDescription.Participant(ref(SUBJECT, "entity"), new DiscoveryDescription.Term("test", "role", "1"), List.of("Label"))),
                new DiscoveryDescription.Term("test", "predicate", "1"), DiscoveryDescription.Modality.EXPERIMENTAL, ref(AGENT, "agent"), o.provenance(), time);
    }
    private EvidenceExchange.Snapshot snapshot(EvidenceHistory h) throws IOException {
        return exchange.snapshot(ref(SNAPSHOT, "root"), ref(ACTIVITY, "capture"), T.plusSeconds(5), Optional.empty(), h);
    }
    private EvidenceAdmission.Pin pin(EvidenceExchange.Snapshot s) throws IOException {
        return new EvidenceAdmission.Pin(s.manifest().reference(), EvidenceExchange.sha256(exchange.encode(s)));
    }
    @Test void committedPhase7GoldenBytesAndHashesSurviveV2CodecUnchanged() throws Exception {
        var bytes = Files.readAllBytes(fixture("snapshot.json")); var s = exchange.decode(bytes);
        assertEquals(EvidenceExchange.SCHEMA, s.manifest().schema()); assertArrayEquals(bytes, exchange.encode(s));
        assertEquals(EvidenceExchange.sha256(bytes), pin(s).sha256());
        var record = Files.readAllBytes(fixture("observation.json")); assertArrayEquals(record, exchange.encodeRecord(observation()));
        assertEquals(EvidenceExchange.sha256(record), exchange.contentDigest(observation()));
        assertTrue(s.history().descriptions().isEmpty()); assertTrue(s.history().withdrawals().isEmpty());
        var c = new EvidenceSnapshotCatalog(temporary); assertEquals(EvidenceSnapshotCatalog.Status.STORED, c.seed(s, pin(s)).status());
        assertEquals(s, c.read(pin(s)).orElseThrow());
    }
    @Test void mixedV1V2LineagePreservesParentPinsAndRecordBytesAndReplaysWithoutChanges() throws Exception {
        var parentBytes = Files.readAllBytes(fixture("snapshot.json")); var parent = exchange.decode(parentBytes); var d = description("d", T.plusSeconds(3));
        var child = exchange.snapshot(ref(SNAPSHOT, "child"), ref(ACTIVITY, "capture"), T.plusSeconds(5), Optional.of(parent.manifest().reference()), parent.history().append(d));
        assertEquals(EvidenceExchange.DISCOVERY_SCHEMA, child.manifest().schema());
        assertTrue(child.manifest().records().containsAll(parent.manifest().records()));
        assertEquals(child, exchange.decode(exchange.encode(child))); assertEquals(child.hashCode(), exchange.decode(exchange.encode(child)).hashCode());
        var c = new EvidenceSnapshotCatalog(temporary); c.seed(parent, pin(parent));
        var expected = new EvidenceAdmission.Expectation(pin(parent), pin(child));
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, c.append(child, expected).status());
        assertEquals(EvidenceSnapshotCatalog.Status.REPLAY, c.append(child, expected).status());
        assertArrayEquals(parentBytes, exchange.encode(c.read(pin(parent)).orElseThrow()));
        var view = EvidenceBranchView.load(c, child.manifest().reference()); assertEquals(List.of(pin(parent), pin(child)), view.path());
        assertEquals(pin(child), view.provenance().stream().filter(p -> p.record().reference().equals(d.reference())).findFirst().orElseThrow().firstIncludedIn());
    }
    @Test void exactDescriptionBindingAndIdentityConflictsAreRejectedWithoutMutatingHistory() throws Exception {
        var o = observation(); var d = description("d", T); var h = new EvidenceHistory().append(o).append(d);
        assertSame(h, h.append(d));
        var wrongHash = new DiscoveryDescription(d.reference(), d.observation(), "0".repeat(64), d.participants(), d.relationship(), d.modality(), d.agent(), d.provenance(), d.recordedAt());
        assertThrows(IllegalArgumentException.class, () -> new EvidenceHistory().append(o).append(wrongHash));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceHistory().append(d));
        var changed = new DiscoveryDescription(d.reference(), d.observation(), d.observationSha256(), d.participants(), d.relationship(), DiscoveryDescription.Modality.UNKNOWN, d.agent(), d.provenance(), d.recordedAt());
        assertThrows(IllegalArgumentException.class, () -> h.append(changed)); assertEquals(d, h.descriptions().get(d.reference()));
        assertThrows(UnsupportedOperationException.class, () -> h.descriptions().clear());
    }
    @Test void immutableWithdrawalReplacementHistoryRoundTripsAndRejectsInvalidLifecycle() throws Exception {
        var old = description("old", T); var newer = description("new", T.plusSeconds(1));
        var w = new DiscoveryDescription.Withdrawal(ref(DISCOVERY_WITHDRAWAL, "w"), old.reference(), Optional.of(newer.reference()), old.agent(), old.provenance(), "explicit correction", T.plusSeconds(2));
        var h = new EvidenceHistory().append(observation()).append(old).append(newer).append(w); assertSame(h, h.append(w));
        var s = snapshot(h); assertEquals(s, exchange.decode(exchange.encode(s))); assertEquals(w, exchange.decodeRecord(exchange.encodeRecord(w)));
        var c = new EvidenceSnapshotCatalog(temporary); c.seed(s, pin(s)); var view = EvidenceBranchView.load(c, s.manifest().reference());
        assertEquals(2, view.descriptions().size()); assertEquals(1, view.withdrawals().size());
        assertEquals(Optional.of(w), view.descriptions().stream().filter(d -> d.record().equals(old)).findFirst().orElseThrow().withdrawal());
        assertThrows(IllegalArgumentException.class, () -> new EvidenceHistory().append(observation()).append(old).append(w));
        assertThrows(IllegalArgumentException.class, () -> h.append(new DiscoveryDescription.Withdrawal(ref(DISCOVERY_WITHDRAWAL, "again"), old.reference(), Optional.empty(), old.agent(), old.provenance(), "again", T.plusSeconds(3))));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceHistory().append(observation()).append(old).append(newer).append(new DiscoveryDescription.Withdrawal(w.reference(), old.reference(), Optional.of(newer.reference()), ref(AGENT, "other"), old.provenance(), "wrong agent", T.plusSeconds(2))));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceHistory().append(observation()).append(old).append(newer).append(new DiscoveryDescription.Withdrawal(w.reference(), newer.reference(), Optional.of(old.reference()), old.agent(), old.provenance(), "backwards", T.plusSeconds(2))));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceHistory().append(observation()).append(old).append(new DiscoveryDescription.Withdrawal(w.reference(), old.reference(), Optional.empty(), old.agent(), old.provenance(), "too early", T.minusSeconds(1))));
    }
    @Test void schemaVersionsAreStrictAndLegacyTypesNeverSilentlyReencodeAsV2() throws Exception {
        var d = description("d", T); var record = new String(exchange.encodeRecord(d), StandardCharsets.UTF_8);
        assertTrue(record.contains("mnemosyne-record/2")); assertEquals(d, exchange.decodeRecord(exchange.encodeRecord(d)));
        assertThrows(IOException.class, () -> exchange.decodeRecord(record.replace("mnemosyne-record/2", "mnemosyne-record/1").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> exchange.decodeRecord(record.replace("mnemosyne-record/2", "mnemosyne-record/99").getBytes(StandardCharsets.UTF_8)));
        var legacy = Files.readString(fixture("observation.json"));
        assertThrows(IOException.class, () -> exchange.decodeRecord(legacy.replace("mnemosyne-record/1", "mnemosyne-record/2").getBytes(StandardCharsets.UTF_8)));
        var s = snapshot(new EvidenceHistory().append(observation()).append(d)); var m = s.manifest();
        var downgraded = new EvidenceExchange.Snapshot(new EvidenceExchange.Manifest(EvidenceExchange.SCHEMA, m.reference(), m.creationActivity(), m.createdAt(), m.parent(), m.records()), s.history());
        assertThrows(IOException.class, () -> exchange.encode(downgraded));
        var encoded = new String(exchange.encode(s), StandardCharsets.UTF_8);
        int rootSchema = encoded.lastIndexOf("mnemosyne-exchange/2");
        var mismatch = encoded.substring(0, rootSchema) + "mnemosyne-exchange/1" + encoded.substring(rootSchema + "mnemosyne-exchange/2".length());
        assertThrows(IOException.class, () -> exchange.decode(mismatch.getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceExchange.Manifest("mnemosyne-exchange/99", m.reference(), m.creationActivity(), m.createdAt(), m.parent(), m.records()));
    }
    @Test void malformedMetadataAndNumericDatesFailClosed() throws Exception {
        var d = description("d", T); var json = new String(exchange.encodeRecord(d), StandardCharsets.UTF_8);
        for (var bad : List.of(json.replace("\"modality\":\"EXPERIMENTAL\"", "\"modality\":null"),
                json.replace("\"modality\":\"EXPERIMENTAL\"", "\"modality\":\"GUESSED\""),
                json.replace("\"recordedAt\":\"2026-01-01T00:00:00Z\"", "\"recordedAt\":0"),
                json.replace("\"modality\":\"EXPERIMENTAL\"", "\"modality\":\"EXPERIMENTAL\",\"extra\":true"))) {
            assertNotEquals(json, bad); assertThrows(IOException.class, () -> exchange.decodeRecord(bad.getBytes(StandardCharsets.UTF_8)));
        }
        assertThrows(IllegalArgumentException.class, () -> new DiscoveryDescription(d.reference(), d.observation(), "bad", d.participants(), d.relationship(), d.modality(), d.agent(), d.provenance(), T));
        assertThrows(IllegalArgumentException.class, () -> new DiscoveryDescription(d.reference(), d.observation(), d.observationSha256(), List.of(), d.relationship(), d.modality(), d.agent(), d.provenance(), T));
        assertThrows(IllegalArgumentException.class, () -> new DiscoveryDescription(d.reference(), d.observation(), d.observationSha256(), List.of(d.participants().getFirst(), d.participants().getFirst()), d.relationship(), d.modality(), d.agent(), d.provenance(), T));
    }
    @Test void manifestMembershipTamperAndFutureMetadataAreRejected() throws Exception {
        var d = description("d", T); var h = new EvidenceHistory().append(observation()).append(d); var s = snapshot(h); var m = s.manifest();
        var missing = new EvidenceExchange.Snapshot(new EvidenceExchange.Manifest(m.schema(), m.reference(), m.creationActivity(), m.createdAt(), m.parent(), m.records().stream().filter(r -> !r.reference().equals(d.reference())).toList()), h);
        assertThrows(IOException.class, () -> exchange.encode(missing));
        assertThrows(IOException.class, () -> snapshot(new EvidenceHistory().append(observation()).append(description("future", T.plusSeconds(6)))));
        var futureWithdrawal = new DiscoveryDescription.Withdrawal(ref(DISCOVERY_WITHDRAWAL, "w"), d.reference(), Optional.empty(), d.agent(), d.provenance(), "future", T.plusSeconds(6));
        assertThrows(IOException.class, () -> snapshot(h.append(futureWithdrawal)));
        var tampered = new String(exchange.encode(s), StandardCharsets.UTF_8).replace("Label", "Other");
        assertThrows(IOException.class, () -> exchange.decode(tampered.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void admissionRejectsChangedOrDroppedInheritedDescriptionsWithoutWritingCatalog() throws Exception {
        var d = description("d", T); var h = new EvidenceHistory().append(observation()).append(d); var root = snapshot(h);
        var c = new EvidenceSnapshotCatalog(temporary); c.seed(root, pin(root));
        for (var incomingHistory : List.of(new EvidenceHistory().append(observation()), new EvidenceHistory().append(observation()).append(description("d", T.plusSeconds(1))))) {
            var child = exchange.snapshot(ref(SNAPSHOT, "child"), ref(ACTIVITY, "capture"), T.plusSeconds(6), Optional.of(root.manifest().reference()), incomingHistory);
            assertEquals(EvidenceSnapshotCatalog.Status.CONFLICT, c.append(child, new EvidenceAdmission.Expectation(pin(root), pin(child))).status());
            assertEquals(1, c.entries().size()); assertTrue(c.read(pin(child)).isEmpty());
        }
    }
    @Test void rehashedExchangeWithWrongObservationBindingFailsClosedDuringRestore() throws Exception {
        var d = description("d", T); var s = snapshot(new EvidenceHistory().append(observation()).append(d));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(exchange.encode(s));
        String alteredHash = null;
        for (var record : root.get("records")) if (record.get("type").asText().equals("DISCOVERY_DESCRIPTION")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) record.get("data")).put("observationSha256", "0".repeat(64));
            alteredHash = EvidenceExchange.sha256(mapper.writeValueAsBytes(record));
        }
        assertNotNull(alteredHash);
        for (var entry : root.get("manifest").get("records")) if (entry.get("type").asText().equals("DISCOVERY_DESCRIPTION"))
            ((com.fasterxml.jackson.databind.node.ObjectNode) entry).put("sha256", alteredHash);
        root.put("manifestSha256", EvidenceExchange.sha256(mapper.writeValueAsBytes(root.get("manifest"))));
        var error = assertThrows(IOException.class, () -> exchange.decode(mapper.writeValueAsBytes(root)));
        assertEquals("inconsistent self-contained history", error.getMessage());
        assertTrue(error.getCause().getMessage().contains("observation digest mismatch"));
    }

}
