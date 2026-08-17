package totah.lab.gaia.graph;

import totah.lab.gaia.classification.ResidueCategory;
import totah.lab.gaia.geometry.AtomSelection;
import totah.lab.gaia.geometry.ResidueGeometry;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Immutable structure-backed residue graph. Cutoff-defined spatial
 * relationships are evaluated on demand against immutable atom indexes.
 */
public final class ResidueGraph {

    private final Structure structure;
    private final List<ResidueId> residueIds;
    private final Map<ResidueId, ResidueNode> nodeIndex;
    private final List<SequenceEdge> sequenceEdges;
    private final Map<AtomSelection, AtomCellIndex> spatialIndexes;

    ResidueGraph(Structure structure, SequencePolicy sequencePolicy) {
        this.structure = Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(sequencePolicy, "sequencePolicy");
        this.nodeIndex = buildNodeIndex(structure);
        this.residueIds = List.copyOf(nodeIndex.keySet());
        this.sequenceEdges = buildSequenceEdges(structure, sequencePolicy);
        this.spatialIndexes = buildSpatialIndexes(structure);
    }

    public static ResidueGraph from(Structure structure) {
        return builder(structure).build();
    }

    public static ResidueGraphBuilder builder(Structure structure) {
        return new ResidueGraphBuilder(structure);
    }

    public Structure structure() {
        return structure;
    }

    /** Returns the exact immutable structure represented by this graph. */
    public Structure toStructure() {
        return structure;
    }

    /** Original structure residues in deterministic structure order. */
    public List<Residue> residues() {
        return nodes().stream()
                .map(ResidueNode::residue)
                .toList();
    }

    /** Stable identities corresponding one-to-one with {@link #residues()}. */
    public List<ResidueId> residueIds() {
        return residueIds;
    }

    public List<ResidueNode> nodes() {
        return List.copyOf(nodeIndex.values());
    }

    public Optional<ResidueNode> findNode(ResidueId residueId) {
        Objects.requireNonNull(residueId, "residueId");
        return Optional.ofNullable(nodeIndex.get(residueId));
    }

    public ResidueNode node(ResidueId residueId) {
        return findNode(residueId).orElseThrow(() ->
                new NoSuchElementException(
                        "Residue node not found: " + residueId));
    }

    public List<SequenceEdge> sequenceEdges() {
        return sequenceEdges;
    }

    public ResidueGraphView view(Collection<ResidueId> selectedResidues) {
        return new ResidueGraphView(this, selectedResidues);
    }

    public ResidueGraphView viewByCategory(ResidueCategory category) {
        Objects.requireNonNull(category, "category");
        return view(nodes().stream()
                .filter(node -> node.chemistry().contains(category))
                .map(ResidueNode::id)
                .toList());
    }

    public List<ResidueDistance> withinDistance(
            double cutoffAngstroms,
            AtomSelection selection) {

        AtomDistanceCriterion.validateCutoff(cutoffAngstroms);
        Objects.requireNonNull(selection, "selection");

        Map<ResiduePair, Double> minimumByPair = new HashMap<>();
        for (IndexedAtomPair atomPair : spatialIndexes.get(selection)
                .pairsWithin(cutoffAngstroms)) {
            ResiduePair pair = new ResiduePair(
                    atomPair.first().residueId(),
                    atomPair.second().residueId());
            minimumByPair.merge(
                    pair,
                    atomPair.distance(),
                    Math::min);
        }

        return minimumByPair.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        ResidueIds.pairComparator()))
                .map(entry -> residueDistance(
                        entry.getKey(),
                        entry.getValue(),
                        selection))
                .toList();
    }

    public List<ResidueAtomProximity> atomProximities(
            AtomDistanceCriterion criterion) {

        Objects.requireNonNull(criterion, "criterion");

        Map<ResiduePair, List<AtomPairDistance>> pairsByResidue =
                new HashMap<>();
        for (IndexedAtomPair atomPair : spatialIndexes
                .get(criterion.atomSelection())
                .pairsWithin(criterion.cutoffAngstroms())) {
            ResiduePair residuePair = new ResiduePair(
                    atomPair.first().residueId(),
                    atomPair.second().residueId());
            AtomPairDistance atomPairDistance = orientedAtomPair(
                    residuePair,
                    atomPair);
            pairsByResidue.computeIfAbsent(
                            residuePair,
                            ignored -> new ArrayList<>())
                    .add(atomPairDistance);
        }

        return pairsByResidue.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        ResidueIds.pairComparator()))
                .map(entry -> new ResidueAtomProximity(
                        entry.getKey().first(),
                        entry.getKey().second(),
                        entry.getValue().stream()
                                .sorted((first, second) -> {
                                    int comparison = first.first()
                                            .compareTo(second.first());
                                    return comparison != 0
                                            ? comparison
                                            : first.second().compareTo(
                                                    second.second());
                                })
                                .toList()))
                .toList();
    }

    Residue residue(ResidueId residueId) {
        ResidueNode node = nodeIndex.get(residueId);
        return node == null ? null : node.residue();
    }

    boolean contains(ResidueId residueId) {
        return nodeIndex.containsKey(residueId);
    }

    private ResidueDistance residueDistance(
            ResiduePair pair,
            double minimumDistance,
            AtomSelection selection) {

        Residue first = residue(pair.first());
        Residue second = residue(pair.second());
        OptionalDouble centroidDistance = centroidDistance(
                first,
                second,
                selection);
        return new ResidueDistance(
                pair.first(),
                pair.second(),
                minimumDistance,
                ResidueGeometry.alphaCarbonDistance(first, second),
                centroidDistance);
    }

    private static OptionalDouble centroidDistance(
            Residue first,
            Residue second,
            AtomSelection selection) {

        return ResidueGeometry.centroid(first, selection)
                .flatMap(firstCentroid -> ResidueGeometry
                        .centroid(second, selection)
                        .map(firstCentroid::distance))
                .map(OptionalDouble::of)
                .orElseGet(OptionalDouble::empty);
    }

    private static AtomPairDistance orientedAtomPair(
            ResiduePair residuePair,
            IndexedAtomPair atomPair) {

        boolean alreadyOriented = atomPair.first().residueId()
                .equals(residuePair.first());
        return new AtomPairDistance(
                alreadyOriented
                        ? atomPair.first().reference()
                        : atomPair.second().reference(),
                alreadyOriented
                        ? atomPair.second().reference()
                        : atomPair.first().reference(),
                atomPair.distance());
    }

    private static Map<ResidueId, ResidueNode> buildNodeIndex(
            Structure structure) {

        Map<ResidueId, ResidueNode> result = new LinkedHashMap<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId id = residueId(chain.id(), residue);
                ResidueNode node = new ResidueNode(
                        id,
                        residue,
                        ResidueChemistry.from(residue));
                if (result.putIfAbsent(id, node) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate residue identity: " + id);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<AtomSelection, AtomCellIndex> buildSpatialIndexes(
            Structure structure) {

        Map<AtomSelection, List<IndexedAtom>> selectedAtoms =
                new EnumMap<>(AtomSelection.class);
        for (AtomSelection selection : AtomSelection.values()) {
            selectedAtoms.put(selection, new ArrayList<>());
        }

        int ordinal = 0;
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId residueId = residueId(chain.id(), residue);
                for (Atom atom : residue.getAtoms()) {
                    AtomReference reference = atomReference(
                            chain.id(), residue, atom);
                    for (AtomSelection selection : AtomSelection.values()) {
                        if (selection.includes(atom)) {
                            selectedAtoms.get(selection).add(new IndexedAtom(
                                    ordinal,
                                    residueId,
                                    reference,
                                    atom.getPosition()));
                        }
                    }
                    ordinal++;
                }
            }
        }

        Map<AtomSelection, AtomCellIndex> indexes =
                new EnumMap<>(AtomSelection.class);
        selectedAtoms.forEach((selection, atoms) ->
                indexes.put(selection, new AtomCellIndex(atoms)));
        return Map.copyOf(indexes);
    }

    private static List<SequenceEdge> buildSequenceEdges(
            Structure structure,
            SequencePolicy policy) {

        if (policy == SequencePolicy.NONE) {
            return List.of();
        }

        Map<AtomReference, ResidueId> residueByAtom = new HashMap<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId residueId = residueId(chain.id(), residue);
                for (Atom atom : residue.getAtoms()) {
                    residueByAtom.put(
                            atomReference(chain.id(), residue, atom),
                            residueId);
                }
            }
        }

        Set<ResiduePair> explicit = new LinkedHashSet<>();
        for (Bond bond : structure.bonds()) {
            ResidueId first = residueByAtom.get(bond.atom1());
            ResidueId second = residueByAtom.get(bond.atom2());
            if (first != null
                    && second != null
                    && !first.equals(second)
                    && first.chainId().equals(second.chainId())
                    && isPolymerLinkage(bond)) {
                explicit.add(new ResiduePair(first, second));
            }
        }

        Map<ResiduePair, SequenceEdgeProvenance> edges = new HashMap<>();
        SequenceEdgeProvenance bondProvenance = bondProvenance(
                structure.getConnectivityMetadata().provenance());
        explicit.forEach(pair -> edges.put(pair, bondProvenance));

        if (policy == SequencePolicy.EXPLICIT_OR_CHAIN_ORDER) {
            for (Chain chain : structure.getChains()) {
                List<Residue> chainResidues = chain.residues();
                for (int index = 1; index < chainResidues.size(); index++) {
                    ResiduePair pair = new ResiduePair(
                            residueId(chain.id(), chainResidues.get(index - 1)),
                            residueId(chain.id(), chainResidues.get(index)));
                    edges.putIfAbsent(
                            pair,
                            SequenceEdgeProvenance.CHAIN_ORDER_INFERRED);
                }
            }
        }

        return edges.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        ResidueIds.pairComparator()))
                .map(entry -> new SequenceEdge(
                        entry.getKey().first(),
                        entry.getKey().second(),
                        entry.getValue()))
                .toList();
    }

    private static boolean isPolymerLinkage(Bond bond) {
        String first = bond.atom1().atomName();
        String second = bond.atom2().atomName();
        return namesMatch(first, second, "C", "N")
                || namesMatch(first, second, "O3'", "P")
                || namesMatch(first, second, "O3*", "P");
    }

    private static SequenceEdgeProvenance bondProvenance(
            ConnectivityProvenance provenance) {

        return switch (provenance) {
            case EXPLICIT -> SequenceEdgeProvenance.EXPLICIT_BOND;
            case PARTIAL ->
                    SequenceEdgeProvenance.PARTIAL_CONNECTIVITY_BOND;
            case INFERRED ->
                    SequenceEdgeProvenance.INFERRED_CONNECTIVITY_BOND;
            case ABSENT -> SequenceEdgeProvenance.UNVERIFIED_BOND;
        };
    }

    private static boolean namesMatch(
            String first,
            String second,
            String expectedFirst,
            String expectedSecond) {

        return first.equalsIgnoreCase(expectedFirst)
                && second.equalsIgnoreCase(expectedSecond)
                || first.equalsIgnoreCase(expectedSecond)
                && second.equalsIgnoreCase(expectedFirst);
    }

    private static ResidueId residueId(
            String chainId,
            Residue residue) {

        return new ResidueId(
                chainId,
                residue.getNumber(),
                residue.getInsertionCode());
    }

    private static AtomReference atomReference(
            String chainId,
            Residue residue,
            Atom atom) {

        return new AtomReference(
                chainId,
                residue.getNumber(),
                residue.getInsertionCode() == null
                        ? ' '
                        : residue.getInsertionCode(),
                atom.getName());
    }
}
