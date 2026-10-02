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

class EvidenceBranchViewAcceptanceTest {
    @TempDir Path temporary;
    private final EvidenceExchange exchange = new EvidenceExchange();
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");
    private ScientificReference ref(ScientificReference.Kind kind, String id) {
        return new ScientificReference(kind, "synthetic-branch-view", id, "1");
    }
    private Observation observation(String id) {
        return new Observation(ref(OBSERVATION, id), ref(SUBJECT, "subject"), ref(ENDPOINT, "endpoint"), ref(METHOD, "method"),
                ref(CONTEXT, "context"), ref(ACTIVITY, "run"),
                new Observation.Provenance(ref(SOURCE, "source"), new ScientificReference(ARTIFACT, "sha256", "a".repeat(64), "1"),
                        ref(RECEIPT, "receipt"), ref(METHOD, "projection"), "synthetic row", List.of(ref(RECEIPT, "prior"))),
                Observation.Availability.PRESENT, Optional.of(new Observation.Scalar("1.00", "unit")),
                new Observation.Unknown("not reported"), List.of("synthetic software fixture"));
    }
    private Review review(String id, Review.Decision decision, Instant at) {
        return new Review(ref(REVIEW, id), ref(OBSERVATION, "o"), ref(AGENT, "reviewer"), ref(METHOD, "review-process"),
                ref(POLICY, "policy"), ref(CONTEXT, "context"), decision, List.of("attributed reason"), at, at,
                List.of(ref(RECEIPT, "qualification")), List.of("bounded scope"));
    }
    private Assessment assessment(String id, Assessment.Outcome outcome, String reason, Instant at) {
        return new Assessment(ref(ASSESSMENT, id), Optional.of(ref(OBSERVATION, "o")), Optional.of(ref(REVIEW, "r")),
                ref(PROPOSITION, "p"), ref(CRITERION, "c"), ref(POLICY, "policy"), ref(CONTEXT, "context"),
                outcome, List.of(reason), at);
    }
    private EvidenceHistory base() {
        return new EvidenceHistory().append(observation("o")).append(review("r", Review.Decision.ACCEPTED, T))
                .append(assessment("original", Assessment.Outcome.SUPPORTS, "original interpretation", T));
    }
    private EvidenceExchange.Snapshot snapshot(String id, String parent, EvidenceHistory history, Instant at) throws IOException {
        return exchange.snapshot(ref(SNAPSHOT, id), ref(ACTIVITY, "capture"), at, Optional.ofNullable(parent).map(p -> ref(SNAPSHOT, p)), history);
    }
    private EvidenceAdmission.Pin pin(EvidenceExchange.Snapshot s) throws IOException {
        return new EvidenceAdmission.Pin(s.manifest().reference(), EvidenceExchange.sha256(exchange.encode(s)));
    }
    private EvidenceSnapshotCatalog catalog(String name) throws IOException {
        return new EvidenceSnapshotCatalog(Files.createDirectory(temporary.resolve(name)));
    }
    private void append(EvidenceSnapshotCatalog catalog, EvidenceExchange.Snapshot parent, EvidenceExchange.Snapshot child) throws IOException {
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, catalog.append(child, new EvidenceAdmission.Expectation(pin(parent), pin(child))).status());
    }
    private EvidenceHistory.ReviewChange withdrawal(String id, String review, Instant at) {
        return new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, id), ref(REVIEW, review), Optional.empty(),
                ref(AGENT, "reviewer"), "attributed withdrawal", at, at);
    }
    private record Branches(EvidenceSnapshotCatalog catalog, EvidenceExchange.Snapshot root,
                            EvidenceExchange.Snapshot left, EvidenceExchange.Snapshot right) { }
    private Branches branches(String directory, boolean reverse) throws IOException {
        var catalog = catalog(directory); var root = snapshot("root", null, base(), T);
        var leftHistory = root.history().append(assessment("branch", Assessment.Outcome.CONTRADICTS, "left interpretation", T.plusSeconds(1)))
                .append(withdrawal("withdraw-r", "r", T.plusSeconds(2)));
        var rightHistory = root.history().append(assessment("branch", Assessment.Outcome.SUPPORTS, "right interpretation", T.plusSeconds(1)));
        var left = snapshot("left", "root", leftHistory, T.plusSeconds(3));
        var right = snapshot("right", "root", rightHistory, T.plusSeconds(3));
        catalog.seed(root, pin(root));
        for (var s : reverse ? List.of(right, left) : List.of(left, right)) append(catalog, root, s);
        return new Branches(catalog, root, left, right);
    }
    private EvidenceBranchView.ReviewState reviewed(EvidenceBranchView view, String id) {
        return view.reviews().stream().filter(r -> r.record().reference().equals(ref(REVIEW, id))).findFirst().orElseThrow();
    }
    private EvidenceBranchView.RecordProvenance provenance(EvidenceBranchView view, ScientificReference ref) {
        return view.provenance().stream().filter(p -> p.record().reference().equals(ref)).findFirst().orElseThrow();
    }
    private Map<String,String> inventory(String directory) throws IOException {
        var root = temporary.resolve(directory); var result = new TreeMap<String,String>();
        result.put("directory-mtime", Files.getLastModifiedTime(root).toString());
        try (var files = Files.list(root)) {
            for (var f : files.toList()) result.put(f.getFileName().toString(), EvidenceExchange.sha256(Files.readAllBytes(f)) + ":" + Files.getLastModifiedTime(f));
        }
        return result;
    }
    private Path path(String directory, EvidenceExchange.Snapshot s) throws IOException {
        return temporary.resolve(directory).resolve(EvidenceExchange.sha256(exchange.encodeRecord(s.manifest().reference())) + ".snapshot.json");
    }
    private void assertSameView(EvidenceBranchView a, EvidenceBranchView b) {
        assertEquals(a.selected(), b.selected()); assertEquals(a.asOf(), b.asOf()); assertEquals(a.path(), b.path());
        assertEquals(a.observations(), b.observations()); assertEquals(a.reviews(), b.reviews());
        assertEquals(a.assessments(), b.assessments()); assertEquals(a.changes(), b.changes()); assertEquals(a.provenance(), b.provenance());
    }
    @Test void selectedSnapshotOnlyPreservesHistoricalRecordsAndEffectiveWithdrawal() throws Exception {
        var b = branches("catalog", false); var before = inventory("catalog");
        var view = EvidenceBranchView.load(b.catalog(), ref(SNAPSHOT, "left"));
        assertEquals(pin(b.left()), view.selected()); assertEquals(T.plusSeconds(3), view.asOf());
        assertEquals(List.of(pin(b.root()), pin(b.left())), view.path());
        assertEquals(List.of(observation("o")), view.observations());
        assertEquals(2, view.assessments().size());
        assertTrue(view.assessments().stream().anyMatch(a -> a.outcome() == Assessment.Outcome.SUPPORTS));
        assertTrue(view.assessments().stream().anyMatch(a -> a.outcome() == Assessment.Outcome.CONTRADICTS));
        assertFalse(reviewed(view, "r").admissible());
        assertEquals(Optional.of(withdrawal("withdraw-r", "r", T.plusSeconds(2))), reviewed(view, "r").change());
        assertEquals(List.of(withdrawal("withdraw-r", "r", T.plusSeconds(2))), view.changes());
        assertEquals(b.left().history().assessments().get(ref(ASSESSMENT, "original")),
                view.assessments().stream().filter(a -> a.reference().equals(ref(ASSESSMENT, "original"))).findFirst().orElseThrow());
        assertEquals(before, inventory("catalog"));
    }
    @Test void siblingSameIdentityDisagreementAndWithdrawalsNeverLeakAcrossBranches() throws Exception {
        var b = branches("catalog", false);
        var left = EvidenceBranchView.load(b.catalog(), ref(SNAPSHOT, "left"));
        var right = EvidenceBranchView.load(b.catalog(), ref(SNAPSHOT, "right"));
        assertFalse(reviewed(left, "r").admissible()); assertTrue(reviewed(right, "r").admissible());
        assertTrue(right.changes().isEmpty()); assertTrue(reviewed(right, "r").change().isEmpty());
        assertEquals(Assessment.Outcome.CONTRADICTS, left.assessments().stream().filter(a -> a.reference().equals(ref(ASSESSMENT, "branch"))).findFirst().orElseThrow().outcome());
        assertEquals(Assessment.Outcome.SUPPORTS, right.assessments().stream().filter(a -> a.reference().equals(ref(ASSESSMENT, "branch"))).findFirst().orElseThrow().outcome());
        assertEquals(pin(b.left()), provenance(left, ref(ASSESSMENT, "branch")).firstIncludedIn());
        assertEquals(pin(b.right()), provenance(right, ref(ASSESSMENT, "branch")).firstIncludedIn());
    }
    @Test void earlierSnapshotDoesNotAcquireLaterRecordsOrWithdrawals() throws Exception {
        var b = branches("catalog", false); var view = EvidenceBranchView.load(b.catalog(), ref(SNAPSHOT, "root"));
        assertEquals(T, view.asOf()); assertEquals(List.of(pin(b.root())), view.path());
        assertEquals(1, view.assessments().size()); assertTrue(view.changes().isEmpty());
        assertTrue(reviewed(view, "r").admissible());
        assertTrue(view.provenance().stream().allMatch(p -> p.firstIncludedIn().equals(view.selected())));
    }
    @Test void provenanceTracksFirstPathInclusionAndExactContentWithoutDuplicatingInheritedRecords() throws Exception {
        var b = branches("catalog", false);
        var grandchild = snapshot("grandchild", "left", b.left().history().append(observation("new")), T.plusSeconds(4));
        append(b.catalog(), b.left(), grandchild);
        var view = EvidenceBranchView.load(b.catalog(), ref(SNAPSHOT, "grandchild"));
        assertEquals(List.of(pin(b.root()), pin(b.left()), pin(grandchild)), view.path());
        assertEquals(pin(b.root()), provenance(view, ref(OBSERVATION, "o")).firstIncludedIn());
        assertEquals(pin(b.left()), provenance(view, ref(REVIEW_CHANGE, "withdraw-r")).firstIncludedIn());
        assertEquals(pin(grandchild), provenance(view, ref(OBSERVATION, "new")).firstIncludedIn());
        assertEquals(grandchild.manifest().records(), view.provenance().stream().map(EvidenceBranchView.RecordProvenance::record).toList());
        assertEquals(exchange.contentDigest(observation("o")), provenance(view, ref(OBSERVATION, "o")).record().sha256());
        assertEquals(observation("o").provenance(), view.observations().stream().filter(o -> o.reference().equals(ref(OBSERVATION, "o"))).findFirst().orElseThrow().provenance());
    }
    @Test void supersessionAndSubsequentWithdrawalRetainBothReviewsAndOriginalAssessments() throws Exception {
        var catalog = catalog("catalog"); var root = snapshot("root", null, base(), T); catalog.seed(root, pin(root));
        var replacement = review("replacement", Review.Decision.ACCEPTED, T.plusSeconds(1));
        var supersession = new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, "supersede"), ref(REVIEW, "r"),
                Optional.of(replacement.reference()), replacement.reviewer(), "later review", T.plusSeconds(1), T.plusSeconds(1));
        var child = snapshot("child", "root", root.history().append(replacement).append(supersession), T.plusSeconds(2)); append(catalog, root, child);
        var view = EvidenceBranchView.load(catalog, ref(SNAPSHOT, "child"));
        assertFalse(reviewed(view, "r").admissible()); assertTrue(reviewed(view, "replacement").admissible());
        assertEquals(Optional.of(supersession), reviewed(view, "r").change());
        var grandchild = snapshot("grandchild", "child", child.history().append(withdrawal("withdraw-new", "replacement", T.plusSeconds(3))), T.plusSeconds(4));
        append(catalog, child, grandchild);
        var later = EvidenceBranchView.load(catalog, ref(SNAPSHOT, "grandchild"));
        assertEquals(2, later.reviews().size()); assertEquals(2, later.changes().size());
        assertTrue(later.reviews().stream().noneMatch(EvidenceBranchView.ReviewState::admissible));
        assertEquals(view.assessments(), later.assessments());
        assertTrue(reviewed(view, "replacement").admissible(), "captured view is immutable");
    }
    @Test void rejectedReviewsAndNotMeasuredAssessmentsRemainExplicitWithoutInventingSupport() throws Exception {
        var catalog = catalog("catalog");
        var missing = new Assessment(ref(ASSESSMENT, "coverage"), Optional.empty(), Optional.empty(), ref(PROPOSITION, "p"),
                ref(CRITERION, "c"), ref(POLICY, "policy"), ref(CONTEXT, "context"), Assessment.Outcome.NOT_MEASURED, List.of("not measured"), T);
        var rejected = review("rejected", Review.Decision.REJECTED, T);
        var root = snapshot("root", null, new EvidenceHistory().append(observation("o")).append(rejected).append(missing), T);
        catalog.seed(root, pin(root)); var view = EvidenceBranchView.load(catalog, ref(SNAPSHOT, "root"));
        assertFalse(view.reviews().getFirst().admissible()); assertTrue(view.reviews().getFirst().change().isEmpty());
        assertEquals(List.of(missing), view.assessments()); assertEquals(List.of(observation("o")), view.observations());
    }
    @Test void lateRecordedWithdrawalDoesNotRewriteEarlierKnownState() throws Exception {
        var catalog = catalog("catalog"); var root = snapshot("root", null, base(), T); catalog.seed(root, pin(root));
        var earlier = snapshot("earlier", "root", root.history(), T.plusSeconds(2)); append(catalog, root, earlier);
        var delayed = new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, "delayed"), ref(REVIEW, "r"), Optional.empty(),
                ref(AGENT, "reviewer"), "recorded later", T.plusSeconds(1), T.plusSeconds(4));
        var later = snapshot("later", "earlier", earlier.history().append(delayed), T.plusSeconds(5)); append(catalog, earlier, later);
        var oldView = EvidenceBranchView.load(catalog, ref(SNAPSHOT, "earlier"));
        var newView = EvidenceBranchView.load(catalog, ref(SNAPSHOT, "later"));
        assertTrue(reviewed(oldView, "r").admissible()); assertTrue(oldView.changes().isEmpty());
        assertFalse(reviewed(newView, "r").admissible()); assertEquals(List.of(delayed), newView.changes());
        assertEquals(oldView.assessments(), newView.assessments());
        assertEquals(pin(later), provenance(newView, delayed.reference()).firstIncludedIn());
        assertEquals(T.plusSeconds(1), newView.changes().getFirst().effectiveAt());
        assertEquals(T.plusSeconds(4), newView.changes().getFirst().recordedAt());
    }
    @Test void recordInsertionOrderDoesNotChangeOutputOrFirstInclusionPins() throws Exception {
        var first = catalog("first"); var second = catalog("second");
        var a = snapshot("root", null, new EvidenceHistory().append(observation("z")).append(observation("a")), T);
        var b = snapshot("root", null, new EvidenceHistory().append(observation("a")).append(observation("z")), T);
        first.seed(a, pin(a)); second.seed(b, pin(b));
        var view = EvidenceBranchView.load(first, ref(SNAPSHOT, "root"));
        assertEquals(List.of(ref(OBSERVATION, "a"), ref(OBSERVATION, "z")), view.observations().stream().map(Observation::reference).toList());
        assertSameView(view, EvidenceBranchView.load(second, ref(SNAPSHOT, "root")));
    }
    @Test void outputIsDeterministicAcrossBranchInsertionOrderAndReopening() throws Exception {
        var first = branches("first", false); branches("second", true);
        var reopened = new EvidenceSnapshotCatalog(temporary.resolve("second"));
        for (String id : List.of("root", "left", "right"))
            assertSameView(EvidenceBranchView.load(first.catalog(), ref(SNAPSHOT, id)), EvidenceBranchView.load(reopened, ref(SNAPSHOT, id)));
    }
    @Test void allReturnedCollectionsAreImmutable() throws Exception {
        var b = branches("catalog", false); var view = EvidenceBranchView.load(b.catalog(), ref(SNAPSHOT, "left"));
        assertThrows(UnsupportedOperationException.class, () -> view.path().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.observations().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.reviews().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.assessments().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.changes().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.provenance().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.assessments().getFirst().reasons().clear());
    }
    @Test void explicitSelectionIsRequiredAndEmptyRootIsValid() throws Exception {
        var catalog = catalog("catalog");
        assertThrows(IllegalArgumentException.class, () -> EvidenceBranchView.load(catalog, ref(SNAPSHOT, "missing")));
        assertThrows(NullPointerException.class, () -> EvidenceBranchView.load(catalog, null));
        assertThrows(IllegalArgumentException.class, () -> EvidenceBranchView.load(catalog, ref(OBSERVATION, "o")));
        var empty = snapshot("empty", null, new EvidenceHistory(), T); catalog.seed(empty, pin(empty));
        var view = EvidenceBranchView.load(catalog, ref(SNAPSHOT, "empty"));
        assertEquals(List.of(pin(empty)), view.path()); assertTrue(view.provenance().isEmpty());
        assertTrue(view.observations().isEmpty()); assertTrue(view.assessments().isEmpty());
    }
    @Test void orphanOrCycleAnywhereFailsClosedWithoutReconstructingPartialBranch() throws Exception {
        var b = branches("catalog", false);
        var orphan = snapshot("orphan", "absent", new EvidenceHistory(), T);
        Files.write(path("catalog", orphan), exchange.encode(orphan)); // Deliberate corruption in test-only catalog.
        var before = inventory("catalog");
        assertTrue(assertThrows(IOException.class, () -> EvidenceBranchView.load(b.catalog(), ref(SNAPSHOT, "left"))).getMessage().contains("ORPHAN_SNAPSHOT"));
        assertEquals(before, inventory("catalog"));
        var other = branches("cycles", false);
        var a = snapshot("cycle-a", "cycle-b", new EvidenceHistory(), T); var c = snapshot("cycle-b", "cycle-a", new EvidenceHistory(), T);
        Files.write(path("cycles", a), exchange.encode(a)); Files.write(path("cycles", c), exchange.encode(c));
        var cycleBefore = inventory("cycles");
        assertTrue(assertThrows(IOException.class, () -> EvidenceBranchView.load(other.catalog(), ref(SNAPSHOT, "left"))).getMessage().contains("CYCLIC_LINEAGE"));
        assertEquals(cycleBefore, inventory("cycles"));
    }
    @Test void corruptedAncestorAndInvalidInheritedContentFailClosedWithoutWrites() throws Exception {
        var b = branches("catalog", false); Files.writeString(path("catalog", b.root()), "broken");
        var before = inventory("catalog");
        assertThrows(IOException.class, () -> EvidenceBranchView.load(b.catalog(), ref(SNAPSHOT, "left")));
        assertEquals(before, inventory("catalog"));
        var other = branches("invalid", false);
        var invalid = snapshot("left", "root", new EvidenceHistory(), T.plusSeconds(3));
        Files.write(path("invalid", invalid), exchange.encode(invalid));
        var invalidBefore = inventory("invalid");
        assertTrue(assertThrows(IOException.class, () -> EvidenceBranchView.load(other.catalog(), ref(SNAPSHOT, "left"))).getMessage().contains("INVALID_LINEAGE_LINK"));
        assertEquals(invalidBefore, inventory("invalid"));
    }
}
