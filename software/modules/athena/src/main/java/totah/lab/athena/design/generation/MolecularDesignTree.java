package totah.lab.athena.design.generation;

import totah.lab.athena.design.backend.*;

import java.io.IOException;
import java.util.*;

/** Chemical representatives plus distinct, replayable design derivations and execution outcomes. */
public record MolecularDesignTree(String rootNodeId, List<Node> nodes, List<Edge> edges,
                                  Map<String, String> canonicalKeyToNodeId,
                                  List<Attempt> attempts, List<DesignState> states,
                                  Termination termination) {
    public MolecularDesignTree {
        nodes = List.copyOf(nodes); edges = List.copyOf(edges);
        canonicalKeyToNodeId = Map.copyOf(canonicalKeyToNodeId);
        attempts = List.copyOf(attempts); states = List.copyOf(states);
        Objects.requireNonNull(termination);
    }
    public MolecularDesignTree(String rootNodeId, List<Node> nodes, List<Edge> edges,
                               Map<String,String> canonicalKeyToNodeId) {
        this(rootNodeId, nodes, edges, canonicalKeyToNodeId, List.of(), List.of(),
                new Termination(TerminationReason.LEGACY_UNRECORDED, 0, 0, "historical result"));
    }
    public record Node(String nodeId, String canonicalKey, MolecularGraph graph, int depth,
                       boolean expanded) { }
    public record Edge(String parentNodeId, String childNodeId, GraphEditReceipt editReceipt,
                       TopologyEditReceipt topologyReceipt, String attemptId) {
        public Edge(String parentNodeId, String childNodeId, GraphEditReceipt editReceipt) {
            this(parentNodeId, childNodeId, editReceipt, null, "legacy");
        }
    }
    public record Reference(String id, String version) {
        public Reference { required(id); required(version); }
    }
    /** Atom/bond identity retention; permitted additions to the neighborhood must be explicit. */
    public record RetainedAnchor(String id, Set<String> atomIds, Set<String> bondIds,
                                  boolean allowNewIncidentBonds) {
        public RetainedAnchor { required(id); atomIds = Set.copyOf(atomIds); bondIds = Set.copyOf(bondIds); }
    }
    public record Provenance(Reference hypothesis, List<Reference> evidence,
                             List<RetainedAnchor> retainedAnchors, Reference rule,
                             List<Reference> resources, String intendedEffect,
                             List<Reference> geometryConstraints, boolean legacyUnspecified) {
        public Provenance {
            Objects.requireNonNull(hypothesis); Objects.requireNonNull(rule); required(intendedEffect);
            evidence = List.copyOf(evidence); retainedAnchors = List.copyOf(retainedAnchors);
            resources = List.copyOf(resources); geometryConstraints = List.copyOf(geometryConstraints);
        }
        public static Provenance legacy() {
            var unknown = new Reference("UNSPECIFIED", "UNSPECIFIED");
            return new Provenance(unknown, List.of(), List.of(), unknown, List.of(),
                    "UNSPECIFIED", List.of(), true);
        }
    }
    /** The graph retains this derivation's IDs and coordinates, not the representative's. */
    public record DesignState(String stateId, String representativeNodeId, String parentStateId,
                              MolecularGraph graph, Provenance provenance, int depth) { }
    public enum Outcome {
        ACCEPTED, DEDUPLICATED, AUTHORIZATION_REJECTED, INVALID_CHEMISTRY,
        INVALID_TOPOLOGY, BACKEND_VALIDATION_FAILURE, GEOMETRY_FAILURE,
        LINEAGE_MAPPING_FAILURE, SEARCH_BUDGET_TERMINATION, UNEXPECTED_EXECUTION_FAILURE
    }
    public record Attempt(String attemptId, String parentStateId, String resultingStateId,
                          MolecularDesignGraphGenerator.AuthorizedEdit operation,
                          Outcome outcome, String reason, MolecularGraph parent,
                          MolecularGraph attemptedProduct, MolecularGraph resultingProduct,
                          GraphEditReceipt graphReceipt, TopologyEditReceipt topologyReceipt,
                          MolecularGraph.Delta finalDelta, MolecularGraph.Delta validationDelta,
                          CanonicalIdentityService.Correspondence representativeMapping,
                          Map<String,String> parentToRepresentativeAtoms,
                          Map<String,String> parentToRepresentativeBonds,
                          List<BackendEvidence> backendEvidence, List<String> geometryEvidence, String canonicalKey, DesignState parentDesignState) {
        public Attempt {
            required(attemptId); Objects.requireNonNull(outcome); required(reason);
            Objects.requireNonNull(parentDesignState);
            if (!Objects.equals(parentStateId, parentDesignState.stateId()) || !parent.equals(parentDesignState.graph()))
                throw new IllegalArgumentException("attempt parent design state mismatch");
            parentToRepresentativeAtoms = Map.copyOf(parentToRepresentativeAtoms);
            parentToRepresentativeBonds = Map.copyOf(parentToRepresentativeBonds);
            backendEvidence = List.copyOf(backendEvidence); geometryEvidence = List.copyOf(geometryEvidence);
        }
    }
    public enum TerminationReason { EXHAUSTED, MAXIMUM_NODES, MAXIMUM_ATTEMPTS, MAXIMUM_DEPTH,
        ROOT_VALIDATION_FAILURE, PLANNER_FAILURE, LEGACY_UNRECORDED }
    public record Termination(TerminationReason reason, int attemptedOperations, int unexecutedOperations,
                              String detail) { }
    /**
     * Synchronous journal boundary. Production implementations must durably commit each callback
     * before returning; errors abort execution. planned() permits recovery of interrupted attempts.
     * IDs are run-local: the host supplies the journal/run namespace. No storage engine or workflow
     * is owned here. Legacy generate() only returns in-memory receipts.
     */
    public interface OutcomeSink {
        default void started(MolecularGraph root, Provenance provenance) throws IOException { }
        default void state(DesignState state) throws IOException { }
        default void planned(String attemptId, DesignState parent,
                             MolecularDesignGraphGenerator.AuthorizedEdit operation) throws IOException { }
        void record(Attempt attempt) throws IOException;
        default void terminated(Termination termination) throws IOException { }
    }
    /**
     * Versioned JSON-lines journal for the existing receipt contract. CREATE_NEW prevents accidental
     * overwrite. Each callback is forced to storage before returning. A trailing partial line or
     * planned event without a receipt identifies an interrupted run; no automatic replay is implied.
     * Use one file per run, with an already existing parent directory, and try-with-resources.
     */
    public static final class Journal implements OutcomeSink, AutoCloseable {
        public static final String SCHEMA = "athena-design-journal/1";
        private final java.nio.channels.FileChannel channel;
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        private long sequence;

        public Journal(java.nio.file.Path path) throws IOException {
            channel = java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE);
        }
        @Override public void started(MolecularGraph root, Provenance provenance) throws IOException {
            write("started", Map.of("root", root, "provenance", provenance));
        }
        @Override public void state(DesignState state) throws IOException { write("state", state); }
        @Override public void planned(String id, DesignState parent,
                                      MolecularDesignGraphGenerator.AuthorizedEdit operation) throws IOException {
            write("planned", Map.of("attemptId", id, "parent", parent, "operation", operation));
        }
        @Override public void record(Attempt attempt) throws IOException { write("receipt", attempt); }
        @Override public void terminated(Termination termination) throws IOException { write("terminated", termination); }
        private synchronized void write(String event, Object data) throws IOException {
            var entry = mapper.createObjectNode();
            entry.put("schema", SCHEMA); entry.put("sequence", sequence); entry.put("event", event);
            entry.set("data", mapper.valueToTree(data));
            byte[] json = mapper.writeValueAsBytes(entry);
            var buffer = java.nio.ByteBuffer.allocate(json.length + 1).put(json).put((byte) '\n');
            buffer.flip();
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
            sequence++;
        }
        @Override public synchronized void close() throws IOException { channel.close(); }
    }

    private static void required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("nonblank provenance value required");
    }
}
