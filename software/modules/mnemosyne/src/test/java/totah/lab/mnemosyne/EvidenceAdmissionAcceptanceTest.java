package totah.lab.mnemosyne;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;
import totah.lab.mnemosyne.EvidenceAdmission.*;

class EvidenceAdmissionAcceptanceTest {
    private final EvidenceExchange exchange = new EvidenceExchange();
    private final EvidenceAdmission admission = new EvidenceAdmission();
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");
    private ScientificReference ref(ScientificReference.Kind kind, String id) {
        return new ScientificReference(kind, "synthetic-admission", id, "1");
    }
    private Assessment missing(String id, String reason) {
        return new Assessment(ref(ASSESSMENT, id), Optional.empty(), Optional.empty(), ref(PROPOSITION, "p"),
                ref(CRITERION, "c"), ref(POLICY, "policy"), ref(CONTEXT, "context"),
                Assessment.Outcome.NOT_MEASURED, List.of(reason), T);
    }
    private EvidenceExchange.Snapshot snapshot(String id, Optional<ScientificReference> parent, EvidenceHistory history, Instant at) throws Exception {
        return exchange.snapshot(ref(SNAPSHOT, id), ref(ACTIVITY, "capture"), at, parent, history);
    }
    private EvidenceExchange.Snapshot parent() throws Exception {
        return snapshot("parent", Optional.empty(), new EvidenceHistory().append(missing("a", "not measured")), T);
    }
    private Pin pin(EvidenceExchange.Snapshot s) throws Exception {
        return new Pin(s.manifest().reference(), EvidenceExchange.sha256(exchange.encode(s)));
    }
    private Expectation expected(EvidenceExchange.Snapshot p, EvidenceExchange.Snapshot s) throws Exception {
        return new Expectation(pin(p), pin(s));
    }
    private void reason(Result result, Reason reason, ScientificReference affected) {
        assertEquals(Status.CONFLICT, result.status());
        assertTrue(result.findings().contains(new Finding(reason, affected)), result.toString());
    }
    @Test void successorAndReplayAreDeterministicAndInputsRemainByteIdentical() throws Exception {
        var p = parent();
        var child = snapshot("child", Optional.of(p.manifest().reference()), p.history().append(missing("b", "unknown")), T.plusSeconds(1));
        byte[] beforeParent = exchange.encode(p), beforeChild = exchange.encode(child);
        var expected = expected(p, child);
        var result = admission.check(p, child, expected);
        assertEquals(Status.ADMISSIBLE, result.status());
        assertEquals(expected, result.expectation());
        assertEquals(EvidenceAdmission.METHOD, result.method());
        assertTrue(result.findings().isEmpty());
        assertEquals(result, new EvidenceAdmission().check(exchange.decode(beforeParent), exchange.decode(beforeChild), expected));
        assertEquals(Status.REPLAY, admission.check(child, exchange.decode(beforeChild), expected(child, child)).status());
        assertArrayEquals(beforeParent, exchange.encode(p));
        assertArrayEquals(beforeChild, exchange.encode(child));
        assertThrows(UnsupportedOperationException.class, () -> result.findings().add(new Finding(Reason.WRONG_PARENT, ref(SNAPSHOT, "x"))));
    }
    @Test void pinsRejectWrongReferencesAndDigestsEvenForReplay() throws Exception {
        var p = parent(); var correct = pin(p);
        var wrongRef = new Pin(ref(SNAPSHOT, "wrong"), correct.sha256());
        var wrongDigest = new Pin(correct.reference(), "0".repeat(64));
        reason(admission.check(p, p, new Expectation(wrongRef, correct)), Reason.PARENT_REFERENCE_MISMATCH, wrongRef.reference());
        reason(admission.check(p, p, new Expectation(correct, wrongRef)), Reason.INCOMING_REFERENCE_MISMATCH, wrongRef.reference());
        reason(admission.check(p, p, new Expectation(wrongDigest, correct)), Reason.PARENT_DIGEST_MISMATCH, correct.reference());
        reason(admission.check(p, p, new Expectation(correct, wrongDigest)), Reason.INCOMING_DIGEST_MISMATCH, correct.reference());
    }
    @Test void wrongOrMissingParentAndBackwardChronologyConflict() throws Exception {
        var p = snapshot("parent", Optional.empty(), new EvidenceHistory(), T.plusSeconds(10));
        for (var parent : List.of(Optional.<ScientificReference>empty(), Optional.of(ref(SNAPSHOT, "wrong")))) {
            var child = snapshot("child", parent, p.history(), T.plusSeconds(11));
            reason(admission.check(p, child, expected(p, child)), Reason.WRONG_PARENT, child.manifest().reference());
        }
        var backward = snapshot("backward", Optional.of(p.manifest().reference()), p.history(), T);
        reason(admission.check(p, backward, expected(p, backward)), Reason.CHRONOLOGY_CONFLICT, backward.manifest().reference());
    }
    @Test void reusedSnapshotIdentityWithNewContentIsNotReplay() throws Exception {
        var p = parent();
        var reused = snapshot("parent", Optional.empty(), p.history().append(missing("b", "unknown")), T);
        reason(admission.check(p, reused, expected(p, reused)), Reason.SNAPSHOT_IDENTITY_CONFLICT, p.manifest().reference());
    }
    @Test void missingAndChangedInheritedRecordsConflictWithoutMutatingEitherSnapshot() throws Exception {
        var p = parent(); byte[] before = exchange.encode(p);
        var omitted = snapshot("omitted", Optional.of(p.manifest().reference()), new EvidenceHistory(), T);
        reason(admission.check(p, omitted, expected(p, omitted)), Reason.MISSING_INHERITED_RECORD, ref(ASSESSMENT, "a"));
        var changed = snapshot("changed", Optional.of(p.manifest().reference()), new EvidenceHistory().append(missing("a", "different reason")), T);
        byte[] changedBefore = exchange.encode(changed);
        reason(admission.check(p, changed, expected(p, changed)), Reason.CHANGED_INHERITED_RECORD, ref(ASSESSMENT, "a"));
        assertArrayEquals(before, exchange.encode(p));
        assertArrayEquals(changedBefore, exchange.encode(changed));
    }
    @Test void inconsistentManifestBodyIsReportedForEitherInput() throws Exception {
        var p = parent();
        var invalid = new EvidenceExchange.Snapshot(p.manifest(), new EvidenceHistory());
        var expectation = expected(p, p);
        reason(admission.check(invalid, p, expectation), Reason.INVALID_PARENT, p.manifest().reference());
        reason(admission.check(p, invalid, expectation), Reason.INVALID_INCOMING, p.manifest().reference());
    }
    @Test void siblingsRemainIndependentAndCannotMasqueradeAsSuccessorsOfEachOther() throws Exception {
        var p = parent();
        var left = snapshot("left", Optional.of(p.manifest().reference()), p.history().append(missing("branch", "left reason")), T);
        var right = snapshot("right", Optional.of(p.manifest().reference()), p.history().append(missing("branch", "right reason")), T);
        assertEquals(Status.ADMISSIBLE, admission.check(p, left, expected(p, left)).status());
        assertEquals(Status.ADMISSIBLE, admission.check(p, right, expected(p, right)).status());
        var result = admission.check(left, right, expected(left, right));
        reason(result, Reason.WRONG_PARENT, right.manifest().reference());
        reason(result, Reason.CHANGED_INHERITED_RECORD, ref(ASSESSMENT, "branch"));
        assertNotEquals(left.history().assessments().get(ref(ASSESSMENT, "branch")), right.history().assessments().get(ref(ASSESSMENT, "branch")));
    }
    @Test void differingAssessmentsWithDistinctIdentitiesSurviveAdmission() throws Exception {
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
        var p = snapshot("p", Optional.empty(), new EvidenceHistory().append(o).append(review).append(supports), T);
        var child = snapshot("c", Optional.of(p.manifest().reference()), p.history().append(contradicts), T);
        assertEquals(Status.ADMISSIBLE, admission.check(p, child, expected(p, child)).status());
        assertEquals(2, exchange.decode(exchange.encode(child)).history().assessments().size());
        assertEquals(supports, child.history().assessments().get(supports.reference()));
        assertEquals(contradicts, child.history().assessments().get(contradicts.reference()));
    }
    @Test void insertionOrderDoesNotChangePinsOrAdmission() throws Exception {
        var p = parent();
        var first = snapshot("c", Optional.of(p.manifest().reference()), p.history().append(missing("b", "b")).append(missing("c", "c")), T);
        var reverse = snapshot("c", Optional.of(p.manifest().reference()), new EvidenceHistory().append(missing("c", "c")).append(missing("b", "b")).append(missing("a", "not measured")), T);
        assertEquals(pin(first), pin(reverse));
        assertEquals(admission.check(p, first, expected(p, first)), admission.check(p, reverse, expected(p, first)));
    }
    @Test void malformedExpectationsAreCallerErrors() {
        assertThrows(IllegalArgumentException.class, () -> new Pin(ref(ASSESSMENT, "a"), "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new Pin(ref(SNAPSHOT, "s"), "bad"));
        assertThrows(NullPointerException.class, () -> new Expectation(null, null));
    }
}
