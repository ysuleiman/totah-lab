package totah.lab.gaia.graph;

import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;

/** Canonically ordered undirected residue pair. */
public record ResiduePair(ResidueId first, ResidueId second) {

    public ResiduePair {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second)) {
            throw new IllegalArgumentException(
                    "A residue pair requires distinct residues");
        }
        if (ResidueIds.compare(first, second) > 0) {
            ResidueId originalFirst = first;
            first = second;
            second = originalFirst;
        }
    }
}
