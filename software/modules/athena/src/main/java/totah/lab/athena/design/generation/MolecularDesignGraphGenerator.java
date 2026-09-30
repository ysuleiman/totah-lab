package totah.lab.athena.design.generation;

import totah.lab.athena.design.backend.CanonicalIdentityService;
import totah.lab.athena.design.backend.GraphEdit;
import totah.lab.athena.design.backend.GraphEditTransactionEngine;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.MolecularSanitizer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bounded, target-independent generation with in-generation canonical deduplication.
 * The planner supplies grammar-authorized edits and priorities; the chemistry backend
 * validates only after Athena has executed an edit.
 */
public final class MolecularDesignGraphGenerator {
    private final GraphEditTransactionEngine editor;
    private final MolecularSanitizer sanitizer;
    private final CanonicalIdentityService identityService;

    public MolecularDesignGraphGenerator(GraphEditTransactionEngine editor,
                                         MolecularSanitizer sanitizer,
                                         CanonicalIdentityService identityService) {
        this.editor = editor; this.sanitizer = sanitizer; this.identityService = identityService;
    }

    public MolecularDesignTree generate(MolecularGraph root, Configuration configuration,
                                        AuthorizedEditPlanner planner) throws MolecularBackendException {
        var rootIdentity = identityService.identify(root).canonicalKey();
        var mutableNodes = new LinkedHashMap<String, MutableNode>();
        var keyToNode = new LinkedHashMap<String, String>();
        var edges = new ArrayList<MolecularDesignTree.Edge>();
        var rootNode = new MutableNode("node-0000", rootIdentity, root, 0);
        mutableNodes.put(rootNode.id, rootNode); keyToNode.put(rootIdentity, rootNode.id);

        var sequence = new long[]{0};
        Queue queue = configuration.strategy() == GenerationStrategy.ENUMERATIVE
                ? new FifoQueue() : new PriorityEditQueue();
        enqueue(queue, rootNode, planner, sequence);
        int attempted = 0;
        while (!queue.isEmpty() && mutableNodes.size() < configuration.maximumNodes()
                && attempted < configuration.maximumAttemptedEdits()) {
            Planned planned = queue.remove(); attempted++;
            var parent = mutableNodes.get(planned.parentNodeId());
            if (parent.depth >= configuration.maximumDepth()) continue;
            GraphEditTransactionEngine.Result edited;
            try {
                edited = editor.apply(parent.graph, planned.edit().edit(), planned.edit().authorization());
            } catch (IllegalArgumentException | IllegalStateException rejected) {
                continue;
            }
            if (!configuration.disconnectedProductsAllowed() && !connected(edited.product())) continue;
            MolecularSanitizer.Result sanitized;
            try {
                sanitized = sanitizer.sanitize(edited.product(), configuration.sanitizationPolicy());
            } catch (MolecularBackendException rejected) {
                continue;
            }
            if (!sanitized.valid()) continue;
            String key = identityService.identify(sanitized.graph()).canonicalKey();
            String existing = keyToNode.get(key);
            if (existing != null) {
                edges.add(new MolecularDesignTree.Edge(parent.id, existing, edited.receipt()));
                continue; // critically, do not re-expand an equivalent graph
            }
            String childId = "node-" + String.format("%04d", mutableNodes.size());
            var child = new MutableNode(childId, key, sanitized.graph(), parent.depth + 1);
            mutableNodes.put(childId, child); keyToNode.put(key, childId);
            edges.add(new MolecularDesignTree.Edge(parent.id, childId, edited.receipt()));
            enqueue(queue, child, planner, sequence);
        }
        var nodes = mutableNodes.values().stream().map(node ->
                new MolecularDesignTree.Node(node.id, node.key, node.graph, node.depth, node.expanded)).toList();
        return new MolecularDesignTree(rootNode.id, nodes, edges, keyToNode);
    }

    private static void enqueue(Queue queue, MutableNode node, AuthorizedEditPlanner planner, long[] sequence) {
        var edits = planner.editsFor(node.graph, node.depth).stream()
                .sorted(Comparator.comparingInt(AuthorizedEdit::priority)
                        .thenComparing(edit -> edit.edit().editId())).toList();
        edits.forEach(edit -> queue.add(new Planned(node.id, edit, sequence[0]++)));
        node.expanded = !edits.isEmpty();
    }

    private static boolean connected(MolecularGraph graph) {
        if (graph.atoms().isEmpty()) return false;
        var adjacent = new LinkedHashMap<String, List<String>>();
        graph.atoms().forEach(atom -> adjacent.put(atom.id(), new ArrayList<>()));
        graph.bonds().forEach(bond -> {
            adjacent.get(bond.firstAtomId()).add(bond.secondAtomId());
            adjacent.get(bond.secondAtomId()).add(bond.firstAtomId());
        });
        var seen = new java.util.HashSet<String>(); var pending = new ArrayDeque<String>();
        pending.add(graph.atoms().getFirst().id());
        while (!pending.isEmpty()) { String id = pending.removeFirst(); if (seen.add(id)) pending.addAll(adjacent.get(id)); }
        return seen.size() == graph.atoms().size();
    }

    public record Configuration(GenerationStrategy strategy, int maximumNodes, int maximumDepth,
                                int maximumAttemptedEdits, boolean disconnectedProductsAllowed,
                                MolecularSanitizer.SanitizationPolicy sanitizationPolicy) {
        public Configuration {
            if (maximumNodes < 1 || maximumDepth < 0 || maximumAttemptedEdits < 0) {
                throw new IllegalArgumentException("invalid generation bounds");
            }
        }
    }
    public record AuthorizedEdit(GraphEdit edit, GraphEditTransactionEngine.Authorization authorization,
                                 int priority) { }
    @FunctionalInterface public interface AuthorizedEditPlanner {
        List<AuthorizedEdit> editsFor(MolecularGraph parent, int depth);
    }
    private static final class MutableNode {
        private final String id; private final String key; private final MolecularGraph graph; private final int depth;
        private boolean expanded;
        private MutableNode(String id, String key, MolecularGraph graph, int depth) {
            this.id = id; this.key = key; this.graph = graph; this.depth = depth;
        }
    }
    private record Planned(String parentNodeId, AuthorizedEdit edit, long sequence) { }
    private interface Queue { void add(Planned value); Planned remove(); boolean isEmpty(); }
    private static final class FifoQueue implements Queue {
        private final ArrayDeque<Planned> values = new ArrayDeque<>();
        public void add(Planned value) { values.addLast(value); }
        public Planned remove() { return values.removeFirst(); }
        public boolean isEmpty() { return values.isEmpty(); }
    }
    private static final class PriorityEditQueue implements Queue {
        private final PriorityQueue<Planned> values = new PriorityQueue<>(Comparator
                .comparingInt((Planned value) -> value.edit().priority()).thenComparingLong(Planned::sequence));
        public void add(Planned value) { values.add(value); }
        public Planned remove() { return values.remove(); }
        public boolean isEmpty() { return values.isEmpty(); }
    }
}
