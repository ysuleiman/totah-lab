package totah.lab.docking.torsion;

import java.util.*;

public class TorsionTree {

    private final List<Integer> rootAtoms = new ArrayList<>();
    private final List<TorsionBranch> rootBranches = new ArrayList<>();
    private final Set<Integer> allAtoms = new HashSet<>();

    public List<Integer> getRootAtoms() {
        return Collections.unmodifiableList(rootAtoms);
    }

    public List<TorsionBranch> getRootBranches() {
        return Collections.unmodifiableList(rootBranches);
    }

    public boolean containsAtom(int idx) {
        return allAtoms.contains(idx);
    }

    public void addRootAtom(int idx) {
        rootAtoms.add(idx);
        allAtoms.add(idx);
    }

    public void addRootBranch(TorsionBranch branch) {
        rootBranches.add(branch);
        collectAtoms(branch);
    }

    private void collectAtoms(TorsionBranch branch) {
        allAtoms.addAll(branch.movingAtoms);
        for (TorsionBranch child : branch.children) {
            collectAtoms(child);
        }
    }

    public List<TorsionBranch> flattenBranches() {
        List<TorsionBranch> result = new ArrayList<>();
        Deque<TorsionBranch> stack = new ArrayDeque<>();
        for (int i = rootBranches.size() - 1; i >= 0; i--) {
            stack.push(rootBranches.get(i));
        }
        while (!stack.isEmpty()) {
            TorsionBranch b = stack.pop();
            result.add(b);
            for (int i = b.children.size() - 1; i >= 0; i--) {
                stack.push(b.children.get(i));
            }
        }
        return result;
    }
}
