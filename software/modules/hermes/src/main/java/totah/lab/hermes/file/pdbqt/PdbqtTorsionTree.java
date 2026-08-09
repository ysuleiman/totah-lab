package totah.lab.hermes.file.pdbqt;

import java.util.List;

public record PdbqtTorsionTree(
        List<PdbqtAtom> rootAtoms,
        List<PdbqtBranch> branches,
        Integer torsdof
) {
    public PdbqtTorsionTree {
        rootAtoms = List.copyOf(rootAtoms);
        branches = List.copyOf(branches);
    }
}
