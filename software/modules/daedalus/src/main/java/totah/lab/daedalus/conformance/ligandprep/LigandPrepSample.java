package totah.lab.daedalus.conformance.ligandprep;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One reference ligand: the source SDF and the Meeko
 * (mk_prepare_ligand.py) prepared-ligand PDBQT we generated locally
 * from it.
 */
public record LigandPrepSample(
        String id,
        String name,
        Path sdf,
        Path meekoPdbqt
) {
    public LigandPrepSample {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sdf, "sdf");
        Objects.requireNonNull(meekoPdbqt, "meekoPdbqt");
    }
}
