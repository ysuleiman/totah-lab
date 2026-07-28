package totah.lab.ligand.torsion;

import totah.lab.docking.torsion.TorsionTree;

import java.util.List;
import java.util.Objects;

public record LigandTorsionTreeResult(
        TorsionTree tree,
        LigandRotatableBondReport bondReport,
        List<Integer> rootFragmentAtoms,
        int torsionalDegreesOfFreedom) {

    public LigandTorsionTreeResult {
        Objects.requireNonNull(tree, "tree is null");
        Objects.requireNonNull(bondReport, "bondReport is null");
        rootFragmentAtoms = List.copyOf(
                Objects.requireNonNull(rootFragmentAtoms, "rootFragmentAtoms is null"));
        if (torsionalDegreesOfFreedom < 0) {
            throw new IllegalArgumentException(
                    "torsionalDegreesOfFreedom must be non-negative");
        }
        if (torsionalDegreesOfFreedom != bondReport.rotatableBondCount()) {
            throw new IllegalArgumentException(
                    "TORSDOF must equal the active rotatable-bond count");
        }
    }
}
