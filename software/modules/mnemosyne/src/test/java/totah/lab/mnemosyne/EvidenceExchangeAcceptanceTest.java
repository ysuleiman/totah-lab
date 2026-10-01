package totah.lab.mnemosyne;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

class EvidenceExchangeAcceptanceTest {
    @TempDir Path temporary;
    private final EvidenceExchange exchange = new EvidenceExchange();
    private final ObjectMapper json = new ObjectMapper();
    private static final Instant T = Instant.parse("2026-01-01T00:00:00.123456789Z");
    private static ScientificReference ref(ScientificReference.Kind kind, String id) { return new ScientificReference(kind, "fixture", id, "1"); }
    private static Observation observation(Observation.Uncertainty uncertainty) {
        return new Observation(ref(OBSERVATION, "o"), ref(SUBJECT, "subject"), ref(ENDPOINT, "mass"), ref(METHOD, "method"),
                ref(CONTEXT, "scope"), ref(ACTIVITY, "run"),
                new Observation.Provenance(ref(SOURCE, "source"), new ScientificReference(ARTIFACT, "sha256", "a".repeat(64), "1"),
                        ref(RECEIPT, "receipt"), ref(METHOD, "projection"), "table 1 / row α", List.of(ref(RECEIPT, "prior"))),
                Observation.Availability.PRESENT, Optional.of(new Observation.Scalar("62.140", "g/mol")), uncertainty,
                List.of("Unknown precision ≠ zero uncertainty", "Quoted \"source\"\nnext line"));
    }
    private static Review review(String id, Review.Decision decision, Instant at) {
        return new Review(ref(REVIEW, id), ref(OBSERVATION, "o"), ref(AGENT, "reviewer"), ref(METHOD, "process"),
                ref(POLICY, id), ref(CONTEXT, "scope"), decision, List.of("reason", "independent policy"), at, at,
                List.of(ref(RECEIPT, "qualification")), List.of("limited scope"));
    }
    private static Assessment assessment(String id, Review review, Assessment.Outcome outcome) {
        return new Assessment(ref(ASSESSMENT, id), outcome == Assessment.Outcome.NOT_MEASURED ? Optional.empty() : Optional.of(ref(OBSERVATION, "o")),
                outcome == Assessment.Outcome.NOT_MEASURED ? Optional.empty() : Optional.of(review.reference()),
                ref(PROPOSITION, "hypothesis"), ref(CRITERION, id), review.policy(), review.scope(), outcome, List.of("explicit interpretation"), T);
    }
    private EvidenceHistory history(boolean reverse) {
        var o = observation(new Observation.Unknown("not reported"));
        var a = review("a", Review.Decision.ACCEPTED, T); var b = review("b", Review.Decision.ACCEPTED, T);
        var history = new EvidenceHistory().append(o);
        history = reverse ? history.append(b).append(a) : history.append(a).append(b);
        for (var outcome : Assessment.Outcome.values()) history = history.append(assessment(outcome.name(), a, outcome));
        var revision = review("a-revised", Review.Decision.REJECTED, T.plusSeconds(2));
        return history.append(revision).append(new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, "revision"), a.reference(),
                Optional.of(revision.reference()), a.reviewer(), "Revised scope; preserve original assessment", T.plusSeconds(2), T.plusSeconds(4)))
                .append(new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, "withdraw-b"), b.reference(), Optional.empty(),
                        b.reviewer(), "Withdraw independent review", T.plusSeconds(3), T.plusSeconds(5)));
    }
    private EvidenceExchange.Snapshot snapshot(boolean reverse) throws IOException {
        return exchange.snapshot(ref(SNAPSHOT, "snapshot"), ref(ACTIVITY, "export"), T.plusSeconds(6), Optional.of(ref(SNAPSHOT, "parent")), history(reverse));
    }
    private static void sameHistory(EvidenceHistory expected, EvidenceHistory actual) {
        assertEquals(expected.observations(), actual.observations()); assertEquals(expected.reviews(), actual.reviews());
        assertEquals(expected.assessments(), actual.assessments()); assertEquals(expected.changes(), actual.changes());
    }
    @Test void frozenReferenceEncodingIsAnExplicitDeterministicContract() throws Exception {
        var reference = new ScientificReference(SUBJECT, "pubchem.compound", "6343", "2025.04.14");
        String golden = "{\"data\":{\"id\":\"6343\",\"kind\":\"SUBJECT\",\"namespace\":\"pubchem.compound\",\"version\":\"2025.04.14\"},\"schema\":\"mnemosyne-record/1\",\"type\":\"REFERENCE\"}";
        assertArrayEquals(golden.getBytes(StandardCharsets.UTF_8), exchange.encodeRecord(reference));
        assertEquals(reference, exchange.decodeRecord(golden.getBytes(StandardCharsets.UTF_8)));
        for (var kind : ScientificReference.Kind.values()) assertEquals(ref(kind, "id"), exchange.decodeRecord(exchange.encodeRecord(ref(kind, "id"))));
    }
    @Test void scalarSpellingUnitsAndAllUncertaintyKindsRoundTripWithoutNormalization() throws Exception {
        for (var uncertainty : List.of(new Observation.Unknown("unknown"),
                new Observation.Interval(new Observation.Scalar("62.100", "g/mol"), new Observation.Scalar("62.200", "g/mol"), "reported range"),
                new Observation.ReportedError(ref(ENDPOINT, "standard-error"), new Observation.Scalar("0.0100", "g/mol"), "source SE"))) {
            var o = observation(uncertainty); byte[] bytes = exchange.encodeRecord(o);
            assertEquals(o, exchange.decodeRecord(bytes, Optional.of(exchange.contentDigest(o))));
            assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("62.140"));
            var altered = new Observation(o.reference(), o.subject(), o.endpoint(), o.method(), o.context(), o.activity(), o.provenance(),
                    o.availability(), Optional.of(new Observation.Scalar("62.14", "g/mol")), o.uncertainty(), o.limitations());
            assertNotEquals(exchange.contentDigest(o), exchange.contentDigest(altered));
            assertEquals(o.reference(), altered.reference()); // Content digest is not the immutable reference identity.
        }
    }
    @Test void snapshotRoundTripPreservesEveryRecordAndBothHistoricalTimeAxes() throws Exception {
        var before = snapshot(false); var path = temporary.resolve("exchange.json"); exchange.write(path, before);
        var after = new EvidenceExchange().read(path);
        assertEquals(before, after); assertEquals(before.hashCode(), after.hashCode());
        assertEquals(before.manifest(), after.manifest()); sameHistory(before.history(), after.history());
        assertArrayEquals(Files.readAllBytes(path), exchange.encode(after));
        assertThrows(IOException.class, () -> exchange.write(path, before));
        for (var known : List.of(T, T.plusSeconds(3), T.plusSeconds(6)))
            for (var effective : List.of(T, T.plusSeconds(2), T.plusSeconds(6)))
                assertEquals(before.history().admissible(ref(REVIEW, "a"), known, effective), after.history().admissible(ref(REVIEW, "a"), known, effective));
        assertTrue(after.history().admissible(ref(REVIEW, "a"), T.plusSeconds(3), T.plusSeconds(3)));
        assertFalse(after.history().admissible(ref(REVIEW, "a"), T.plusSeconds(4), T.plusSeconds(3)));
        assertEquals(before.history().assessmentsAsOf(T), after.history().assessmentsAsOf(T));
        assertEquals(5, after.history().assessmentsAsOf(T).size());
        assertEquals(1, after.history().observations().size());
    }
    @Test void insertionOrderAndTransportWhitespaceDoNotChangeCanonicalContent() throws Exception {
        byte[] canonical = exchange.encode(snapshot(false));
        assertArrayEquals(canonical, exchange.encode(snapshot(true)));
        byte[] pretty = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(json.readTree(canonical));
        assertNotEquals(EvidenceExchange.sha256(canonical), EvidenceExchange.sha256(pretty));
        assertArrayEquals(canonical, exchange.encode(exchange.decode(pretty)));
        assertNotEquals(EvidenceExchange.sha256(canonical), observation(new Observation.Unknown("not reported")).provenance().artifact().id());
    }
    @Test void unknownSchemasMalformedReferencesUnknownFieldsAndDuplicateJsonKeysFailClosed() throws Exception {
        byte[] bytes = exchange.encode(snapshot(false));
        var root = (ObjectNode) json.readTree(bytes); root.put("schema", "mnemosyne-exchange/99");
        assertThrows(IOException.class, () -> exchange.decode(json.writeValueAsBytes(root)));
        var record = (ObjectNode) json.readTree(exchange.encodeRecord(ref(SUBJECT, "id")));
        ((ObjectNode) record.get("data")).put("namespace", " ");
        assertThrows(IOException.class, () -> exchange.decodeRecord(json.writeValueAsBytes(record)));
        record.set("data", json.readTree("{\"kind\":\"SUBJECT\",\"namespace\":\"fixture\",\"id\":123,\"version\":\"1\"}"));
        assertThrows(IOException.class, () -> exchange.decodeRecord(json.writeValueAsBytes(record)));
        assertThrows(IOException.class, () -> exchange.decodeRecord("{\"schema\":\"x\",\"schema\":\"y\"}".getBytes(StandardCharsets.UTF_8)));
        record.put("extra", "unrecognized"); assertThrows(IOException.class, () -> exchange.decodeRecord(json.writeValueAsBytes(record)));
    }
    @Test void tamperedBodyOrManifestFailsIntegrityChecks() throws Exception {
        var root = (ObjectNode) json.readTree(exchange.encode(snapshot(false)));
        for (var record : root.get("records")) if (record.get("type").asText().equals("OBSERVATION"))
            ((ObjectNode) record.path("data").path("value")).put("text", "61.14");
        assertThrows(IOException.class, () -> exchange.decode(json.writeValueAsBytes(root)));
        var other = (ObjectNode) json.readTree(exchange.encode(snapshot(false)));
        ((ObjectNode) other.path("manifest").path("reference")).put("version", "2");
        assertThrows(IOException.class, () -> exchange.decode(json.writeValueAsBytes(other)));
    }
    private void rehashManifest(ObjectNode root) throws IOException {
        // Parsed canonical object field order is retained by Jackson's tree model.
        root.put("manifestSha256", EvidenceExchange.sha256(json.writeValueAsBytes(root.get("manifest"))));
    }
    @Test void missingInternalRecordFailsEvenWithConsistentIntegrityMetadata() throws Exception {
        var root = (ObjectNode) json.readTree(exchange.encode(snapshot(false)));
        var records = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("records");
        for (int i = records.size() - 1; i >= 0; i--) if (records.get(i).path("type").asText().equals("OBSERVATION")) records.remove(i);
        var entries = (com.fasterxml.jackson.databind.node.ArrayNode) root.path("manifest").path("records");
        for (int i = entries.size() - 1; i >= 0; i--) if (entries.get(i).path("type").asText().equals("OBSERVATION")) entries.remove(i);
        rehashManifest(root);
        var failure = assertThrows(IOException.class, () -> exchange.decode(json.writeValueAsBytes(root)));
        assertEquals("inconsistent self-contained history", failure.getMessage());
    }
    @Test void duplicateIdentityWithConflictingContentIsNeverRepairedOrDeduplicated() throws Exception {
        var root = (ObjectNode) json.readTree(exchange.encode(snapshot(false)));
        var records = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("records");
        var duplicate = records.get(0).deepCopy();
        ((ObjectNode) duplicate.path("data")).putArray("reasons").add("conflicting claim under the same identity");
        records.add(duplicate);
        assertThrows(IOException.class, () -> exchange.decode(json.writeValueAsBytes(root)));
    }
    @Test void timestampsParticipateInDigestsAndUnavailableValuesRemainAbsent() throws Exception {
        var review = review("a", Review.Decision.ACCEPTED, T);
        var later = new Review(review.reference(), review.observation(), review.reviewer(), review.process(), review.policy(), review.scope(),
                review.decision(), review.reasons(), T, T.plusSeconds(1), review.qualifications(), review.limitations());
        assertNotEquals(exchange.contentDigest(review), exchange.contentDigest(later));
        var o = observation(new Observation.Unknown("not reported"));
        var failed = new Observation(o.reference(), o.subject(), o.endpoint(), o.method(), o.context(), o.activity(), o.provenance(),
                Observation.Availability.FAILED_INVALID, Optional.empty(), o.uncertainty(), o.limitations());
        assertEquals(failed, exchange.decodeRecord(exchange.encodeRecord(failed)));
    }
    @Test void unsupportedRecordSchemaNumericEnumsAndNullDataAreRejected() throws Exception {
        var root = (ObjectNode) json.readTree(exchange.encodeRecord(ref(SUBJECT, "id")));
        root.put("schema", "mnemosyne-record/2");
        assertThrows(IOException.class, () -> exchange.decodeRecord(json.writeValueAsBytes(root)));
        root.put("schema", EvidenceExchange.RECORD_SCHEMA);
        ((ObjectNode) root.get("data")).put("kind", 2);
        assertThrows(IOException.class, () -> exchange.decodeRecord(json.writeValueAsBytes(root)));
        root.putNull("data"); assertThrows(IOException.class, () -> exchange.decodeRecord(json.writeValueAsBytes(root)));
    }
    @Test void numericTimestampAndInconsistentKnownTimeHistoryAreNotSilentlyRepaired() throws Exception {
        var r = review("a", Review.Decision.ACCEPTED, T);
        var record = (ObjectNode) json.readTree(exchange.encodeRecord(r));
        ((ObjectNode) record.get("data")).put("reviewedAt", 123);
        assertThrows(IOException.class, () -> exchange.decodeRecord(json.writeValueAsBytes(record)));
        // Phase 1 can append a late-imported withdrawal to an earlier working set.
        // An exchange must not publish an assessment that was already inadmissible at its recorded time.
        var a = assessment("a", r, Assessment.Outcome.SUPPORTS);
        var h = new EvidenceHistory().append(observation(new Observation.Unknown("unknown"))).append(r).append(a)
                .append(new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, "same-time"), r.reference(), Optional.empty(),
                        r.reviewer(), "Already withdrawn at assessment time", T, T));
        assertThrows(IOException.class, () -> exchange.snapshot(ref(SNAPSHOT, "invalid-time"), ref(ACTIVITY, "export"), T.plusSeconds(1), Optional.empty(), h));
    }

}
