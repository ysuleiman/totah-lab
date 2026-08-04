package totah.lab.athena.pocket.compare.residue;



import totah.lab.athena.pocket.selection.PocketResidueSelection;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Objects;

public final class PocketResiduePointFactory {

    private final PocketResidueSelection selection;
    private final PocketResidueMapper mapper;

    public PocketResiduePointFactory() {
        this(
                new PocketResidueSelection(),
                new PocketResidueMapper()
        );
    }

    public PocketResiduePointFactory(
            PocketResidueSelection selection,
            PocketResidueMapper mapper
    ) {
        this.selection = Objects.requireNonNull(
                selection,
                "selection"
        );

        this.mapper = Objects.requireNonNull(
                mapper,
                "mapper"
        );
    }

    public List<PocketResiduePoint> create(
            Structure structure,
            Pocket pocket
    ) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");

        return selection
                .resolvedPocketResidues(structure, pocket)
                .stream()
                .map(resolved -> mapper.map(resolved))
                .toList();
    }
}