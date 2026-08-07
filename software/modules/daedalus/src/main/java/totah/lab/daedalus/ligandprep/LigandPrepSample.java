package totah.lab.daedalus.ligandprep;

import java.util.Objects;

/**
 * One sampled chemflow3 compound with its source SDF and Meeko
 * prepared-ligand PDBQT artifact URIs.
 */
public record LigandPrepSample(
        String compoundId,
        String smiles,
        String sdfUri,
        String meekoPdbqtUri
) {
    public LigandPrepSample {
        Objects.requireNonNull(compoundId, "compoundId");
        Objects.requireNonNull(sdfUri, "sdfUri");
        Objects.requireNonNull(meekoPdbqtUri, "meekoPdbqtUri");
    }
}
