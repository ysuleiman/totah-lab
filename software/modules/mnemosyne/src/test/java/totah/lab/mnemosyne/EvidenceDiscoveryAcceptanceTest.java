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
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;
import static totah.lab.mnemosyne.DiscoveryDescription.Modality.*;

class EvidenceDiscoveryAcceptanceTest {
    @TempDir Path temporary;
    private final EvidenceExchange exchange = new EvidenceExchange();
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");
    private ScientificReference ref(ScientificReference.Kind kind, String id) { return new ScientificReference(kind, "synthetic-discovery", id, "1"); }
    private DiscoveryDescription.Term term(String id) { return new DiscoveryDescription.Term("synthetic-vocabulary", id, "1"); }
    private Observation.Provenance provenance(String id) {
        return new Observation.Provenance(ref(SOURCE, id), ref(ARTIFACT, id), ref(RECEIPT, id), ref(METHOD, "projection"), "synthetic://" + id + "#row=1", List.of());
    }
    private Observation observation(String id, String value) {
        return new Observation(ref(OBSERVATION, id), ref(SUBJECT, "opaque-run-" + id), ref(ENDPOINT, "endpoint"), ref(METHOD, "method"),
                ref(CONTEXT, "scope"), ref(ACTIVITY, "run-" + id), provenance(id), Observation.Availability.PRESENT,
                Optional.of(new Observation.Scalar(value, "unit")), new Observation.Unknown("not reported"), List.of("synthetic"));
    }
    private DiscoveryDescription.Participant participant(String entity, String role, String... labels) {
        return new DiscoveryDescription.Participant(ref(SUBJECT, entity), term(role), List.of(labels));
    }
    private DiscoveryDescription description(String id, Observation o, DiscoveryDescription.Modality modality,
                                             DiscoveryDescription.Participant... participants) throws IOException {
        return new DiscoveryDescription(ref(DISCOVERY_DESCRIPTION, id), o.reference(), exchange.contentDigest(o), List.of(participants),
                term("measured-with"), modality, ref(AGENT, "curator"), provenance("description-" + id), T);
    }
    private DiscoveryDescription description(String id, Observation o) throws IOException {
        return description(id, o, EXPERIMENTAL, participant("alpha", "first", "Alpha"), participant("beta", "second", "Beta", "B"));
    }
    private DiscoveryDescription.Withdrawal withdrawal(String id, DiscoveryDescription d, Optional<ScientificReference> replacement) {
        return new DiscoveryDescription.Withdrawal(ref(DISCOVERY_WITHDRAWAL, id), d.reference(), replacement, d.agent(), provenance(id), "explicit withdrawal", T.plusSeconds(2));
    }
    private EvidenceExchange.Snapshot snapshot(String id, String parent, EvidenceHistory h) throws IOException {
        return exchange.snapshot(ref(SNAPSHOT, id), ref(ACTIVITY, "capture"), T.plusSeconds(3), Optional.ofNullable(parent).map(p -> ref(SNAPSHOT, p)), h);
    }
    private EvidenceAdmission.Pin pin(EvidenceExchange.Snapshot s) throws IOException { return new EvidenceAdmission.Pin(s.manifest().reference(), EvidenceExchange.sha256(exchange.encode(s))); }
    private EvidenceSnapshotCatalog catalog(String name, EvidenceExchange.Snapshot root) throws IOException {
        var c = new EvidenceSnapshotCatalog(Files.createDirectory(temporary.resolve(name)));
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, c.seed(root, pin(root)).status()); return c;
    }
    private void append(EvidenceSnapshotCatalog c, EvidenceExchange.Snapshot p, EvidenceExchange.Snapshot s) throws IOException {
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, c.append(s, new EvidenceAdmission.Expectation(pin(p), pin(s))).status());
    }
    private EvidenceDiscovery discovery(String name) throws IOException { return new EvidenceDiscovery(new EvidenceQueries(temporary.resolve(name))); }
    private EvidenceDiscovery.ParticipantConstraint entity(String id) {
        return new EvidenceDiscovery.ParticipantConstraint(Optional.of(ref(SUBJECT, id)), Optional.empty(), Optional.empty());
    }
    private EvidenceDiscovery.ParticipantConstraint label(String name) {
        return new EvidenceDiscovery.ParticipantConstraint(Optional.empty(), Optional.of(name), Optional.empty());
    }
    private EvidenceDiscovery.Query query(EvidenceDiscovery.ParticipantConstraint... constraints) {
        return new EvidenceDiscovery.Query(List.of(constraints), Optional.empty(), Optional.of(EXPERIMENTAL), Optional.empty());
    }
    private List<String> ids(EvidenceDiscovery.Result r) { return r.matches().stream().map(m -> m.observation().reference().id()).toList(); }
    private Map<String,String> inventory(String name) throws IOException {
        var directory = temporary.resolve(name); var result = new TreeMap<String,String>();
        result.put("directory", Files.getLastModifiedTime(directory).toString());
        try (var files = Files.list(directory)) {
            for (var f : files.toList()) result.put(f.getFileName().toString(), EvidenceExchange.sha256(Files.readAllBytes(f)) + ":" + Files.getLastModifiedTime(f));
        }
        return result;
    }
    @Test void exactEntitiesRolesRelationshipAndEndpointReturnOriginalEvidenceAndProvenanceWithoutWrites() throws Exception {
        var o = observation("o", "1.00"); var d = description("d", o); var s = snapshot("root", null, new EvidenceHistory().append(o).append(d));
        catalog("catalog", s); var before = inventory("catalog"); var q = new EvidenceDiscovery.Query(
                List.of(new EvidenceDiscovery.ParticipantConstraint(Optional.of(ref(SUBJECT, "alpha")), Optional.empty(), Optional.of(term("first"))), entity("beta")),
                Optional.of(term("measured-with")), Optional.of(EXPERIMENTAL), Optional.of(o.endpoint()));
        var r = discovery("catalog").find(s.manifest().reference(), q);
        assertEquals(EvidenceDiscovery.Status.MATCHES, r.status()); assertEquals(List.of("o"), ids(r));
        assertEquals(o, r.matches().getFirst().observation()); assertEquals(d, r.matches().getFirst().descriptions().getFirst().record());
        assertEquals(d.provenance(), r.matches().getFirst().descriptions().getFirst().record().provenance());
        assertEquals(pin(s), r.evidence().selected()); assertEquals(List.of(pin(s)), r.evidence().path());
        assertTrue(r.evidence().provenance().stream().allMatch(p -> p.firstIncludedIn().equals(r.evidence().selected())));
        assertEquals(before, inventory("catalog"));
        assertTrue(discovery("catalog").find(s.manifest().reference(), new EvidenceDiscovery.Query(q.participants(), Optional.of(term("different")), q.modality(), q.endpoint())).matches().isEmpty());
        assertTrue(discovery("catalog").find(s.manifest().reference(), new EvidenceDiscovery.Query(q.participants(), q.relationship(), q.modality(), Optional.of(ref(ENDPOINT, "different")))).matches().isEmpty());
        assertTrue(discovery("catalog").find(s.manifest().reference(), query(new EvidenceDiscovery.ParticipantConstraint(Optional.of(ref(SUBJECT, "alpha")), Optional.empty(), Optional.of(term("second"))))).matches().isEmpty());
    }
    @Test void explicitAliasesWorkButCaseAndUnrecordedAliasesAreNeverInferred() throws Exception {
        var o = observation("o", "1"); var s = snapshot("root", null, new EvidenceHistory().append(o).append(description("d", o))); catalog("catalog", s);
        var r = discovery("catalog").find(s.manifest().reference(), query(label("Alpha"), label("B")));
        assertEquals(List.of("o"), ids(r)); assertEquals(ref(SUBJECT, "beta"), r.names().get(1).candidates().getFirst().entity());
        assertEquals(List.of(ref(DISCOVERY_DESCRIPTION, "d")), r.names().get(1).candidates().getFirst().descriptions());
        assertTrue(discovery("catalog").find(s.manifest().reference(), query(label("alpha"))).matches().isEmpty());
        assertTrue(discovery("catalog").find(s.manifest().reference(), query(label("unrecorded"))).matches().isEmpty());
    }
    @Test void ambiguousNamesExposeAllCandidatesAndNeverGuessEvenWhenOneDescriptionIsWithdrawn() throws Exception {
        var a = observation("a", "1"); var b = observation("b", "2");
        var da = description("a", a, EXPERIMENTAL, participant("one", "first", "Shared"));
        var db = description("b", b, EXPERIMENTAL, participant("two", "first", "Shared"));
        var s = snapshot("root", null, new EvidenceHistory().append(a).append(b).append(da).append(db).append(withdrawal("w", da, Optional.empty())));
        catalog("catalog", s); var r = discovery("catalog").find(s.manifest().reference(), query(label("Shared")));
        assertEquals(EvidenceDiscovery.Status.AMBIGUOUS_NAME, r.status()); assertTrue(r.matches().isEmpty());
        assertEquals(List.of(ref(SUBJECT, "one"), ref(SUBJECT, "two")), r.names().getFirst().candidates().stream().map(EvidenceDiscovery.Candidate::entity).toList());
        assertEquals(List.of("a"), ids(discovery("catalog").find(s.manifest().reference(), query(entity("one")))));
    }
    @Test void modalityIsExplicitAndUnknownNeverBecomesExperimental() throws Exception {
        var h = new EvidenceHistory();
        for (var modality : DiscoveryDescription.Modality.values()) {
            var o = observation(modality.name(), "1"); h = h.append(o).append(description(modality.name(), o, modality, participant("alpha", "first", "Alpha")));
        }
        var s = snapshot("root", null, h); catalog("catalog", s);
        var r = discovery("catalog").find(s.manifest().reference(), query(entity("alpha")));
        assertEquals(List.of("EXPERIMENTAL"), ids(r)); assertEquals(List.of(ref(DISCOVERY_DESCRIPTION, "UNKNOWN")), r.descriptionsWithUnknownModality());
        assertTrue(r.limitations().contains(EvidenceDiscovery.Limitation.UNKNOWN_MODALITY_PRESENT));
        assertEquals(5, discovery("catalog").find(s.manifest().reference(), new EvidenceDiscovery.Query(List.of(entity("alpha")), Optional.empty(), Optional.empty(), Optional.empty())).matches().size());
    }
    @Test void legacyAndPartialCoverageDoNotClaimNoExperimentExists() throws Exception {
        var a = observation("a", "1"); var b = observation("b", "2"); var root = snapshot("root", null, new EvidenceHistory().append(a).append(b));
        var c = catalog("catalog", root); var later = snapshot("later", "root", root.history().append(description("d", a))); append(c, root, later);
        var empty = discovery("catalog").find(root.manifest().reference(), query(entity("alpha")));
        assertEquals(EvidenceDiscovery.Status.NO_MATCH_IN_SELECTED_SNAPSHOT, empty.status()); assertEquals(2, empty.observationsWithoutDescription().size());
        assertEquals(2, empty.evidence().observations().size());
        var partial = discovery("catalog").find(later.manifest().reference(), query(entity("alpha")));
        assertEquals(List.of(ref(OBSERVATION, "b")), partial.observationsWithoutDescription());
        assertTrue(partial.limitations().containsAll(List.of(EvidenceDiscovery.Limitation.PARTIAL_DISCOVERY_COVERAGE,
                EvidenceDiscovery.Limitation.SOURCE_AVAILABILITY_NOT_CHECKED, EvidenceDiscovery.Limitation.ASSAY_DETAILS_NOT_MODELED,
                EvidenceDiscovery.Limitation.EXPERIMENT_EVENT_TIME_NOT_MODELED)));
    }
    @Test void participantsMustOccurInOneDescriptionWithoutJoiningObservationsOrDescriptions() throws Exception {
        var o = observation("o", "1"); var other = observation("other", "2"); var h = new EvidenceHistory().append(o).append(other)
                .append(description("a", o, EXPERIMENTAL, participant("alpha", "first", "Alpha")))
                .append(description("b", o, EXPERIMENTAL, participant("beta", "second", "Beta")))
                .append(description("c", other, EXPERIMENTAL, participant("gamma", "second", "Gamma")));
        var s = snapshot("root", null, h); catalog("catalog", s);
        assertTrue(discovery("catalog").find(s.manifest().reference(), query(entity("alpha"), entity("beta"))).matches().isEmpty());
        assertTrue(discovery("catalog").find(s.manifest().reference(), query(entity("alpha"), entity("gamma"))).matches().isEmpty());
    }
    @Test void oldNewAndSameValueRunsRemainDistinctAndConflictingDescriptionsRemainVisible() throws Exception {
        var old = observation("old", "1"); var root = snapshot("root", null, new EvidenceHistory().append(old).append(description("old", old)));
        var c = catalog("catalog", root); var newer = observation("new", "2"); var repeat = observation("repeat", "1");
        var s = snapshot("later", "root", root.history().append(newer).append(repeat).append(description("new", newer)).append(description("repeat", repeat))
                .append(description("disagreement", old, COMPUTATIONAL, participant("alpha", "first", "Alpha"), participant("beta", "second", "Beta"))));
        append(c, root, s); var r = discovery("catalog").find(s.manifest().reference(), query(entity("alpha"), entity("beta")));
        assertEquals(List.of("new", "old", "repeat"), ids(r)); assertEquals(List.of("old"), ids(discovery("catalog").find(root.manifest().reference(), query(entity("alpha")))));
        var all = discovery("catalog").find(s.manifest().reference(), new EvidenceDiscovery.Query(List.of(entity("alpha")), Optional.empty(), Optional.empty(), Optional.empty()));
        assertEquals(2, all.matches().stream().filter(m -> m.observation().equals(old)).findFirst().orElseThrow().descriptions().size());
        assertEquals(pin(root), r.evidence().provenance().stream().filter(p -> p.record().reference().equals(old.reference())).findFirst().orElseThrow().firstIncludedIn());
    }
    @Test void siblingDisagreementsAndWithdrawalsStayBranchLocalWithHistoryAndReplay() throws Exception {
        var o = observation("o", "1"); var d = description("original", o); var root = snapshot("root", null, new EvidenceHistory().append(o).append(d)); var c = catalog("catalog", root);
        var ld = description("branch", o, EXPERIMENTAL, participant("left", "first", "Left"));
        var rd = description("branch", o, EXPERIMENTAL, participant("right", "first", "Right"));
        var left = snapshot("left", "root", root.history().append(ld).append(withdrawal("w", d, Optional.empty())));
        var right = snapshot("right", "root", root.history().append(rd)); append(c, root, right); append(c, root, left);
        var before = inventory("catalog");
        assertEquals(EvidenceSnapshotCatalog.Status.REPLAY, c.append(left, new EvidenceAdmission.Expectation(pin(root), pin(left))).status());
        var l = discovery("catalog").find(left.manifest().reference(), query(entity("alpha"))); var r = discovery("catalog").find(right.manifest().reference(), query(entity("alpha")));
        assertTrue(l.matches().getFirst().descriptions().getFirst().withdrawal().isPresent()); assertTrue(r.matches().getFirst().descriptions().getFirst().withdrawal().isEmpty());
        assertTrue(discovery("catalog").find(left.manifest().reference(), query(entity("right"))).matches().isEmpty());
        assertTrue(discovery("catalog").find(right.manifest().reference(), query(entity("left"))).matches().isEmpty());
        assertEquals(before, inventory("catalog"));
    }
    @Test void reviewWithdrawalAndContradictoryAssessmentsArePreservedByDiscovery() throws Exception {
        var o = observation("o", "1"); var review = new Review(ref(REVIEW, "r"), o.reference(), ref(AGENT, "reviewer"), ref(METHOD, "review"), ref(POLICY, "policy"), o.context(), Review.Decision.ACCEPTED, List.of("attributed"), T, T, List.of(), List.of());
        var h = new EvidenceHistory().append(o).append(review).append(description("d", o));
        for (var outcome : List.of(Assessment.Outcome.SUPPORTS, Assessment.Outcome.CONTRADICTS)) h = h.append(new Assessment(ref(ASSESSMENT, outcome.name()), Optional.of(o.reference()), Optional.of(review.reference()), ref(PROPOSITION, "p"), ref(CRITERION, "criterion"), ref(POLICY, "policy"), o.context(), outcome, List.of("attributed"), T));
        h = h.append(new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, "w"), review.reference(), Optional.empty(), review.reviewer(), "withdrawn", T.plusSeconds(1), T.plusSeconds(1)));
        var root = snapshot("root", null, h); catalog("catalog", root); var r = discovery("catalog").find(root.manifest().reference(), query(entity("alpha")));
        assertEquals(1, r.matches().size()); assertEquals(2, r.evidence().assessments().size()); assertFalse(r.evidence().reviews().getFirst().admissible());
        assertEquals(1, r.evidence().changes().size());
    }
    @Test void descriptionsAndResultsAreImmutableAndDeterministicAcrossInsertionOrders() throws Exception {
        var o = observation("o", "1"); var a = description("a", o); var b = description("b", o); var h = new EvidenceHistory().append(o);
        var first = snapshot("root", null, h.append(a).append(b)); var second = snapshot("root", null, h.append(b).append(a));
        assertArrayEquals(exchange.encode(first), exchange.encode(second)); catalog("a", first); catalog("b", second);
        var ra = discovery("a").find(first.manifest().reference(), query(label("Alpha"))); var rb = discovery("b").find(second.manifest().reference(), query(label("Alpha")));
        assertEquals(ra.matches(), rb.matches()); assertEquals(ra.names(), rb.names()); assertEquals(ra.evidence().provenance(), rb.evidence().provenance());
        assertThrows(UnsupportedOperationException.class, () -> ra.matches().clear()); assertThrows(UnsupportedOperationException.class, () -> ra.names().clear());
        assertThrows(UnsupportedOperationException.class, () -> ra.names().getFirst().candidates().clear());
        assertThrows(UnsupportedOperationException.class, () -> ra.matches().getFirst().descriptions().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.participants().clear()); assertThrows(UnsupportedOperationException.class, () -> a.participants().getFirst().labels().clear());
        assertThrows(UnsupportedOperationException.class, () -> ra.evidence().descriptions().clear()); assertThrows(UnsupportedOperationException.class, () -> ra.evidence().withdrawals().clear());
        assertThrows(UnsupportedOperationException.class, () -> ra.limitations().clear()); assertThrows(UnsupportedOperationException.class, () -> ra.observationsWithoutDescription().clear());
    }
    @Test void wrongIdentityNullAndAbsentSelectionsNeverChooseFallback() throws Exception {
        var root = snapshot("root", null, new EvidenceHistory()); catalog("catalog", root); var q = discovery("catalog");
        assertThrows(IllegalArgumentException.class, () -> q.find(ref(SNAPSHOT, "absent"), query(entity("alpha"))));
        assertThrows(IllegalArgumentException.class, () -> q.find(ref(OBSERVATION, "root"), query(entity("alpha"))));
        assertThrows(NullPointerException.class, () -> q.find(null, query(entity("alpha"))));
        assertThrows(NullPointerException.class, () -> q.find(root.manifest().reference(), null));
    }
    private Path path(String catalog, EvidenceExchange.Snapshot s) throws IOException {
        return temporary.resolve(catalog).resolve(EvidenceExchange.sha256(exchange.encodeRecord(s.manifest().reference())) + ".snapshot.json");
    }
    @Test void corruptOrphanCycleAndInvalidInheritanceFailClosedWithoutWrites() throws Exception {
        for (String mode : List.of("corrupt", "orphan", "cycle", "inheritance")) {
            var o = observation("o", "1"); var root = snapshot("root", null, new EvidenceHistory().append(o).append(description("d", o))); catalog(mode, root);
            var query = discovery(mode); assertEquals(1, query.find(root.manifest().reference(), query(entity("alpha"))).matches().size());
            if (mode.equals("corrupt")) Files.writeString(path(mode, root), "broken");
            else if (mode.equals("orphan")) { var orphan = snapshot("orphan", "absent", new EvidenceHistory()); Files.write(path(mode, orphan), exchange.encode(orphan)); }
            else if (mode.equals("cycle")) {
                var a = snapshot("a", "b", new EvidenceHistory()); var b = snapshot("b", "a", new EvidenceHistory());
                Files.write(path(mode, a), exchange.encode(a)); Files.write(path(mode, b), exchange.encode(b));
            } else { var child = snapshot("child", "root", new EvidenceHistory().append(o)); Files.write(path(mode, child), exchange.encode(child)); }
            var before = inventory(mode); assertThrows(IOException.class, () -> query.find(root.manifest().reference(), query(entity("alpha")))); assertEquals(before, inventory(mode));
        }
    }
}
