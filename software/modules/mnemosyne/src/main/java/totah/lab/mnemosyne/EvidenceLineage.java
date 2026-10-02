package totah.lab.mnemosyne;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable validated catalog view. Query order expresses identity, never branch preference. */
public final class EvidenceLineage {
    public static final String METHOD = "mnemosyne-lineage/1";
    private static final Comparator<EvidenceSnapshotCatalog.Entry> ORDER = Comparator
            .comparing((EvidenceSnapshotCatalog.Entry e) -> e.pin().reference().namespace())
            .thenComparing(e -> e.pin().reference().id()).thenComparing(e -> e.pin().reference().version());
    private final Map<ScientificReference, EvidenceSnapshotCatalog.Entry> entries;
    private final Map<ScientificReference, List<EvidenceSnapshotCatalog.Entry>> children;
    private final List<EvidenceSnapshotCatalog.Entry> roots;
    private final List<EvidenceSnapshotCatalog.Entry> tips;

    private EvidenceLineage(Map<ScientificReference, EvidenceSnapshotCatalog.Entry> entries) {
        this.entries = Map.copyOf(entries);
        var childLists = new HashMap<ScientificReference, List<EvidenceSnapshotCatalog.Entry>>();
        entries.keySet().forEach(r -> childLists.put(r, new ArrayList<>()));
        entries.values().forEach(e -> e.parent().ifPresent(p -> childLists.get(p).add(e)));
        var immutable = new HashMap<ScientificReference, List<EvidenceSnapshotCatalog.Entry>>();
        childLists.forEach((r, list) -> immutable.put(r, list.stream().sorted(ORDER).toList()));
        children = Map.copyOf(immutable);
        roots = entries.values().stream().filter(e -> e.parent().isEmpty()).sorted(ORDER).toList();
        tips = entries.values().stream().filter(e -> children.get(e.pin().reference()).isEmpty()).sorted(ORDER).toList();
    }

    /**
     * Fail closed before publishing any view. Reload explicitly to include later appends.
     * This detects changes observed during loading; it is not a filesystem transaction.
     */
    public static EvidenceLineage load(EvidenceSnapshotCatalog catalog) throws IOException {
        Objects.requireNonNull(catalog);
        var inventory = catalog.entries();
        var entries = new LinkedHashMap<ScientificReference, EvidenceSnapshotCatalog.Entry>();
        for (var entry : inventory) {
            if (entries.putIfAbsent(entry.pin().reference(), entry) != null)
                throw new IOException("DUPLICATE_SNAPSHOT_IDENTITY: " + entry.pin().reference());
        }
        for (var entry : inventory) {
            if (entry.parent().isPresent() && !entries.containsKey(entry.parent().orElseThrow()))
                throw new IOException("ORPHAN_SNAPSHOT: " + entry.pin().reference());
        }
        // Iterative walk avoids recursion limits and validates disconnected components too.
        var complete = new HashSet<ScientificReference>();
        for (var entry : inventory) {
            var path = new HashSet<ScientificReference>();
            var current = Optional.of(entry.pin().reference());
            while (current.isPresent() && !complete.contains(current.orElseThrow())) {
                var ref = current.orElseThrow();
                if (!path.add(ref)) throw new IOException("CYCLIC_LINEAGE: " + ref);
                current = entries.get(ref).parent();
            }
            complete.addAll(path);
        }
        var admission = new EvidenceAdmission();
        for (var entry : inventory) {
            var snapshot = catalog.read(entry.pin()).orElseThrow(() -> new IOException("SNAPSHOT_DISAPPEARED: " + entry.pin().reference()));
            if (!snapshot.manifest().parent().equals(entry.parent())) throw new IOException("CATALOG_CHANGED_DURING_LOAD");
            var parentEntry = entry.parent().map(entries::get).orElse(entry);
            var parent = entry.parent().isEmpty() ? snapshot : catalog.read(parentEntry.pin())
                    .orElseThrow(() -> new IOException("PARENT_DISAPPEARED: " + parentEntry.pin().reference()));
            var decision = admission.check(parent, snapshot, new EvidenceAdmission.Expectation(parentEntry.pin(), entry.pin()));
            if (decision.status() == EvidenceAdmission.Status.CONFLICT)
                throw new IOException("INVALID_LINEAGE_LINK: " + entry.pin().reference() + " " + decision.findings());
        }
        if (!inventory.equals(catalog.entries())) throw new IOException("CATALOG_CHANGED_DURING_LOAD");
        return new EvidenceLineage(entries);
    }
    public List<EvidenceSnapshotCatalog.Entry> roots() { return roots; }
    /** All leaves, including isolated roots. No preferred or current head exists. */
    public List<EvidenceSnapshotCatalog.Entry> tips() { return tips; }
    public Optional<EvidenceSnapshotCatalog.Entry> parent(ScientificReference reference) {
        return require(reference).parent().map(entries::get);
    }
    public List<EvidenceSnapshotCatalog.Entry> children(ScientificReference reference) {
        require(reference);
        return children.get(reference);
    }
    /** Strict ancestors, nearest parent first and root last; excludes the queried snapshot. */
    public List<EvidenceSnapshotCatalog.Entry> ancestry(ScientificReference reference) {
        var ancestors = new ArrayList<EvidenceSnapshotCatalog.Entry>();
        var next = parent(reference);
        while (next.isPresent()) {
            var entry = next.orElseThrow(); ancestors.add(entry);
            next = entry.parent().map(entries::get);
        }
        return List.copyOf(ancestors);
    }
    /** Strict descendants sorted by reference; excludes the queried snapshot. */
    public List<EvidenceSnapshotCatalog.Entry> descendants(ScientificReference reference) {
        require(reference);
        var descendants = new ArrayList<EvidenceSnapshotCatalog.Entry>();
        var queue = new ArrayDeque<>(children.get(reference));
        while (!queue.isEmpty()) {
            var entry = queue.removeFirst(); descendants.add(entry);
            queue.addAll(children.get(entry.pin().reference()));
        }
        return descendants.stream().sorted(ORDER).toList();
    }
    /** Strict ancestry: self is false; unknown references are errors, not negative evidence. */
    public boolean isAncestor(ScientificReference ancestor, ScientificReference descendant) {
        require(ancestor); require(descendant);
        return ancestry(descendant).stream().anyMatch(e -> e.pin().reference().equals(ancestor));
    }
    private EvidenceSnapshotCatalog.Entry require(ScientificReference reference) {
        Objects.requireNonNull(reference).require(ScientificReference.Kind.SNAPSHOT);
        var entry = entries.get(reference);
        if (entry == null) throw new IllegalArgumentException("unknown snapshot in lineage view: " + reference);
        return entry;
    }
}
