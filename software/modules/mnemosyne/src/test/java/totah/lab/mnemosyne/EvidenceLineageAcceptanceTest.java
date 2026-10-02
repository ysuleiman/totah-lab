package totah.lab.mnemosyne;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

class EvidenceLineageAcceptanceTest {
    @TempDir Path temporary;
    private final EvidenceExchange exchange = new EvidenceExchange();
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");
    private ScientificReference ref(String id) { return new ScientificReference(SNAPSHOT, "synthetic-lineage", id, "1"); }
    private ScientificReference other(ScientificReference.Kind kind, String id) { return new ScientificReference(kind, "synthetic-lineage", id, "1"); }
    private EvidenceExchange.Snapshot snapshot(String id, String parent, EvidenceHistory history) throws Exception {
        return exchange.snapshot(ref(id), other(ACTIVITY, "capture"), T, Optional.ofNullable(parent).map(this::ref), history);
    }
    private EvidenceExchange.Snapshot snapshot(String id, String parent) throws Exception {
        return snapshot(id, parent, new EvidenceHistory());
    }
    private EvidenceAdmission.Pin pin(EvidenceExchange.Snapshot s) throws IOException {
        return new EvidenceAdmission.Pin(s.manifest().reference(), EvidenceExchange.sha256(exchange.encode(s)));
    }
    private EvidenceSnapshotCatalog catalog(String name) throws IOException {
        return new EvidenceSnapshotCatalog(Files.createDirectory(temporary.resolve(name)));
    }
    private void append(EvidenceSnapshotCatalog catalog, EvidenceExchange.Snapshot p, EvidenceExchange.Snapshot s) throws IOException {
        assertEquals(EvidenceSnapshotCatalog.Status.STORED, catalog.append(s, new EvidenceAdmission.Expectation(pin(p), pin(s))).status());
    }
    private List<String> ids(List<EvidenceSnapshotCatalog.Entry> entries) {
        return entries.stream().map(e -> e.pin().reference().id()).toList();
    }
    private EvidenceSnapshotCatalog tree(String name, boolean reverse) throws Exception {
        var catalog = catalog(name);
        var root = snapshot("root", null); var isolated = snapshot("isolated", null);
        var left = snapshot("left", "root"); var right = snapshot("right", "root");
        var grandchild = snapshot("grandchild", "left");
        for (var r : reverse ? List.of(isolated, root) : List.of(root, isolated)) catalog.seed(r, pin(r));
        for (var child : reverse ? List.of(right, left) : List.of(left, right)) append(catalog, root, child);
        append(catalog, left, grandchild);
        return catalog;
    }
    private Path path(String directory, EvidenceExchange.Snapshot snapshot) throws IOException {
        return temporary.resolve(directory).resolve(EvidenceExchange.sha256(exchange.encodeRecord(snapshot.manifest().reference())) + ".snapshot.json");
    }
    /** Deliberately bypass the write gate only to simulate external corruption in a disposable test directory. */
    private void corruptImport(String directory, EvidenceExchange.Snapshot snapshot) throws IOException {
        Files.write(path(directory, snapshot), exchange.encode(snapshot));
    }
    private Map<String,String> inventory(String directory) throws IOException {
        var root = temporary.resolve(directory); var result = new TreeMap<String,String>();
        result.put("directory-mtime", Files.getLastModifiedTime(root).toString());
        try (var paths = Files.list(root)) {
            for (var p : paths.toList()) result.put(p.getFileName().toString(),
                    EvidenceExchange.sha256(Files.readAllBytes(p)) + ":" + Files.getLastModifiedTime(p));
        }
        return result;
    }
    @Test void rootsParentsChildrenAncestryDescendantsTipsAndStrictAncestorTests() throws Exception {
        var catalog = tree("catalog", false); var before = inventory("catalog");
        var view = EvidenceLineage.load(catalog);
        assertEquals(List.of("isolated", "root"), ids(view.roots()));
        assertEquals(Optional.empty(), view.parent(ref("root")));
        assertEquals(ref("left"), view.parent(ref("grandchild")).orElseThrow().pin().reference());
        assertEquals(List.of("left", "right"), ids(view.children(ref("root"))));
        assertEquals(List.of("left", "root"), ids(view.ancestry(ref("grandchild"))));
        assertEquals(List.of("grandchild", "left", "right"), ids(view.descendants(ref("root"))));
        assertEquals(List.of("grandchild", "isolated", "right"), ids(view.tips()));
        assertTrue(view.isAncestor(ref("root"), ref("grandchild")));
        assertTrue(view.isAncestor(ref("left"), ref("grandchild")));
        assertFalse(view.isAncestor(ref("left"), ref("right")));
        assertFalse(view.isAncestor(ref("grandchild"), ref("root")));
        assertFalse(view.isAncestor(ref("root"), ref("root")));
        assertFalse(view.isAncestor(ref("isolated"), ref("grandchild")));
        assertTrue(view.ancestry(ref("root")).isEmpty());
        assertTrue(view.children(ref("grandchild")).isEmpty());
        assertTrue(view.descendants(ref("isolated")).isEmpty());
        assertEquals(before, inventory("catalog"));
    }
    @Test void resultsAreDeterministicAcrossInsertionOrderAndReopening() throws Exception {
        var first = EvidenceLineage.load(tree("first", false));
        tree("second", true);
        var second = EvidenceLineage.load(new EvidenceSnapshotCatalog(temporary.resolve("second")));
        assertEquals(first.roots(), second.roots()); assertEquals(first.tips(), second.tips());
        for (String id : List.of("root", "left", "right", "grandchild", "isolated")) {
            assertEquals(first.parent(ref(id)), second.parent(ref(id)));
            assertEquals(first.children(ref(id)), second.children(ref(id)));
            assertEquals(first.ancestry(ref(id)), second.ancestry(ref(id)));
            assertEquals(first.descendants(ref(id)), second.descendants(ref(id)));
        }
    }
    @Test void emptyCatalogAndUnknownReferencesRemainDistinct() throws Exception {
        var view = EvidenceLineage.load(catalog("empty"));
        assertTrue(view.roots().isEmpty()); assertTrue(view.tips().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> view.parent(ref("missing")));
        assertThrows(IllegalArgumentException.class, () -> view.children(ref("missing")));
        assertThrows(IllegalArgumentException.class, () -> view.ancestry(ref("missing")));
        assertThrows(IllegalArgumentException.class, () -> view.descendants(ref("missing")));
        assertThrows(IllegalArgumentException.class, () -> view.isAncestor(ref("missing"), ref("missing")));
    }
    @Test void bothAncestorArgumentsMustExistAndHaveSnapshotKind() throws Exception {
        var view = EvidenceLineage.load(tree("catalog", false));
        assertThrows(IllegalArgumentException.class, () -> view.isAncestor(ref("root"), ref("missing")));
        assertThrows(IllegalArgumentException.class, () -> view.isAncestor(ref("missing"), ref("root")));
        assertThrows(IllegalArgumentException.class, () -> view.parent(other(OBSERVATION, "root")));
    }
    @Test void capturedViewsStayImmutableAndRequireExplicitReloadForNewAppends() throws Exception {
        var catalog = tree("catalog", false); var old = EvidenceLineage.load(catalog);
        var root = snapshot("root", null); var later = snapshot("later", "root"); append(catalog, root, later);
        assertEquals(List.of("left", "right"), ids(old.children(ref("root"))));
        assertEquals(List.of("later", "left", "right"), ids(EvidenceLineage.load(catalog).children(ref("root"))));
        assertThrows(UnsupportedOperationException.class, () -> old.roots().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.tips().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.children(ref("root")).clear());
        assertThrows(UnsupportedOperationException.class, () -> old.ancestry(ref("grandchild")).clear());
        assertThrows(UnsupportedOperationException.class, () -> old.descendants(ref("root")).clear());
    }
    @Test void orphanInAnyComponentBlocksTheEntireViewWithoutRepair() throws Exception {
        var catalog = tree("catalog", false);
        corruptImport("catalog", snapshot("orphan", "absent"));
        var before = inventory("catalog");
        var error = assertThrows(IOException.class, () -> EvidenceLineage.load(catalog));
        assertTrue(error.getMessage().startsWith("ORPHAN_SNAPSHOT:"));
        assertEquals(before, inventory("catalog"));
    }
    @Test void cycleInDisconnectedComponentBlocksHealthyRootsToo() throws Exception {
        var catalog = tree("catalog", false);
        corruptImport("catalog", snapshot("cycle-a", "cycle-b"));
        corruptImport("catalog", snapshot("cycle-b", "cycle-c"));
        corruptImport("catalog", snapshot("cycle-c", "cycle-a"));
        var before = inventory("catalog");
        var error = assertThrows(IOException.class, () -> EvidenceLineage.load(catalog));
        assertTrue(error.getMessage().startsWith("CYCLIC_LINEAGE:"));
        assertEquals(before, inventory("catalog"));
    }
    @Test void corruptStoredContentBlocksViewAndIsNeverRegenerated() throws Exception {
        var catalog = tree("catalog", false);
        Files.writeString(path("catalog", snapshot("left", "root")), "{broken");
        var before = inventory("catalog");
        assertThrows(IOException.class, () -> EvidenceLineage.load(catalog));
        assertEquals(before, inventory("catalog"));
    }
    private Assessment missing(String reason) {
        return new Assessment(other(ASSESSMENT, "a"), Optional.empty(), Optional.empty(), other(PROPOSITION, "p"),
                other(CRITERION, "c"), other(POLICY, "policy"), other(CONTEXT, "context"),
                Assessment.Outcome.NOT_MEASURED, List.of(reason), T);
    }
    @Test void validExchangeButInvalidInheritedContentIsRejectedByAdmission() throws Exception {
        var catalog = catalog("catalog");
        var p = snapshot("root", null, new EvidenceHistory().append(missing("original"))); catalog.seed(p, pin(p));
        var changed = snapshot("child", "root", new EvidenceHistory().append(missing("changed")));
        corruptImport("catalog", changed);
        var before = inventory("catalog");
        var error = assertThrows(IOException.class, () -> EvidenceLineage.load(catalog));
        assertTrue(error.getMessage().contains("INVALID_LINEAGE_LINK:"));
        assertTrue(error.getMessage().contains("CHANGED_INHERITED_RECORD"));
        assertEquals(before, inventory("catalog"));
    }
    @Test void backwardChronologyIsRejectedEvenWhenExchangeAndTopologyAreValid() throws Exception {
        var catalog = catalog("catalog");
        var p = exchange.snapshot(ref("root"), other(ACTIVITY, "capture"), T.plusSeconds(1), Optional.empty(), new EvidenceHistory());
        catalog.seed(p, pin(p)); corruptImport("catalog", snapshot("child", "root"));
        assertTrue(assertThrows(IOException.class, () -> EvidenceLineage.load(catalog)).getMessage().contains("CHRONOLOGY_CONFLICT"));
    }
    @Test void siblingDisagreementsAreNeitherMergedNorComparedAcrossBranches() throws Exception {
        var catalog = catalog("catalog"); var root = snapshot("root", null); catalog.seed(root, pin(root));
        var left = snapshot("left", "root", new EvidenceHistory().append(missing("left assessment")));
        var right = snapshot("right", "root", new EvidenceHistory().append(missing("right assessment")));
        append(catalog, root, left); append(catalog, root, right);
        var before = inventory("catalog"); var view = EvidenceLineage.load(catalog);
        assertEquals(List.of("left", "right"), ids(view.tips()));
        assertFalse(view.isAncestor(ref("left"), ref("right")));
        assertFalse(view.isAncestor(ref("right"), ref("left")));
        assertEquals(left, catalog.read(view.tips().getFirst().pin()).orElseThrow());
        assertEquals(right, catalog.read(view.tips().getLast().pin()).orElseThrow());
        assertEquals(before, inventory("catalog"));
    }
    @Test void iterativeTraversalHandlesLongAncestryWithoutRecursion() throws Exception {
        var catalog = catalog("catalog"); var p = snapshot("node-000", null); catalog.seed(p, pin(p));
        for (int i = 1; i <= 128; i++) {
            var next = snapshot(String.format("node-%03d", i), p.manifest().reference().id());
            append(catalog, p, next); p = next;
        }
        var view = EvidenceLineage.load(catalog);
        assertEquals(128, view.ancestry(p.manifest().reference()).size());
        assertEquals(128, view.descendants(ref("node-000")).size());
        assertEquals(List.of("node-128"), ids(view.tips()));
        assertTrue(view.isAncestor(ref("node-000"), ref("node-128")));
    }
}
