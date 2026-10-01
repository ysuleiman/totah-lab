package totah.lab.mnemosyne;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

class EvidenceHistoryAcceptanceTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(1), T2 = T0.plusSeconds(2), T3 = T0.plusSeconds(3);
    private static ScientificReference ref(ScientificReference.Kind kind, String id) {
        return new ScientificReference(kind, "fixture", id, "1");
    }
    private static Observation observation(String id, String run, String value) {
        return new Observation(ref(OBSERVATION, id), ref(SUBJECT, "opaque-subject"), ref(ENDPOINT, "mass"),
                ref(METHOD, "same-specification"), ref(CONTEXT, "scope"), ref(ACTIVITY, run),
                new Observation.Provenance(ref(SOURCE, "source"), new ScientificReference(ARTIFACT, "sha256", "a".repeat(64), "1"),
                        ref(RECEIPT, "receipt"), ref(METHOD, "projection"), "fixture#value", List.of()),
                Observation.Availability.PRESENT, Optional.of(new Observation.Scalar(value, "g/mol")),
                new Observation.Unknown("Not reported"), List.of("Fixture only"));
    }
    private static Review review(Observation o, String id, Review.Decision decision, Instant at) {
        return new Review(ref(REVIEW, id), o.reference(), ref(AGENT, "reviewer"), ref(METHOD, "review-process"),
                ref(POLICY, id), o.context(), decision, List.of("Explicit fixture policy decision"), at, at, List.of(), List.of());
    }
    private static Assessment assessment(Observation o, Review r, String id, Assessment.Outcome outcome, Instant at) {
        return new Assessment(ref(ASSESSMENT, id), Optional.of(o.reference()), Optional.of(r.reference()),
                ref(PROPOSITION, "mass-objective"), ref(CRITERION, "declared-policy-window"), r.policy(), o.context(),
                outcome, List.of("Explicit policy interpretation, not an inferred scientific truth"), at);
    }

    @Test void standaloneObservationHasNoDomainObjectsAndPreservesLexicalValueAndUnknownUncertainty() {
        var o = observation("o", "run", "62.140");
        assertEquals("62.140", o.value().orElseThrow().text());
        assertInstanceOf(Observation.Unknown.class, o.uncertainty());
        assertNotEquals(ref(ACTIVITY, "same"), ref(METHOD, "same"));
        assertThrows(IllegalArgumentException.class, () -> ref(METHOD, "run").require(ACTIVITY));
        assertThrows(IllegalArgumentException.class, () -> new Observation.Scalar("NaN", "hartree"));
    }

    @Test void independentIdenticalValueReplicatesNeverCollapseBySpecificationOrArtifact() {
        var a = observation("a", "run-a", "62.14"); var b = observation("b", "run-b", "62.14");
        assertEquals(a.method(), b.method()); assertEquals(a.provenance(), b.provenance()); assertEquals(a.value(), b.value());
        var history = new EvidenceHistory().append(a).append(b);
        assertEquals(2, history.observations().size());
        assertNotEquals(a.activity(), b.activity());
        assertSame(history, history.append(observation("a", "run-a", "62.14")));
        assertThrows(IllegalArgumentException.class, () -> history.append(observation("a", "run-a", "63.14")));
        assertThrows(IllegalArgumentException.class, () -> history.append(observation("a", "run-b", "62.14")));
        assertEquals(a, history.observations().get(a.reference()));
    }

    @Test void twoReviewersCanDisagreeWithoutRewritingObservation() {
        var o = observation("o", "run", "62.14");
        var a = review(o, "accept", Review.Decision.ACCEPTED, T0);
        var b0 = review(o, "reject", Review.Decision.REJECTED, T0);
        var b = new Review(b0.reference(), b0.observation(), ref(AGENT, "other-reviewer"), b0.process(), b0.policy(),
                b0.scope(), b0.decision(), b0.reasons(), T0, T0, List.of(), List.of());
        var history = new EvidenceHistory().append(o).append(a).append(b);
        assertEquals(1, history.observations().size()); assertEquals(2, history.reviews().size());
        assertTrue(history.admissible(a.reference(), T1, T1)); assertFalse(history.admissible(b.reference(), T1, T1));
        assertSame(o, history.observations().get(o.reference()));
        assertThrows(IllegalArgumentException.class, () -> history.append(assessment(o, b, "bad", Assessment.Outcome.SUPPORTS, T1)));
        assertDoesNotThrow(() -> history.append(assessment(o, b, "invalid", Assessment.Outcome.FAILED_INVALID, T1)));
    }

    @Test void policyScopedSupportContradictionAndUnresolvedCoexistWithCoverageAndInvalidity() {
        var o = observation("o", "run", "62.14"); var a = review(o, "window-a", Review.Decision.ACCEPTED, T0);
        var b = review(o, "window-b", Review.Decision.ACCEPTED, T0);
        var history = new EvidenceHistory().append(o).append(a).append(b)
                .append(assessment(o, a, "support", Assessment.Outcome.SUPPORTS, T1))
                .append(assessment(o, b, "contradict", Assessment.Outcome.CONTRADICTS, T1))
                .append(assessment(o, a, "unresolved", Assessment.Outcome.UNRESOLVED, T1))
                .append(assessment(o, b, "failed", Assessment.Outcome.FAILED_INVALID, T1));
        var missing = new Assessment(ref(ASSESSMENT, "missing"), Optional.empty(), Optional.empty(), ref(PROPOSITION, "mass-objective"),
                ref(CRITERION, "unmeasured-endpoint"), a.policy(), o.context(), Assessment.Outcome.NOT_MEASURED,
                List.of("No measurement of this criterion"), T1);
        history = history.append(missing);
        assertEquals(5, history.assessments().size()); assertEquals(1, history.observations().size());
        assertThrows(IllegalArgumentException.class, () -> assessment(o, a, "fake", Assessment.Outcome.NOT_MEASURED, T1));
    }

    @Test void failedExecutionCannotBecomeContradictionAndMissingResultIsNotZero() {
        var o = observation("o", "run", "62.14");
        var failed = new Observation(o.reference(), o.subject(), o.endpoint(), o.method(), o.context(), o.activity(), o.provenance(),
                Observation.Availability.FAILED_INVALID, Optional.empty(), new Observation.Unknown("Execution failed"), List.of());
        var r = review(failed, "r", Review.Decision.ACCEPTED, T0);
        var history = new EvidenceHistory().append(failed).append(r);
        assertThrows(IllegalArgumentException.class, () -> history.append(assessment(failed, r, "c", Assessment.Outcome.CONTRADICTS, T1)));
        assertDoesNotThrow(() -> history.append(assessment(failed, r, "f", Assessment.Outcome.FAILED_INVALID, T1)));
    }

    @Test void supersessionAndRetractionPreserveHistoricalInterpretationsAndObservation() {
        var o = observation("o", "run", "62.14"); var r = review(o, "r", Review.Decision.ACCEPTED, T0);
        var assessed = assessment(o, r, "a", Assessment.Outcome.SUPPORTS, T0);
        var replacement = review(o, "r2", Review.Decision.REJECTED, T1);
        var original = new EvidenceHistory().append(o).append(r).append(assessed);
        var change = new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, "revision"), r.reference(), Optional.of(replacement.reference()),
                r.reviewer(), "Policy corrected; historical support remains attributed", T1, T2);
        var updated = original.append(replacement).append(change);
        assertTrue(updated.admissible(r.reference(), T1, T1)); // not yet known
        assertFalse(updated.admissible(r.reference(), T2, T1));
        assertTrue(updated.admissible(r.reference(), T2, T0)); // earlier effective view
        assertEquals(List.of(assessed), updated.assessmentsAsOf(T0));
        assertTrue(original.admissible(r.reference(), T3, T3)); // immutable earlier working set
        assertEquals(o, updated.observations().get(o.reference()));
        assertSame(updated, updated.append(change));
        var retracted = updated.append(new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, "withdraw"), replacement.reference(),
                Optional.empty(), r.reviewer(), "Review withdrawn, observation retained", T2, T3));
        assertEquals(2, retracted.reviews().size()); assertEquals(2, retracted.changes().size());
        assertEquals(1, retracted.observations().size());
        assertThrows(IllegalArgumentException.class, () -> retracted.append(assessment(o, r, "new", Assessment.Outcome.SUPPORTS, T3)));
        assertSame(retracted, retracted.append(assessed)); // exact historical re-import remains idempotent
    }

    @Test void lateKnownReviewCannotAuthorizeEarlierAssessmentAndDanglingLinksFail() {
        var o = observation("o", "run", "62.14"); var r = review(o, "r", Review.Decision.ACCEPTED, T2);
        assertThrows(IllegalArgumentException.class, () -> new EvidenceHistory().append(r));
        var history = new EvidenceHistory().append(o).append(r);
        assertThrows(IllegalArgumentException.class, () -> history.append(assessment(o, r, "old", Assessment.Outcome.SUPPORTS, T1)));
        var other = observation("other", "other-run", "62.14");
        var more = history.append(other);
        assertThrows(IllegalArgumentException.class, () -> more.append(assessment(other, r, "wrong", Assessment.Outcome.SUPPORTS, T3)));
    }

    @Test void intervalsAndErrorsAreTypedWithoutInventedConfidence() {
        var interval = new Observation.Interval(new Observation.Scalar("61", "g/mol"), new Observation.Scalar("63", "g/mol"), "Reported range, not confidence interval");
        assertEquals("61", interval.lower().text());
        assertThrows(IllegalArgumentException.class, () -> new Observation.Interval(interval.upper(), interval.lower(), "invalid"));
        var error = new Observation.ReportedError(ref(ENDPOINT, "standard-error"), new Observation.Scalar("0.3", "g/mol"), "Source-reported SE");
        assertNotEquals(error, new Observation.Unknown("Not reported"));
        var o = observation("o", "run", "62.14");
        assertDoesNotThrow(() -> new Observation(o.reference(), o.subject(), o.endpoint(), o.method(), o.context(), o.activity(), o.provenance(),
                o.availability(), o.value(), interval, o.limitations()));
        assertThrows(IllegalArgumentException.class, () -> new Observation(o.reference(), o.subject(), o.endpoint(), o.method(), o.context(), o.activity(), o.provenance(),
                o.availability(), o.value(), new Observation.ReportedError(ref(ENDPOINT, "SE"), new Observation.Scalar("1", "kg/mol"), "Unconverted"), o.limitations()));
    }

    @Test void mutableInputsCannotChangeHistoryAndRecordIdentitiesFailClosed() {
        var o = observation("o", "run", "62.14"); var r = review(o, "r", Review.Decision.ACCEPTED, T0);
        var reasons = new ArrayList<>(List.of("original"));
        var copy = new Review(r.reference(), r.observation(), r.reviewer(), r.process(), r.policy(), r.scope(), r.decision(), reasons, T0, T0, List.of(), List.of());
        reasons.set(0, "mutated"); assertEquals(List.of("original"), copy.reasons());
        var h = new EvidenceHistory().append(o).append(r);
        assertThrows(UnsupportedOperationException.class, () -> h.observations().clear());
        assertThrows(IllegalArgumentException.class, () -> h.append(copy));
    }
}
