package totah.lab.hermes.file.pdbqt;

import java.util.List;
import java.util.Objects;

public record PdbqtLigand(
        List<PdbqtAtomReference> atoms,
        String rootFragmentId,
        List<PdbqtLigandFragment> fragments) {

    public PdbqtLigand {
        atoms = List.copyOf(atoms);
        fragments = List.copyOf(fragments);
        Objects.requireNonNull(rootFragmentId, "rootFragmentId");
        if (atoms.isEmpty()) throw new IllegalArgumentException("Ligand atoms must not be empty.");
    }

    public int torsionalDegreesOfFreedom() { return fragments.size() - 1; }
}
