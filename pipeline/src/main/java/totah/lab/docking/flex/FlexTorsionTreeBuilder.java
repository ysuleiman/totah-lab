package totah.lab.docking.flex;

import totah.lab.docking.torsion.TorsionBranch;
import totah.lab.docking.torsion.TorsionTree;
import totah.lab.protein.Atom;
import totah.lab.protein.Residue;
import totah.lab.protein.Topology;
import totah.lab.topology.SideChainRotamers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlexTorsionTreeBuilder {

    private static final Set<String> FLEX_BACKBONE_NAMES = Set.of("N", "C", "O", "OXT");

    public TorsionTree build(Residue residue, int flatBase, Topology topology) {
        List<Atom> atoms = residue.getAtoms();
        int n = atoms.size();

        // Local adjacency restricted to this residue's own atoms
        List<List<Integer>> neighbors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<Integer> local = new ArrayList<>();
            for (int j : topology.getNeighbors(flatBase + i)) {
                int lj = j - flatBase;
                if (lj >= 0 && lj < n) local.add(lj);
            }
            neighbors.add(local);
        }

        // The distance-based topology contains spurious H···heavy contacts
        // (e.g. CA···HB2) that would bypass the chi-bond blocks, so torsion
        // trees are built over heavy atoms only; each hydrogen is attached
        // to its closest heavy neighbor
        boolean[] hydrogen = new boolean[n];
        Map<Integer, Integer> hydrogenParent = new HashMap<>();
        for (int i = 0; i < n; i++) {
            hydrogen[i] = isHydrogen(atoms.get(i));
            if (!hydrogen[i]) continue;
            int best = -1;
            double bestDist = Double.MAX_VALUE;
            for (int j : neighbors.get(i)) {
                if (hydrogen[j]) continue;
                double d = atoms.get(i).getPosition().distance(atoms.get(j).getPosition());
                if (d < bestDist) {
                    bestDist = d;
                    best = j;
                }
            }
            if (best >= 0) hydrogenParent.put(i, best);
        }

        // Rigid backbone: N, C, O, OXT plus hydrogens attached to any rigid
        // backbone atom (N-H amides, but also e.g. a protonated OXT, which
        // would otherwise land in neither file and be silently dropped);
        // CA and the side chain go to the flex file (Meeko convention)
        Set<Integer> rigid = new HashSet<>();
        int caIdx = -1;
        for (int i = 0; i < n; i++) {
            String name = atoms.get(i).getName();
            if (FLEX_BACKBONE_NAMES.contains(name)) rigid.add(i);
            if (name.equals("CA")) caIdx = i;
        }
        if (caIdx < 0) {
            throw new IllegalStateException("Flex residue " + residue.getName() + " "
                    + residue.getChain() + ":" + residue.getNumber() + " has no CA atom");
        }
        for (Map.Entry<Integer, Integer> h : hydrogenParent.entrySet()) {
            if (rigid.contains(h.getValue())) rigid.add(h.getKey());
        }

        // Active chi bonds (skip with a warning if an atom is missing, e.g.
        // dropped by the pLDDT filter)
        List<int[]> activeChi = new ArrayList<>();
        Set<Long> rotatableEdges = new HashSet<>();
        for (SideChainRotamers.ChiBond chi : SideChainRotamers.chiBonds(residue.getName())) {
            int parent = indexOf(atoms, chi.parent());
            int child = indexOf(atoms, chi.child());
            if (parent < 0 || child < 0) {
                System.err.println("[PdbqtExporter] Warning: flex residue " + residue.getName() + " "
                        + residue.getChain() + ":" + residue.getNumber() + " is missing chi bond "
                        + chi.parent() + "-" + chi.child() + " - skipping it");
                continue;
            }
            activeChi.add(new int[]{parent, child});
            rotatableEdges.add(edgeKey(parent, child));
        }

        TorsionTree tree = new TorsionTree();

        for (int i : withHydrogens(reachable(caIdx, -1, rigid, neighbors, rotatableEdges, hydrogen),
                hydrogen, hydrogenParent, n)) {
            if (!rigid.contains(i)) tree.addRootAtom(i);
        }

        if (!activeChi.isEmpty()) {
            Set<Long> emitted = new HashSet<>();
            TorsionBranch first = collectBranchAtoms(
                    activeChi.get(0)[0], activeChi.get(0)[1],
                    activeChi, rigid, neighbors, rotatableEdges,
                    emitted, hydrogen, hydrogenParent, n
            );
            tree.addRootBranch(first);
        }
        return tree;
    }

    private TorsionBranch collectBranchAtoms(int parentIdx, int childIdx, List<int[]> activeChi,
                                            Set<Integer> rigid, List<List<Integer>> neighbors,
                                            Set<Long> rotatableEdges, Set<Long> emitted,
                                            boolean[] hydrogen, Map<Integer, Integer> hydrogenParent, int n) {
        emitted.add(edgeKey(parentIdx, childIdx));
        Set<Integer> movingHeavy = reachable(childIdx, parentIdx, rigid, neighbors,
                rotatableEdges, hydrogen);
        List<Integer> moving = withHydrogens(movingHeavy, hydrogen, hydrogenParent, n);
        TorsionBranch branch = new TorsionBranch(parentIdx, childIdx, moving);

        for (int[] chi : activeChi) {
            if (emitted.contains(edgeKey(chi[0], chi[1]))) continue;
            if (movingHeavy.contains(chi[0])) {
                branch.getChildren().add(
                        collectBranchAtoms(chi[0], chi[1], activeChi, rigid, neighbors,
                                rotatableEdges, emitted, hydrogen, hydrogenParent, n)
                );
            }
        }
        return branch;
    }

    /** Heavy atom set expanded with its attached hydrogens, in residue atom order. */
    private List<Integer> withHydrogens(Set<Integer> heavySet, boolean[] hydrogen,
                                        Map<Integer, Integer> hydrogenParent, int n) {
        List<Integer> ordered = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (hydrogen[i]) {
                Integer parent = hydrogenParent.get(i);
                if (parent != null && heavySet.contains(parent)) ordered.add(i);
            } else if (heavySet.contains(i)) {
                ordered.add(i);
            }
        }
        return ordered;
    }

    /**
     * Heavy atoms reachable from start without crossing any rotatable bond.
     * The edge back to excludeIdx (the branch parent) is blocked too.
     */
    private Set<Integer> reachable(int start, int excludeIdx, Set<Integer> rigid,
                                   List<List<Integer>> neighbors, Set<Long> rotatableEdges,
                                   boolean[] hydrogen) {
        Set<Integer> visited = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            int i = queue.poll();
            for (int j : neighbors.get(i)) {
                if (visited.contains(j) || rigid.contains(j) || hydrogen[j]) continue;
                if (j == excludeIdx) continue;
                if (rotatableEdges.contains(edgeKey(i, j))) continue;
                visited.add(j);
                queue.add(j);
            }
        }
        return visited;
    }

    private long edgeKey(int a, int b) {
        return a < b ? ((long) a << 32) | b : ((long) b << 32) | a;
    }

    private int indexOf(List<Atom> atoms, String name) {
        for (int i = 0; i < atoms.size(); i++) {
            if (atoms.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private boolean isHydrogen(Atom atom) {
        return atom.getElement() != null && "H".equals(atom.getElement().getSymbol());
    }
}
