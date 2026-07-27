package totah.lab.docking.torsion;


import java.util.ArrayList;
import java.util.List;

public class TorsionBranch {
    final int parentIdx;
    final int childIdx;
    final List<Integer> movingAtoms;
    final List<TorsionBranch> children = new ArrayList<>();

    public TorsionBranch(int parentIdx, int childIdx, List<Integer> movingAtoms) {
        this.parentIdx = parentIdx;
        this.childIdx = childIdx;
        this.movingAtoms = movingAtoms;
    }

    public List<TorsionBranch> getChildren() {
        return children;
    }

    public int getChildIdx() {
        return childIdx;
    }

    public int getParentIdx() {
        return parentIdx;
    }

    public List<Integer> getMovingAtoms() {
        return movingAtoms;
    }
}
