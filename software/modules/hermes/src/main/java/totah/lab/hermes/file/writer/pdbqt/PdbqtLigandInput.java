package totah.lab.hermes.file.writer.pdbqt;

import java.util.List;
import java.util.Objects;

public record PdbqtLigandInput(
        List<PdbqtAtomInput> atoms,
        String rootFragmentId,
        List<PdbqtLigandFragmentInput> fragments) {

    public PdbqtLigandInput {
        atoms = List.copyOf(atoms);
        fragments = List.copyOf(fragments);
        Objects.requireNonNull(rootFragmentId, "rootFragmentId");
        if (atoms.isEmpty()) throw new IllegalArgumentException("Ligand atoms must not be empty.");
    }

    public int torsionalDegreesOfFreedom() { return fragments.size() - 1; }
}
