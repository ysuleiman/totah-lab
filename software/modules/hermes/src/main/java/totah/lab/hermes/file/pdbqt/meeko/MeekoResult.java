package totah.lab.hermes.file.pdbqt.meeko;

import java.util.List;

public record MeekoResult(
        String smiles,
        List<IndexPair> smilesIndices,
        List<HydrogenParent> hydrogenParents,
        List<String> otherRemarks
) {
    public MeekoResult {
        smilesIndices = List.copyOf(smilesIndices);
        hydrogenParents = List.copyOf(hydrogenParents);
        otherRemarks = List.copyOf(otherRemarks);
    }

    public record IndexPair(
            int first,
            int second
    ) {}

    public record HydrogenParent(
            int parentAtom,
            int hydrogenAtom
    ) {}
}
