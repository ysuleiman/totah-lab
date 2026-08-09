package totah.lab.hermes.file.pdbqt;

import java.util.List;

public record PdbqtBranch(
        int parentAtom,
        int childAtom,
        List<PdbqtAtom> atoms,
        List<PdbqtBranch> children
) {
    public PdbqtBranch {
        atoms = List.copyOf(atoms);
        children = List.copyOf(children);
    }
}
