package totah.lab.hermes.file.pdbqt;

import java.util.List;

public record PdbqtModel(
        int modelNumber,
        List<PdbqtAtom> atoms,
        PdbqtTorsionTree torsionTree,
        List<String> remarks
) {
    public PdbqtModel {
        atoms = List.copyOf(atoms);
        remarks = List.copyOf(remarks);
    }

    /**
     * Atoms without hydrogens (per the AutoDock type of each atom).
     */
    public List<PdbqtAtom> heavyAtoms() {
        return atoms.stream()
                .filter(atom -> !atom.hydrogen())
                .toList();
    }

    /**
     * Sum of the partial charges over all atoms.
     */
    public double totalCharge() {
        return atoms.stream()
                .mapToDouble(PdbqtAtom::partialCharge)
                .sum();
    }

    /**
     * The torsion count (0 when the file carries no TORSDOF record).
     */
    public int torsdof() {
        return torsionTree != null && torsionTree.torsdof() != null
                ? torsionTree.torsdof()
                : 0;
    }

    /**
     * Rotatable bonds as {parentSerial, childSerial} pairs collected
     * from the BRANCH tree, depth-first.
     */
    public List<int[]> rotatableBondSerials() {
        List<int[]> serials = new java.util.ArrayList<>();
        if (torsionTree != null) {
            collect(torsionTree.branches(), serials);
        }
        return List.copyOf(serials);
    }

    private static void collect(
            List<PdbqtBranch> branches,
            List<int[]> serials
    ) {
        for (PdbqtBranch branch : branches) {
            serials.add(new int[]{branch.parentAtom(), branch.childAtom()});
            collect(branch.children(), serials);
        }
    }
}
