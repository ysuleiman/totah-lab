package totah.lab.gaia.graph;

import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;

/** Undirected polymer sequence adjacency with explicit provenance. */
public record SequenceEdge(
        ResidueId first,
        ResidueId second,
        SequenceEdgeProvenance provenance) {

    public SequenceEdge {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(provenance, "provenance");
        ResiduePair pair = new ResiduePair(first, second);
        first = pair.first();
        second = pair.second();
    }
}
