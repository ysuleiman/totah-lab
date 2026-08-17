package totah.lab.gaia.graph;

import totah.lab.gaia.classification.ResidueCategory;
import totah.lab.gaia.geometry.AtomSelection;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable induced view over a selected set of graph residues. */
public final class ResidueGraphView {

    private final ResidueGraph graph;
    private final List<ResidueId> residueIds;
    private final Set<ResidueId> residueSet;

    ResidueGraphView(
            ResidueGraph graph,
            Collection<ResidueId> selectedResidues) {

        this.graph = Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(selectedResidues, "selectedResidues");

        Set<ResidueId> requested = new LinkedHashSet<>();
        for (ResidueId residueId : selectedResidues) {
            Objects.requireNonNull(
                    residueId,
                    "selectedResidues must not contain null elements");
            if (!graph.contains(residueId)) {
                throw new IllegalArgumentException(
                        "Residue is not present in graph: " + residueId);
            }
            requested.add(residueId);
        }

        this.residueIds = graph.residueIds().stream()
                .filter(requested::contains)
                .toList();
        this.residueSet = Set.copyOf(requested);
    }

    public Structure structure() {
        return graph.structure();
    }

    /**
     * Materializes this induced residue view as a standalone structure while
     * preserving source chain/residue order and internal bonds.
     */
    public Structure toStructure() {
        List<Chain> selectedChains = new ArrayList<>();
        for (Chain chain : graph.structure().getChains()) {
            List<Residue> selected = chain.residues().stream()
                    .filter(residue -> residueSet.contains(new ResidueId(
                            chain.id(),
                            residue.getNumber(),
                            residue.getInsertionCode())))
                    .toList();
            if (!selected.isEmpty()) {
                selectedChains.add(new Chain(chain.id(), selected));
            }
        }

        return new Structure(
                selectedChains,
                graph.structure().bonds().stream()
                        .filter(bond -> residueSet.contains(
                                residueId(bond.atom1())))
                        .filter(bond -> residueSet.contains(
                                residueId(bond.atom2())))
                        .toList(),
                graph.structure().getConnectivityMetadata());
    }

    /** Original structure residues selected by this view. */
    public List<Residue> residues() {
        return nodes().stream()
                .map(ResidueNode::residue)
                .toList();
    }

    public List<ResidueId> residueIds() {
        return residueIds;
    }

    public List<ResidueNode> nodes() {
        return residueIds.stream()
                .map(graph::node)
                .toList();
    }

    public List<SequenceEdge> sequenceEdges() {
        return graph.sequenceEdges().stream()
                .filter(edge -> includes(
                        edge.first(),
                        edge.second()))
                .toList();
    }

    public ResidueGraphView view(Collection<ResidueId> selectedResidues) {
        Objects.requireNonNull(selectedResidues, "selectedResidues");
        for (ResidueId residueId : selectedResidues) {
            if (!residueSet.contains(residueId)) {
                throw new IllegalArgumentException(
                        "Residue is not present in view: " + residueId);
            }
        }
        return graph.view(selectedResidues);
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

        return graph.withinDistance(cutoffAngstroms, selection).stream()
                .filter(distance -> includes(
                        distance.first(),
                        distance.second()))
                .toList();
    }

    public List<ResidueAtomProximity> atomProximities(
            AtomDistanceCriterion criterion) {

        return graph.atomProximities(criterion).stream()
                .filter(proximity -> includes(
                        proximity.first(),
                        proximity.second()))
                .toList();
    }

    private boolean includes(ResidueId first, ResidueId second) {
        return residueSet.contains(first) && residueSet.contains(second);
    }

    private static ResidueId residueId(AtomReference atom) {
        return new ResidueId(
                atom.chainId(),
                atom.residueNumber(),
                atom.insertionCode());
    }
}
