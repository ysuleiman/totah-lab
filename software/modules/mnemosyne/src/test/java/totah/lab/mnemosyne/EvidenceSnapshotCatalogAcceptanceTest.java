package totah.lab.mnemosyne;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

class EvidenceSnapshotCatalogAcceptanceTest {
    @TempDir Path temporary;
    private final EvidenceExchange exchange = new EvidenceExchange();
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");
    private ScientificReference ref(ScientificReference.Kind kind, String id) {
        return new ScientificReference(kind, "synthetic-catalog", id, "1");
    }
    private Assessment assessment(String id, String reason) {
        return new Assessment(ref(ASSESSMENT, id), Optional.empty(), Optional.empty(), ref(PROPOSITION, "p"),
                ref(CRITERION, "c"), ref(POLICY, "policy"), ref(CONTEXT, "context"),
                Assessment.Outcome.NOT_MEASURED, List.of(reason), T);
    }
    private EvidenceExchange.Snapshot snapshot(String id, Optional<ScientificReference> parent, EvidenceHistory history) throws Exception {
        return exchange.snapshot(ref(SNAPSHOT, id), ref(ACTIVITY, "capture"), T, parent, history);
    }
    private EvidenceExchange.Snapshot root() throws Exception {
        return snapshot("root", Optional.empty(), new EvidenceHistory().append(assessment("a", "not measured")));
    }
    private EvidenceExchange.Snapshot child(EvidenceExchange.Snapshot p, String id) throws Exception {
        return snapshot(id, Optional.of(p.manifest().reference()), p.history().append(assessment(id, "new record")));
    }
    private EvidenceAdmission.Pin pin(EvidenceExchange.Snapshot s) throws IOException {
        return new EvidenceAdmission.Pin(s.manifest().reference(), EvidenceExchange.sha256(exchange.encode(s)));
    }
    private EvidenceAdmission.Expectation expected(EvidenceExchange.Snapshot p, EvidenceExchange.Snapshot s) throws IOException {
        return new EvidenceAdmission.Expectation(pin(p), pin(s));
    }
    private EvidenceSnapshotCatalog catalog() throws IOException {
        return new EvidenceSnapshotCatalog(Files.createDirectory(temporary.resolve("catalog")));
    }
    private Path path(EvidenceExchange.Snapshot s) throws IOException {
        return temporary.resolve("catalog").resolve(EvidenceExchange.sha256(exchange.encodeRecord(s.manifest().reference())) + ".snapshot.json");
    }
    private Map<String,String> inventory() throws IOException {
        var result = new TreeMap<String,String>();
        var directory = temporary.resolve("catalog");
        result.put("directory-mtime", Files.getLastModifiedTime(directory).toString());
        try (var files = Files.list(directory)) {
            for (var file : files.toList()) result.put(file.getFileName().toString(),
                    EvidenceExchange.sha256(Files.readAllBytes(file)) + ":" + Files.getLastModifiedTime(file));
        }
        return result;
    }
    @Test void seedAndSuccessorPersistAcrossIndependentCatalogInstances() throws Exception {
        var store = catalog(); var p = root(); var c = child(p, "child");
        byte[] parentBefore = exchange.encode(p), childBefore = exchange.encode(c);
        assertTrue(store.entries().isEmpty());
        var seeded = store.seed(p, pin(p));
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, seeded.status());
        assertEquals(EvidenceAdmission.Status.REPLAY, seeded.admission().orElseThrow().status());
        var written = store.append(c, expected(p, c));
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, written.status());
        assertEquals(EvidenceAdmission.Status.ADMISSIBLE, written.admission().orElseThrow().status());
        assertEquals(expected(p, c), written.expectation());
        var reopened = new EvidenceSnapshotCatalog(temporary.resolve("catalog"));
        assertEquals(p, reopened.read(pin(p)).orElseThrow());
        assertEquals(c, reopened.read(pin(c)).orElseThrow());
        assertEquals(2, reopened.entries().size());
        assertEquals(Optional.of(p.manifest().reference()), reopened.entries().stream()
                .filter(e -> e.pin().equals(written.expectation().incoming())).findFirst().orElseThrow().parent());
        assertArrayEquals(parentBefore, Files.readAllBytes(path(p)));
        assertArrayEquals(childBefore, Files.readAllBytes(path(c)));
        assertArrayEquals(parentBefore, exchange.encode(p));
        assertArrayEquals(childBefore, exchange.encode(c));
    }
    @Test void exactReplayIsIdempotentAndStillRequiresCorrectPins() throws Exception {
        var store = catalog(); var p = root(); var c = child(p, "child");
        store.seed(p, pin(p)); store.append(c, expected(p, c));
        var before = inventory();
        assertEquals(EvidenceSnapshotCatalog.Status.REPLAY, store.seed(p, pin(p)).status());
        assertEquals(EvidenceSnapshotCatalog.Status.REPLAY, store.append(c, expected(p, c)).status());
        assertEquals(EvidenceSnapshotCatalog.Status.REPLAY, store.append(c, expected(c, c)).status());
        var wrongPin = new EvidenceAdmission.Pin(pin(p).reference(), "0".repeat(64));
        var result = store.append(c, new EvidenceAdmission.Expectation(wrongPin, pin(c)));
        assertEquals(EvidenceSnapshotCatalog.Status.CONFLICT, result.status());
        assertTrue(result.admission().orElseThrow().findings().stream()
                .anyMatch(f -> f.reason() == EvidenceAdmission.Reason.PARENT_DIGEST_MISMATCH));
        assertEquals(before, inventory());
    }
    @Test void admissionConflictsAndMissingParentLeaveCatalogByteIdentical() throws Exception {
        var store = catalog(); var p = root(); store.seed(p, pin(p)); var before = inventory();
        var c = child(p, "child");
        var missing = new EvidenceAdmission.Pin(ref(SNAPSHOT, "absent"), pin(p).sha256());
        assertEquals(EvidenceSnapshotCatalog.Reason.PARENT_UNAVAILABLE,
                store.append(c, new EvidenceAdmission.Expectation(missing, pin(c))).reason());
        var omitted = snapshot("omitted", Optional.of(p.manifest().reference()), new EvidenceHistory());
        assertEquals(EvidenceSnapshotCatalog.Reason.ADMISSION_CONFLICT, store.append(omitted, expected(p, omitted)).reason());
        var changed = snapshot("changed", Optional.of(p.manifest().reference()), new EvidenceHistory().append(assessment("a", "changed")));
        assertEquals(EvidenceSnapshotCatalog.Reason.ADMISSION_CONFLICT, store.append(changed, expected(p, changed)).reason());
        var wrongParent = snapshot("wrong-parent", Optional.of(ref(SNAPSHOT, "other")), p.history());
        assertEquals(EvidenceSnapshotCatalog.Reason.ADMISSION_CONFLICT, store.append(wrongParent, expected(p, wrongParent)).reason());
        var invalid = new EvidenceExchange.Snapshot(c.manifest(), new EvidenceHistory());
        assertEquals(EvidenceSnapshotCatalog.Reason.ADMISSION_CONFLICT, store.append(invalid, expected(p, c)).reason());
        assertEquals(before, inventory());
    }
    @Test void seedCannotBypassPinsOrImportAParentedSnapshot() throws Exception {
        var store = catalog(); var p = root(); var before = inventory();
        var wrong = new EvidenceAdmission.Pin(p.manifest().reference(), "0".repeat(64));
        assertEquals(EvidenceSnapshotCatalog.Reason.ADMISSION_CONFLICT, store.seed(p, wrong).reason());
        var c = child(p, "child");
        assertEquals(EvidenceSnapshotCatalog.Reason.ROOT_HAS_PARENT, store.seed(c, pin(c)).reason());
        var invalid = new EvidenceExchange.Snapshot(p.manifest(), new EvidenceHistory());
        assertEquals(EvidenceSnapshotCatalog.Reason.ADMISSION_CONFLICT, store.seed(invalid, pin(p)).reason());
        assertEquals(before, inventory());
    }
    @Test void alreadyStoredIdentityCannotBeOverwrittenEvenWhenAdmissionAgainstParentPasses() throws Exception {
        var store = catalog(); var p = root(); var first = child(p, "same-id");
        store.seed(p, pin(p)); store.append(first, expected(p, first));
        var different = snapshot("same-id", Optional.of(p.manifest().reference()), p.history().append(assessment("different", "different")));
        assertEquals(EvidenceAdmission.Status.ADMISSIBLE, new EvidenceAdmission().check(p, different, expected(p, different)).status());
        var before = inventory();
        assertEquals(EvidenceSnapshotCatalog.Reason.SNAPSHOT_IDENTITY_CONFLICT, store.append(different, expected(p, different)).reason());
        assertEquals(before, inventory());
        assertEquals(first, store.read(pin(first)).orElseThrow());
        assertThrows(IOException.class, () -> store.read(pin(different)));
    }
    @Test void siblingBranchesKeepDifferentSameRecordIdentitiesIndependently() throws Exception {
        var store = catalog(); var p = root();
        var left = snapshot("left", Optional.of(p.manifest().reference()), p.history().append(assessment("branch-record", "left")));
        var right = snapshot("right", Optional.of(p.manifest().reference()), p.history().append(assessment("branch-record", "right")));
        store.seed(p, pin(p));
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, store.append(right, expected(p, right)).status());
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, store.append(left, expected(p, left)).status());
        var reopened = new EvidenceSnapshotCatalog(temporary.resolve("catalog"));
        assertEquals(left, reopened.read(pin(left)).orElseThrow());
        assertEquals(right, reopened.read(pin(right)).orElseThrow());
        assertEquals(2, reopened.entries().stream().filter(e -> e.parent().equals(Optional.of(p.manifest().reference()))).count());
        var before = inventory();
        assertEquals(EvidenceSnapshotCatalog.Reason.ADMISSION_CONFLICT, store.append(right, expected(left, right)).reason());
        assertEquals(before, inventory());
        assertThrows(UnsupportedOperationException.class, () -> reopened.entries().clear());
    }
    @Test void invalidOrMisplacedStoredBytesFailClosedWithoutWrites() throws Exception {
        var store = catalog(); var p = root(); store.seed(p, pin(p));
        Files.writeString(path(p), "{broken"); // Deliberate corruption of an isolated test catalog.
        var before = inventory();
        assertThrows(IOException.class, store::entries);
        assertThrows(IOException.class, () -> store.read(pin(p)));
        var c = child(p, "child");
        assertThrows(IOException.class, () -> store.append(c, expected(p, c)));
        assertEquals(before, inventory());
        Files.write(path(p), exchange.encode(snapshot("wrong-id", Optional.empty(), p.history())));
        assertThrows(IOException.class, () -> store.read(pin(p)));
    }
    @Test void referenceComponentsAreNeverInterpretedAsPathsAndMissingReadsDoNotWrite() throws Exception {
        var store = catalog();
        var special = snapshot("../nested/path:α", Optional.empty(), new EvidenceHistory());
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, store.seed(special, pin(special)).status());
        assertEquals(special, store.read(pin(special)).orElseThrow());
        var before = inventory();
        assertTrue(store.read(new EvidenceAdmission.Pin(ref(SNAPSHOT, "missing"), "a".repeat(64))).isEmpty());
        assertEquals(before, inventory());
        assertTrue(path(special).getFileName().toString().matches("[0-9a-f]{64}\\.snapshot\\.json"));
    }
    @Test void openingAbsentOrSymlinkCatalogDoesNotCreateStorage() throws Exception {
        var absent = temporary.resolve("absent");
        assertThrows(IOException.class, () -> new EvidenceSnapshotCatalog(absent));
        assertFalse(Files.exists(absent));
        Files.createDirectory(temporary.resolve("real"));
        var link = Files.createSymbolicLink(temporary.resolve("link"), temporary.resolve("real"));
        assertThrows(IOException.class, () -> new EvidenceSnapshotCatalog(link));
    }
    @Test void interruptedPrivateStagingIsNotACatalogEntry() throws Exception {
        var store = catalog(); var p = root();
        var orphan = temporary.resolve(".mnemosyne-snapshot-interrupted.tmp");
        Files.writeString(orphan, "partial uncommitted bytes");
        assertTrue(store.entries().isEmpty());
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, store.seed(p, pin(p)).status());
        assertEquals(1, store.entries().size());
        assertEquals("partial uncommitted bytes", Files.readString(orphan));
        // No broad cleanup of other writers' files.
    }
    @Test void storedDisagreementsAndReviewChangesRemainImmutableAcrossRestart() throws Exception {
        var store = catalog();
        var o = new Observation(ref(OBSERVATION, "o"), ref(SUBJECT, "subject"), ref(ENDPOINT, "endpoint"), ref(METHOD, "method"),
                ref(CONTEXT, "context"), ref(ACTIVITY, "run"),
                new Observation.Provenance(ref(SOURCE, "source"), new ScientificReference(ARTIFACT, "sha256", "a".repeat(64), "1"),
                        ref(RECEIPT, "receipt"), ref(METHOD, "projection"), "synthetic", List.of()),
                Observation.Availability.PRESENT, Optional.of(new Observation.Scalar("1.00", "unit")), new Observation.Unknown("synthetic"), List.of());
        var review = new Review(ref(REVIEW, "r"), o.reference(), ref(AGENT, "reviewer"), ref(METHOD, "process"),
                ref(POLICY, "policy"), o.context(), Review.Decision.ACCEPTED, List.of("synthetic"), T, T, List.of(), List.of());
        var supports = new Assessment(ref(ASSESSMENT, "supports"), Optional.of(o.reference()), Optional.of(review.reference()),
                ref(PROPOSITION, "p"), ref(CRITERION, "c"), review.policy(), o.context(), Assessment.Outcome.SUPPORTS, List.of("one interpretation"), T);
        var contradicts = new Assessment(ref(ASSESSMENT, "contradicts"), supports.observation(), supports.review(), supports.proposition(),
                supports.criterion(), supports.policy(), supports.context(), Assessment.Outcome.CONTRADICTS, List.of("another interpretation"), T);
        var p = snapshot("disagreement-parent", Optional.empty(), new EvidenceHistory().append(o).append(review).append(supports));
        var c = snapshot("disagreement-child", Optional.of(p.manifest().reference()), p.history().append(contradicts));
        var change = new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, "withdrawal"), review.reference(), Optional.empty(),
                review.reviewer(), "later withdrawal", T.plusSeconds(1), T.plusSeconds(1));
        var successor = exchange.snapshot(ref(SNAPSHOT, "withdrawn"), ref(ACTIVITY, "capture"), T.plusSeconds(2),
                Optional.of(c.manifest().reference()), c.history().append(change));
        store.seed(p, pin(p)); store.append(c, expected(p, c)); store.append(successor, expected(c, successor));
        var reopened = new EvidenceSnapshotCatalog(temporary.resolve("catalog"));
        assertEquals(p, reopened.read(pin(p)).orElseThrow());
        assertEquals(c, reopened.read(pin(c)).orElseThrow());
        var restored = reopened.read(pin(successor)).orElseThrow();
        assertEquals(successor, restored);
        assertEquals(2, restored.history().assessments().size());
        assertTrue(restored.history().admissible(review.reference(), T, T));
        assertFalse(restored.history().admissible(review.reference(), T.plusSeconds(2), T.plusSeconds(2)));
        assertEquals(supports, restored.history().assessments().get(supports.reference()));
        assertEquals(contradicts, restored.history().assessments().get(contradicts.reference()));
        assertEquals(change, restored.history().changes().get(change.reference()));
    }
    @Test void catalogRejectsSymlinkAndNoncanonicalEntriesInsteadOfRepairingThem() throws Exception {
        var store = catalog(); var p = root();
        var external = temporary.resolve("external.json"); Files.write(external, exchange.encode(p));
        Files.createSymbolicLink(path(p), external);
        assertThrows(IOException.class, () -> store.seed(p, pin(p)));
        assertThrows(IOException.class, () -> store.read(pin(p)));
        assertArrayEquals(exchange.encode(p), Files.readAllBytes(external));
        Files.delete(path(p)); // Test-owned corrupt fixture only.
        byte[] canonical = exchange.encode(p);
        Files.writeString(path(p), new String(canonical, java.nio.charset.StandardCharsets.UTF_8) + "\n");
        var before = inventory();
        assertThrows(IOException.class, store::entries);
        assertThrows(IOException.class, () -> store.read(pin(p)));
        assertEquals(before, inventory());
    }
    @Test void independentRootsAndInsertionOrderDoNotDesignateAHead() throws Exception {
        var first = catalog();
        var second = new EvidenceSnapshotCatalog(Files.createDirectory(temporary.resolve("second")));
        var p = root(); var q = snapshot("other-root", Optional.empty(), new EvidenceHistory());
        first.seed(q, pin(q)); first.seed(p, pin(p));
        second.seed(p, pin(p)); second.seed(q, pin(q));
        assertEquals(first.entries(), second.entries());
        assertTrue(first.entries().stream().allMatch(e -> e.parent().isEmpty()));
    }
    private List<EvidenceSnapshotCatalog.Result> race(Callable<EvidenceSnapshotCatalog.Result> first,
                                                    Callable<EvidenceSnapshotCatalog.Result> second) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return first.call(); });
            var b = executor.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
    }
    @Test void concurrentIdenticalWritersPublishOnceAndReplay() throws Exception {
        var first = catalog(); var second = new EvidenceSnapshotCatalog(temporary.resolve("catalog"));
        var p = root(); var expected = pin(p);
        var results = race(() -> first.seed(p, expected), () -> second.seed(p, expected));
        assertEquals(1, results.stream().filter(r -> r.status() == EvidenceSnapshotCatalog.Status.STORED).count());
        assertEquals(1, results.stream().filter(r -> r.status() == EvidenceSnapshotCatalog.Status.REPLAY).count());
        assertEquals(1, first.entries().size());
        assertArrayEquals(exchange.encode(p), Files.readAllBytes(path(p)));
        try (var files = Files.list(temporary)) { assertEquals(List.of("catalog"), files.map(f -> f.getFileName().toString()).toList()); }
    }
    @Test void concurrentConflictingWritersNeverReplaceWinner() throws Exception {
        var first = catalog(); var second = new EvidenceSnapshotCatalog(temporary.resolve("catalog"));
        var p = root(); var q = snapshot("root", Optional.empty(), new EvidenceHistory().append(assessment("a", "alternative")));
        var results = race(() -> first.seed(p, pin(p)), () -> second.seed(q, pin(q)));
        assertEquals(1, results.stream().filter(r -> r.status() == EvidenceSnapshotCatalog.Status.STORED).count());
        assertEquals(1, results.stream().filter(r -> r.reason() == EvidenceSnapshotCatalog.Reason.SNAPSHOT_IDENTITY_CONFLICT).count());
        var winner = first.entries().getFirst().pin();
        assertEquals(1, first.entries().size());
        assertTrue(winner.equals(pin(p)) || winner.equals(pin(q)));
        var before = inventory();
        var loser = winner.equals(pin(p)) ? q : p;
        assertEquals(EvidenceSnapshotCatalog.Status.CONFLICT, second.seed(loser, pin(loser)).status());
        assertEquals(before, inventory());
    }
    @Test void concurrentSiblingsBothSurvive() throws Exception {
        var first = catalog(); var second = new EvidenceSnapshotCatalog(temporary.resolve("catalog"));
        var p = root(); first.seed(p, pin(p)); var left = child(p, "left"); var right = child(p, "right");
        var results = race(() -> first.append(left, expected(p, left)), () -> second.append(right, expected(p, right)));
        assertTrue(results.stream().allMatch(r -> r.status() == EvidenceSnapshotCatalog.Status.STORED));
        assertEquals(3, first.entries().size());
        assertEquals(left, second.read(pin(left)).orElseThrow());
        assertEquals(right, first.read(pin(right)).orElseThrow());
    }
}
