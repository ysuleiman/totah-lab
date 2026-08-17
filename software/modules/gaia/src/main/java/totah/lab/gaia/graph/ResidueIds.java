package totah.lab.gaia.graph;

import totah.lab.gaia.structure.ResidueId;

import java.util.Comparator;

final class ResidueIds {

    static final Comparator<ResidueId> COMPARATOR =
            Comparator.comparing(ResidueId::chainId)
                    .thenComparingInt(ResidueId::residueNumber)
                    .thenComparingInt(id -> id.insertionCode() == null
                            ? ' '
                            : id.insertionCode());

    private ResidueIds() {
    }

    static int compare(ResidueId first, ResidueId second) {
        return COMPARATOR.compare(first, second);
    }

    static Comparator<ResiduePair> pairComparator() {
        return Comparator.comparing(
                        ResiduePair::first,
                        COMPARATOR)
                .thenComparing(ResiduePair::second, COMPARATOR);
    }
}
