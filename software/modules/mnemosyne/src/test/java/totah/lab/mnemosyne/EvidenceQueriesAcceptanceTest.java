package totah.lab.mnemosyne;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

class EvidenceQueriesAcceptanceTest {
    @TempDir Path temporary;
    private final EvidenceExchange exchange = new EvidenceExchange();
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");
    private ScientificReference ref(ScientificReference.Kind kind, String id) {
        return new ScientificReference(kind, "synthetic-query", id, "1");
    }
    private EvidenceAdmission.Pin pin(EvidenceExchange.Snapshot s) throws IOException {
        return new EvidenceAdmission.Pin(s.manifest().reference(), EvidenceExchange.sha256(exchange.encode(s)));
    }
    private Observation observation() {
        return new Observation(ref(OBSERVATION, "o"), ref(SUBJECT, "subject"), ref(ENDPOINT, "endpoint"), ref(METHOD, "method"),
                ref(CONTEXT, "scope"), ref(ACTIVITY, "run"),
                new Observation.Provenance(ref(SOURCE, "source"), new ScientificReference(ARTIFACT, "sha256", "a".repeat(64), "1"),
                        ref(RECEIPT, "receipt"), ref(METHOD, "projection"), "synthetic row", List.of()),
                Observation.Availability.PRESENT, Optional.of(new Observation.Scalar("1.00", "unit")),
                new Observation.Unknown("not reported"), List.of("synthetic"));
    }
    private Assessment assessment(String id, Assessment.Outcome outcome) {
        return new Assessment(ref(ASSESSMENT, id), Optional.of(ref(OBSERVATION, "o")), Optional.of(ref(REVIEW, "r")),
                ref(PROPOSITION, "p"), ref(CRITERION, "c"), ref(POLICY, "policy"), ref(CONTEXT, "scope"), outcome, List.of("attributed reason"), T);
    }
    private EvidenceExchange.Snapshot snapshot(String id, String parent, EvidenceHistory history, Instant at) throws IOException {
        return exchange.snapshot(ref(SNAPSHOT, id), ref(ACTIVITY, "capture"), at,
                Optional.ofNullable(parent).map(p -> ref(SNAPSHOT, p)), history);
    }
    private record Fixture(Path directory, EvidenceSnapshotCatalog catalog, EvidenceExchange.Snapshot root,
                           EvidenceExchange.Snapshot left, EvidenceExchange.Snapshot right) { }
    private Fixture fixture(String name, boolean reverse) throws IOException {
        var directory = Files.createDirectory(temporary.resolve(name)); var catalog = new EvidenceSnapshotCatalog(directory);
        var review = new Review(ref(REVIEW, "r"), ref(OBSERVATION, "o"), ref(AGENT, "reviewer"), ref(METHOD, "process"),
                ref(POLICY, "policy"), ref(CONTEXT, "scope"), Review.Decision.ACCEPTED, List.of("attributed reason"), T, T, List.of(), List.of());
        var root = snapshot("root", null, new EvidenceHistory().append(observation()).append(review)
                .append(assessment("original", Assessment.Outcome.SUPPORTS)), T);
        var change = new EvidenceHistory.ReviewChange(ref(REVIEW_CHANGE, "withdrawal"), review.reference(), Optional.empty(),
                review.reviewer(), "withdrawn on left only", T.plusSeconds(1), T.plusSeconds(1));
        var left = snapshot("left", "root", root.history().append(assessment("branch", Assessment.Outcome.CONTRADICTS)).append(change), T.plusSeconds(2));
        var right = snapshot("right", "root", root.history().append(assessment("branch", Assessment.Outcome.SUPPORTS)), T.plusSeconds(2));
        catalog.seed(root, pin(root));
        for (var s : reverse ? List.of(right, left) : List.of(left, right))
            catalog.append(s, new EvidenceAdmission.Expectation(pin(root), pin(s)));
        return new Fixture(directory, catalog, root, left, right);
    }
    private List<String> ids(List<EvidenceSnapshotCatalog.Entry> entries) {
        return entries.stream().map(e -> e.pin().reference().id()).toList();
    }
    private Map<String,String> inventory(Path directory) throws IOException {
        var result = new TreeMap<String,String>(); result.put("directory-mtime", Files.getLastModifiedTime(directory).toString());
        try (var files = Files.list(directory)) {
            for (var f : files.toList()) result.put(f.getFileName().toString(), EvidenceExchange.sha256(Files.readAllBytes(f)) + ":" + Files.getLastModifiedTime(f));
        }
        return result;
    }
    private Path path(Fixture f, EvidenceExchange.Snapshot s) throws IOException {
        return f.directory().resolve(EvidenceExchange.sha256(exchange.encodeRecord(s.manifest().reference())) + ".snapshot.json");
    }
    private void assertSameView(EvidenceBranchView a, EvidenceBranchView b) {
        assertEquals(a.selected(), b.selected()); assertEquals(a.asOf(), b.asOf()); assertEquals(a.path(), b.path());
        assertEquals(a.observations(), b.observations()); assertEquals(a.reviews(), b.reviews());
        assertEquals(a.assessments(), b.assessments()); assertEquals(a.changes(), b.changes()); assertEquals(a.provenance(), b.provenance());
    }
    @Test void facadeExposesOnlyReadQueriesAndNoDefaultSnapshotOrCatalogHandle() {
        var methods = Arrays.stream(EvidenceQueries.class.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers())).toList();
        assertEquals(Set.of("lineage", "evidence"), methods.stream().map(m -> m.getName()).collect(Collectors.toSet()));
        assertEquals(2, methods.size());
        var evidence = methods.stream().filter(m -> m.getName().equals("evidence")).findFirst().orElseThrow();
        assertArrayEquals(new Class<?>[]{ScientificReference.class}, evidence.getParameterTypes());
        assertEquals(EvidenceBranchView.class, evidence.getReturnType());
        assertTrue(methods.stream().noneMatch(m -> m.getReturnType().equals(EvidenceSnapshotCatalog.class)));
        assertTrue(Arrays.stream(EvidenceQueries.class.getDeclaredFields()).allMatch(f -> Modifier.isPrivate(f.getModifiers()) && Modifier.isFinal(f.getModifiers())));
    }
    @Test void lineageQueriesPreserveAllBranchesAndStrictAncestorSemanticsWithoutWrites() throws Exception {
        var f = fixture("catalog", false); var before = inventory(f.directory()); var queries = new EvidenceQueries(f.directory());
        var view = queries.lineage(); var direct = EvidenceLineage.load(f.catalog());
        assertEquals(direct.roots(), view.roots()); assertEquals(direct.tips(), view.tips());
        assertEquals(List.of("root"), ids(view.roots())); assertEquals(List.of("left", "right"), ids(view.tips()));
        assertEquals(List.of("left", "right"), ids(view.children(ref(SNAPSHOT, "root"))));
        assertEquals(List.of("root"), ids(view.ancestry(ref(SNAPSHOT, "left"))));
        assertEquals(ref(SNAPSHOT, "root"), view.parent(ref(SNAPSHOT, "left")).orElseThrow().pin().reference());
        assertEquals(List.of("left", "right"), ids(view.descendants(ref(SNAPSHOT, "root"))));
        assertTrue(view.isAncestor(ref(SNAPSHOT, "root"), ref(SNAPSHOT, "left")));
        assertFalse(view.isAncestor(ref(SNAPSHOT, "left"), ref(SNAPSHOT, "right")));
        assertFalse(view.isAncestor(ref(SNAPSHOT, "root"), ref(SNAPSHOT, "root")));
        assertEquals(before, inventory(f.directory()));
    }
    @Test void explicitEvidenceQueriesPreserveBranchIsolationHistoryAndExactProvenance() throws Exception {
        var f = fixture("catalog", false); var before = inventory(f.directory()); var queries = new EvidenceQueries(f.directory());
        for (var s : List.of(f.root(), f.left(), f.right()))
            assertSameView(EvidenceBranchView.load(f.catalog(), s.manifest().reference()), queries.evidence(s.manifest().reference()));
        var left = queries.evidence(ref(SNAPSHOT, "left")); var right = queries.evidence(ref(SNAPSHOT, "right"));
        assertFalse(left.reviews().getFirst().admissible()); assertTrue(right.reviews().getFirst().admissible());
        assertEquals(1, left.changes().size()); assertTrue(right.changes().isEmpty());
        assertEquals(List.of(pin(f.root()), pin(f.left())), left.path());
        assertEquals(List.of(pin(f.root()), pin(f.right())), right.path());
        assertEquals(Assessment.Outcome.CONTRADICTS, left.assessments().stream().filter(a -> a.reference().equals(ref(ASSESSMENT, "branch"))).findFirst().orElseThrow().outcome());
        assertEquals(Assessment.Outcome.SUPPORTS, right.assessments().stream().filter(a -> a.reference().equals(ref(ASSESSMENT, "branch"))).findFirst().orElseThrow().outcome());
        assertEquals(observation(), left.observations().getFirst());
        assertEquals(pin(f.left()), left.provenance().stream().filter(p -> p.record().reference().equals(ref(REVIEW_CHANGE, "withdrawal"))).findFirst().orElseThrow().firstIncludedIn());
        assertEquals(1, queries.evidence(ref(SNAPSHOT, "root")).assessments().size());
        assertEquals(before, inventory(f.directory()));
    }
    @Test void orderingAndEvidenceAreDeterministicAcrossInstancesAndInsertionOrders() throws Exception {
        var a = fixture("a", false); var b = fixture("b", true);
        var first = new EvidenceQueries(a.directory()); var second = new EvidenceQueries(b.directory());
        assertEquals(first.lineage().roots(), second.lineage().roots()); assertEquals(first.lineage().tips(), second.lineage().tips());
        for (String id : List.of("root", "left", "right")) assertSameView(first.evidence(ref(SNAPSHOT, id)), second.evidence(ref(SNAPSHOT, id)));
    }
    @Test void returnedViewsStayImmutableAndFreshCallsObserveExplicitNewSnapshots() throws Exception {
        var f = fixture("catalog", false); var queries = new EvidenceQueries(f.directory());
        var prior = queries.lineage(); var evidence = queries.evidence(ref(SNAPSHOT, "root"));
        assertThrows(UnsupportedOperationException.class, () -> prior.roots().clear());
        assertThrows(UnsupportedOperationException.class, () -> prior.tips().clear());
        assertThrows(UnsupportedOperationException.class, () -> prior.children(ref(SNAPSHOT, "root")).clear());
        assertThrows(UnsupportedOperationException.class, () -> evidence.path().clear());
        assertThrows(UnsupportedOperationException.class, () -> evidence.observations().clear());
        assertThrows(UnsupportedOperationException.class, () -> evidence.reviews().clear());
        assertThrows(UnsupportedOperationException.class, () -> evidence.assessments().clear());
        assertThrows(UnsupportedOperationException.class, () -> evidence.provenance().clear());
        assertThrows(UnsupportedOperationException.class, () -> evidence.changes().clear());
        var child = snapshot("later", "right", f.right().history(), T.plusSeconds(3));
        f.catalog().append(child, new EvidenceAdmission.Expectation(pin(f.right()), pin(child)));
        assertEquals(List.of("left", "right"), ids(prior.tips()));
        assertEquals(List.of("later", "left"), ids(queries.lineage().tips()));
        assertEquals(pin(child), queries.evidence(ref(SNAPSHOT, "later")).selected());
        assertEquals(pin(f.root()), evidence.selected());
    }
    @Test void invalidSelectionAndAbsentDirectoryNeverCreateStateOrChooseFallback() throws Exception {
        var absent = temporary.resolve("absent");
        assertThrows(IOException.class, () -> new EvidenceQueries(absent)); assertFalse(Files.exists(absent));
        var empty = Files.createDirectory(temporary.resolve("empty")); var queries = new EvidenceQueries(empty); var before = inventory(empty);
        assertTrue(queries.lineage().roots().isEmpty()); assertTrue(queries.lineage().tips().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> queries.evidence(ref(SNAPSHOT, "missing")));
        assertThrows(IllegalArgumentException.class, () -> queries.evidence(ref(OBSERVATION, "wrong-kind")));
        assertThrows(NullPointerException.class, () -> queries.evidence(null));
        assertEquals(before, inventory(empty));
    }
    private void assertFailsClosed(EvidenceQueries queries) {
        assertThrows(IOException.class, queries::lineage);
        assertThrows(IOException.class, () -> queries.evidence(ref(SNAPSHOT, "left")));
    }
    @Test void corruptionAfterSuccessfulReadIsNotHiddenByFacadeCaching() throws Exception {
        var f = fixture("catalog", false); var queries = new EvidenceQueries(f.directory());
        assertEquals(2, queries.lineage().tips().size()); queries.evidence(ref(SNAPSHOT, "left"));
        Files.writeString(path(f, f.root()), "broken"); // Disposable test catalog corruption.
        var before = inventory(f.directory()); assertFailsClosed(queries); assertEquals(before, inventory(f.directory()));
    }
    @Test void orphanCycleAndInvalidInheritancePropagateCheckedFailuresWithoutWrites() throws Exception {
        var orphan = fixture("orphan", false); var missing = snapshot("orphan", "absent", new EvidenceHistory(), T);
        Files.write(path(orphan, missing), exchange.encode(missing));
        var before = inventory(orphan.directory()); assertFailsClosed(new EvidenceQueries(orphan.directory())); assertEquals(before, inventory(orphan.directory()));
        var cycle = fixture("cycle", false);
        var a = snapshot("cycle-a", "cycle-b", new EvidenceHistory(), T); var b = snapshot("cycle-b", "cycle-a", new EvidenceHistory(), T);
        Files.write(path(cycle, a), exchange.encode(a)); Files.write(path(cycle, b), exchange.encode(b));
        before = inventory(cycle.directory()); assertFailsClosed(new EvidenceQueries(cycle.directory())); assertEquals(before, inventory(cycle.directory()));
        var invalid = fixture("invalid", false); var dropped = snapshot("left", "root", new EvidenceHistory(), T.plusSeconds(2));
        Files.write(path(invalid, dropped), exchange.encode(dropped));
        before = inventory(invalid.directory()); assertFailsClosed(new EvidenceQueries(invalid.directory())); assertEquals(before, inventory(invalid.directory()));
    }
}
