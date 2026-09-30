package totah.lab.athena.design.generation;

import totah.lab.athena.design.backend.*;
import static totah.lab.athena.design.generation.MolecularDesignTree.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** One bounded execution path for graph and topology edits, with explicit attempt outcomes. */
public final class MolecularDesignGraphGenerator {
    private final GraphEditTransactionEngine editor;
    private final TopologyEditTransactionEngine topologyEditor;
    private final MolecularSanitizer sanitizer;
    private final CanonicalIdentityService identityService;

    public MolecularDesignGraphGenerator(GraphEditTransactionEngine editor, MolecularSanitizer sanitizer,
                                         CanonicalIdentityService identityService) {
        this.editor = Objects.requireNonNull(editor); this.sanitizer = Objects.requireNonNull(sanitizer);
        this.identityService = Objects.requireNonNull(identityService);
        this.topologyEditor = new TopologyEditTransactionEngine(identityService);
    }

    /** Compatibility entry point. Unspecified scientific context remains explicitly marked. */
    public MolecularDesignTree generate(MolecularGraph root, Configuration configuration,
                                        AuthorizedEditPlanner planner) throws MolecularBackendException {
        try {
            return generateTraced(root, Provenance.legacy(), configuration,
                    state -> planner.editsFor(state.graph(), state.depth()), attempt -> { },
                    (state, operation, product) -> new GeometryResult(operation.provenance().geometryConstraints().isEmpty(),
                            List.of(), Set.of()));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    public MolecularDesignTree generateTraced(MolecularGraph root, Provenance rootProvenance,
            Configuration configuration, StateAwarePlanner planner, OutcomeSink sink,
            GeometryValidator geometry) throws IOException {
        Objects.requireNonNull(root); Objects.requireNonNull(rootProvenance); Objects.requireNonNull(configuration);
        Objects.requireNonNull(planner); Objects.requireNonNull(sink); Objects.requireNonNull(geometry);
        var nodes = new ArrayList<Node>(); var edges = new ArrayList<Edge>();
        var attempts = new ArrayList<Attempt>(); var states = new ArrayList<DesignState>();
        var identities = new LinkedHashMap<String,String>();
        var queue = new PriorityQueue<Planned>(Comparator
                .comparingInt((Planned p) -> configuration.strategy() == GenerationStrategy.ENUMERATIVE ? 0 : p.edit.priority())
                .thenComparingLong(Planned::sequence));
        sink.started(root, rootProvenance);
        String rootKey;
        Outcome rootStage = Outcome.INVALID_TOPOLOGY;
        var rootEvidence = new ArrayList<BackendEvidence>();
        try {
            root.validateTopology(!configuration.disconnectedProductsAllowed());
            rootStage = Outcome.BACKEND_VALIDATION_FAILURE;
            var checked = sanitizer.sanitize(root, configuration.sanitizationPolicy()); addEvidence(rootEvidence, checked.evidence());
            rootStage = Outcome.INVALID_CHEMISTRY;
            if (!checked.valid() || !root.equals(checked.graph()))
                throw new IllegalArgumentException("root must already be a valid, unmodified molecular state");
            rootStage = Outcome.BACKEND_VALIDATION_FAILURE;
            var identity = identityService.identify(root); addEvidence(rootEvidence, identity.evidence()); rootKey = identity.canonicalKey();
            if (rootKey == null || rootKey.isBlank()) throw new MolecularBackendException("backend returned empty root identity");
        } catch (MolecularBackendException | RuntimeException failure) {
            var attempt = new Attempt("root-validation", "state-0000", null, null,
                    failure instanceof MolecularBackendException ? Outcome.BACKEND_VALIDATION_FAILURE
                            : failure instanceof IllegalArgumentException ? rootStage : Outcome.UNEXPECTED_EXECUTION_FAILURE,
                    message(failure), root, null, null, null, null, null, null, null,
                    Map.of(), Map.of(), rootEvidence, List.of(), null,
                    new DesignState("state-0000", null, null, root, rootProvenance, 0));
            sink.record(attempt); attempts.add(attempt);
            var termination = new Termination(TerminationReason.ROOT_VALIDATION_FAILURE, 0, 0, message(failure));
            sink.terminated(termination);
            return new MolecularDesignTree("node-0000", nodes, edges, identities, attempts,
                    List.of(new DesignState("state-0000", null, null, root, rootProvenance, 0)), termination);
        }
        nodes.add(new Node("node-0000", rootKey, root, 0, false)); identities.put(rootKey, "node-0000");
        var initial = new DesignState("state-0000", "node-0000", null, root, rootProvenance, 0); states.add(initial); sink.state(initial);
        long[] sequence = {0}; int executed = 0, blocked = 0; boolean depthLimited = false, plannerFailed = false;
        var expanded = new HashSet<String>();
        plannerFailed = !enqueue(queue, initial, planner, sequence, attempts, sink);
        if (!plannerFailed) expanded.add(initial.representativeNodeId());
        while (!queue.isEmpty()) {
            Planned planned = queue.remove(); var parent = planned.parent; var operation = planned.edit;
            TerminationReason limit = nodes.size() >= configuration.maximumNodes() ? TerminationReason.MAXIMUM_NODES
                    : executed >= configuration.maximumAttemptedEdits() ? TerminationReason.MAXIMUM_ATTEMPTS
                    : parent.depth() >= configuration.maximumDepth() ? TerminationReason.MAXIMUM_DEPTH : null;
            sink.planned("attempt-" + planned.sequence, parent, operation);
            Attempt attempt;
            if (limit != null) {
                blocked++; depthLimited |= limit == TerminationReason.MAXIMUM_DEPTH;
                attempt = failure("attempt-" + planned.sequence, parent, operation,
                        Outcome.SEARCH_BUDGET_TERMINATION, limit.name());
            } else {
                executed++;
                attempt = execute("attempt-" + planned.sequence, parent, operation, configuration, geometry,
                        nodes, identities, "state-" + String.format("%04d", states.size()));
            }
            // Persistence is outside the execution catch: never hide an unsuccessful receipt write.
            sink.record(attempt); attempts.add(attempt);
            if (attempt.outcome() == Outcome.ACCEPTED || attempt.outcome() == Outcome.DEDUPLICATED) {
                String key = attempt.canonicalKey();
                String representative = identities.get(key);
                if (representative == null) {
                    representative = "node-" + String.format("%04d", nodes.size());
                    identities.put(key, representative);
                    nodes.add(new Node(representative, key, attempt.resultingProduct(), parent.depth() + 1, false));
                }
                var child = new DesignState(attempt.resultingStateId(), representative, parent.stateId(),
                        attempt.resultingProduct(), operation.provenance(), parent.depth() + 1);
                states.add(child); sink.state(child);
                edges.add(new Edge(parent.representativeNodeId(), representative, attempt.graphReceipt(),
                        attempt.topologyReceipt(), attempt.attemptId()));
                // Legacy identity-only expansion remains compatible. Scientific requests expand each
                // derivation in its own stable-ID frame, bounded by the same depth/attempt/node budgets.
                if (attempt.outcome() != Outcome.DEDUPLICATED || !operation.provenance().legacyUnspecified()) {
                    boolean success = enqueue(queue, child, planner, sequence, attempts, sink);
                    plannerFailed |= !success;
                    if (success) expanded.add(child.representativeNodeId());
                }
            }
        }
        var reason = plannerFailed ? TerminationReason.PLANNER_FAILURE
                : blocked > 0 && nodes.size() >= configuration.maximumNodes() ? TerminationReason.MAXIMUM_NODES
                : blocked > 0 && executed >= configuration.maximumAttemptedEdits() ? TerminationReason.MAXIMUM_ATTEMPTS
                : depthLimited ? TerminationReason.MAXIMUM_DEPTH : TerminationReason.EXHAUSTED;
        var termination = new Termination(reason, executed, blocked, "Known queued operations are receipted; descendants of unexecuted operations were not enumerated.");
        sink.terminated(termination);
        var finalNodes = nodes.stream().map(n -> new Node(n.nodeId(), n.canonicalKey(), n.graph(), n.depth(), expanded.contains(n.nodeId()))).toList();
        return new MolecularDesignTree("node-0000", finalNodes, edges, identities, attempts, states, termination);
    }

    private Attempt execute(String id, DesignState parent, AuthorizedEdit op, Configuration config,
                            GeometryValidator geometry, List<Node> nodes, Map<String,String> keys, String stateId) {
        MolecularGraph product = null, result = null; GraphEditReceipt graphReceipt = null;
        TopologyEditReceipt topologyReceipt = null; var evidence = new ArrayList<BackendEvidence>();
        List<String> geometryEvidence = List.of(); MolecularGraph.Delta validationDelta = null;
        Outcome stage = Outcome.AUTHORIZATION_REJECTED;
        try {
            checkAnchors(parent.graph(), parent.graph(), op.provenance(), false);
            if (op.topologyEdit() != null && op.topologyEdit().stereoDisposition() == TopologyEdit.StereoDisposition.ENUMERATION_REQUIRED)
                throw new GraphEditTransactionEngine.Rejected(GraphEditTransactionEngine.RejectionKind.CHEMISTRY,
                        "stereo enumeration is not supported in bounded Phase 1 execution");
            if (op.edit() != null) {
                var edited = editor.apply(parent.graph(), op.edit(), op.authorization());
                product = edited.product(); graphReceipt = edited.receipt();
            } else {
                var edited = topologyEditor.apply(parent.graph(), op.topologyEdit(), op.topologyAuthorization());
                product = edited.product(); topologyReceipt = edited.receipt();
            }
            checkAnchors(parent.graph(), product, op.provenance(), true);
            stage = Outcome.INVALID_TOPOLOGY;
            product.validateTopology(!config.disconnectedProductsAllowed());
            stage = Outcome.BACKEND_VALIDATION_FAILURE;
            var sanitized = sanitizer.sanitize(product, config.sanitizationPolicy()); addEvidence(evidence, sanitized.evidence());
            result = sanitized.graph(); validationDelta = MolecularGraph.Delta.between(product, result);
            stage = Outcome.INVALID_CHEMISTRY;
            if (!sanitized.valid()) throw new IllegalArgumentException("backend rejected chemical state");
            // Phase 1 accepts validation, not an implicit second chemistry operation. Coordinate
            // changes are distinct and may proceed to the requested geometry evaluation.
            if (validationDelta.chemicalGraphChanged()
                    || !product.atoms().stream().map(MolecularGraph.Atom::id).toList()
                            .equals(result.atoms().stream().map(MolecularGraph.Atom::id).toList())
                    || !product.bonds().stream().map(MolecularGraph.Bond::id).toList()
                            .equals(result.bonds().stream().map(MolecularGraph.Bond::id).toList()))
                throw new IllegalArgumentException("backend changed chemical state or stable ordering without authorization");
            stage = Outcome.AUTHORIZATION_REJECTED;
            checkAnchors(parent.graph(), result, op.provenance(), true);
            stage = Outcome.INVALID_TOPOLOGY;
            result.validateTopology(!config.disconnectedProductsAllowed());
            stage = Outcome.GEOMETRY_FAILURE;
            var measured = geometry.evaluate(parent, op, result); geometryEvidence = measured.evidence();
            if (!measured.valid() || !measured.evaluatedConstraints().containsAll(op.provenance().geometryConstraints()))
                throw new IllegalArgumentException("geometry rejected or requested constraints not evaluated");
            stage = Outcome.BACKEND_VALIDATION_FAILURE;
            var identity = identityService.identify(result); addEvidence(evidence, identity.evidence());
            if (identity.canonicalKey() == null || identity.canonicalKey().isBlank())
                throw new IllegalArgumentException("backend returned empty canonical identity");
            String existing = keys.get(identity.canonicalKey());
            var atoms = graphReceipt != null ? graphReceipt.parentToProductAtomIds() : topologyReceipt.parentToChildAtomLineage();
            var bonds = graphReceipt != null ? graphReceipt.parentToProductBondIds() : topologyReceipt.parentToChildBondLineage();
            CanonicalIdentityService.Correspondence mapping = null;
            Map<String,String> composedAtoms = atoms, composedBonds = bonds;
            if (existing != null) {
                stage = Outcome.LINEAGE_MAPPING_FAILURE;
                var representative = nodes.stream().filter(n -> n.nodeId().equals(existing)).findFirst().orElseThrow();
                mapping = identityService.correspondence(result, representative.graph());
                if (mapping.alternatives().isEmpty()) throw new IllegalArgumentException("canonical key matched but no verified isomorphism; exhaustive=" + mapping.exhaustive());
                composedAtoms = mapping.selected().composeAtoms(atoms); composedBonds = mapping.selected().composeBonds(bonds);
            }
            return new Attempt(id, parent.stateId(), stateId, op,
                    existing == null ? Outcome.ACCEPTED : Outcome.DEDUPLICATED,
                    existing == null ? "validated product" : "verified representative mapping; symmetry alternatives retained",
                    parent.graph(), product, result, graphReceipt, topologyReceipt,
                    MolecularGraph.Delta.between(parent.graph(), result), validationDelta, mapping,
                    composedAtoms, composedBonds, evidence, geometryEvidence, identity.canonicalKey(), parent);
        } catch (GraphEditTransactionEngine.Rejected exception) {
            stage = switch (exception.kind()) {
                case AUTHORIZATION -> Outcome.AUTHORIZATION_REJECTED;
                case TOPOLOGY -> Outcome.INVALID_TOPOLOGY;
                case CHEMISTRY -> Outcome.INVALID_CHEMISTRY;
            };
            return rejected(id, parent, op, stage, exception, product, result, graphReceipt, topologyReceipt, validationDelta, evidence, geometryEvidence);
        } catch (MolecularBackendException exception) {
            return rejected(id, parent, op, stage == Outcome.LINEAGE_MAPPING_FAILURE ? stage : Outcome.BACKEND_VALIDATION_FAILURE, exception, product, result, graphReceipt, topologyReceipt, validationDelta, evidence, geometryEvidence);
        } catch (IllegalArgumentException exception) {
            return rejected(id, parent, op, stage, exception, product, result, graphReceipt, topologyReceipt, validationDelta, evidence, geometryEvidence);
        } catch (RuntimeException exception) {
            return rejected(id, parent, op, Outcome.UNEXPECTED_EXECUTION_FAILURE, exception, product, result, graphReceipt, topologyReceipt, validationDelta, evidence, geometryEvidence);
        }
    }
    private static Attempt rejected(String id, DesignState parent, AuthorizedEdit op, Outcome outcome, Exception error,
            MolecularGraph product, MolecularGraph result, GraphEditReceipt gr, TopologyEditReceipt tr,
            MolecularGraph.Delta validationDelta, List<BackendEvidence> evidence, List<String> geometry) {
        return new Attempt(id, parent.stateId(), null, op, outcome, message(error), parent.graph(), product, result,
                gr, tr, result == null ? null : MolecularGraph.Delta.between(parent.graph(), result), validationDelta,
                null, Map.of(), Map.of(), evidence, geometry, null, parent);
    }
    private static boolean enqueue(PriorityQueue<Planned> queue, DesignState state, StateAwarePlanner planner,
                                   long[] sequence, List<Attempt> attempts, OutcomeSink sink) throws IOException {
        List<AuthorizedEdit> edits;
        try {
            edits = planner.editsFor(state).stream().sorted(Comparator.comparingInt(AuthorizedEdit::priority)
                    .thenComparing(AuthorizedEdit::operationId)).toList();
            // The same rule/edit ID may be authorized by distinct hypotheses. Attempt IDs,
            // rather than operation IDs, identify executions.
        } catch (RuntimeException error) {
            var attempt = failure("planner-" + state.stateId(), state, null, Outcome.UNEXPECTED_EXECUTION_FAILURE, message(error));
            sink.record(attempt); attempts.add(attempt); return false;
        }
        for (var edit : edits) queue.add(new Planned(state, edit, sequence[0]++));
        return true;
    }
    private static Attempt failure(String id, DesignState state, AuthorizedEdit op, Outcome outcome, String reason) {
        return new Attempt(id, state.stateId(), null, op, outcome, reason, state.graph(), null, null,
                null, null, null, null, null, Map.of(), Map.of(), List.of(), List.of(), null, state);
    }
    private static void checkAnchors(MolecularGraph before, MolecularGraph after, Provenance context, boolean compare) {
        for (var anchor : context.retainedAnchors()) {
            for (String id : anchor.atomIds()) {
                var original = before.atom(id).orElseThrow(() -> new IllegalArgumentException("retained anchor atom missing: " + id));
                if (compare && !after.atom(id).map(a -> chemicalAtom(a).equals(chemicalAtom(original))).orElse(false))
                    throw new GraphEditTransactionEngine.Rejected(GraphEditTransactionEngine.RejectionKind.AUTHORIZATION, "retained anchor atom changed: " + id);
                if (compare) {
                    if (!(anchor.allowNewIncidentBonds() ? incident(after, id).containsAll(incident(before, id))
                            : incident(before, id).equals(incident(after, id)))) throw new GraphEditTransactionEngine.Rejected(GraphEditTransactionEngine.RejectionKind.AUTHORIZATION, "retained anchor neighborhood changed: " + id);
                }
            }
            for (String id : anchor.bondIds()) {
                var original = before.bond(id).orElseThrow(() -> new IllegalArgumentException("retained anchor bond missing: " + id));
                if (compare && !after.bond(id).equals(Optional.of(original))) throw new GraphEditTransactionEngine.Rejected(GraphEditTransactionEngine.RejectionKind.AUTHORIZATION, "retained anchor bond changed: " + id);
            }
        }
    }
    private static MolecularGraph.Atom chemicalAtom(MolecularGraph.Atom a) {
        return new MolecularGraph.Atom(a.id(), a.element(), a.isotope(), a.formalCharge(), a.explicitHydrogens(), a.aromatic(), a.stereochemistry(), null, a.properties());
    }
    private static Set<MolecularGraph.Bond> incident(MolecularGraph g, String id) {
        return g.bonds().stream().filter(b -> b.firstAtomId().equals(id) || b.secondAtomId().equals(id)).collect(java.util.stream.Collectors.toSet());
    }
    private static void addEvidence(List<BackendEvidence> evidence, BackendEvidence entry) throws MolecularBackendException {
        if (entry == null) throw new MolecularBackendException("backend omitted validation evidence");
        evidence.add(entry);
    }
    private static String message(Exception e) { return e.getClass().getSimpleName() + ": " + Objects.toString(e.getMessage(), "no detail"); }
    public record Configuration(GenerationStrategy strategy, int maximumNodes, int maximumDepth,
                                int maximumAttemptedEdits, boolean disconnectedProductsAllowed,
                                MolecularSanitizer.SanitizationPolicy sanitizationPolicy) {
        public Configuration {
            Objects.requireNonNull(strategy); Objects.requireNonNull(sanitizationPolicy);
            if (maximumNodes < 1 || maximumDepth < 0 || maximumAttemptedEdits < 0) throw new IllegalArgumentException("invalid generation bounds");
        }
    }
    public record AuthorizedEdit(GraphEdit edit, GraphEditTransactionEngine.Authorization authorization,
                                 int priority, TopologyEdit topologyEdit,
                                 TopologyEditTransactionEngine.Authorization topologyAuthorization, Provenance provenance) {
        public AuthorizedEdit {
            Objects.requireNonNull(provenance);
            if ((edit == null) == (topologyEdit == null)) throw new IllegalArgumentException("exactly one operation required");
            if (edit != null) Objects.requireNonNull(authorization); else Objects.requireNonNull(topologyAuthorization);
        }
        public AuthorizedEdit(GraphEdit edit, GraphEditTransactionEngine.Authorization authorization, int priority) {
            this(edit, authorization, priority, null, null, Provenance.legacy());
        }
        public AuthorizedEdit(GraphEdit edit, GraphEditTransactionEngine.Authorization authorization, int priority, Provenance provenance) {
            this(edit, authorization, priority, null, null, provenance);
        }
        public AuthorizedEdit(TopologyEdit edit, TopologyEditTransactionEngine.Authorization authorization, int priority, Provenance provenance) {
            this(null, null, priority, edit, authorization, provenance);
        }
        public String operationId() { return edit != null ? edit.editId() : topologyEdit.editId(); }
    }
    @FunctionalInterface public interface AuthorizedEditPlanner { List<AuthorizedEdit> editsFor(MolecularGraph parent, int depth); }
    @FunctionalInterface public interface StateAwarePlanner { List<AuthorizedEdit> editsFor(DesignState parent); }
    @FunctionalInterface public interface GeometryValidator { GeometryResult evaluate(DesignState parent, AuthorizedEdit edit, MolecularGraph product); }
    public record GeometryResult(boolean valid, List<String> evidence, Set<Reference> evaluatedConstraints) {
        public GeometryResult { evidence = List.copyOf(evidence); evaluatedConstraints = Set.copyOf(evaluatedConstraints); }
    }
    private record Planned(DesignState parent, AuthorizedEdit edit, long sequence) { }
}
