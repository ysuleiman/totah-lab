package totah.lab.pocket.visualization;

import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.pocket.Pocket;

import java.util.List;
import java.util.Objects;

record PocketDataset(
        Protein protein,
        List<Pocket> pockets) {

    PocketDataset {
        Objects.requireNonNull(protein, "protein");
        pockets = List.copyOf(Objects.requireNonNull(pockets, "pockets"));
    }
}
