package totah.lab.structure.io.pdbqt;

import totah.lab.docking.torsion.TorsionBranch;
import totah.lab.docking.torsion.TorsionTree;
import totah.lab.protein.Atom;
import totah.lab.protein.Residue;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LigandPDBQTWriter extends PrintWriter {

    private static final Set<String> LEGAL_TYPES = Set.of(
            "C", "A", "N", "NA", "O", "OA", "S", "SA", "P", "HD", "H",
            "F", "Cl", "Br", "I", "Mg", "Mn", "Fe", "Zn", "Ca");

    private final PdbqtAtomLineFormatter formatter = new PdbqtAtomLineFormatter();

    public LigandPDBQTWriter(Writer writer) {
        super(Objects.requireNonNull(writer, "writer is null"));
    }

    public void write(
            Residue ligand,
            TorsionTree tree,
            int torsionalDegreesOfFreedom) {
        Objects.requireNonNull(ligand, "ligand is null");
        Objects.requireNonNull(tree, "tree is null");
        validate(ligand, tree, torsionalDegreesOfFreedom);
        Map<Integer, Integer> serials = serials(tree);

        println("ROOT");
        for (int atomIndex : tree.getRootAtoms()) {
            print(formatter.format(
                    ligand, ligand.getAtoms().get(atomIndex), serials.get(atomIndex)));
        }
        println("ENDROOT");
        for (TorsionBranch branch : tree.getRootBranches()) {
            writeBranch(ligand, branch, serials);
        }
        println("TORSDOF " + torsionalDegreesOfFreedom);
        flush();
        if (checkError()) {
            throw new IllegalStateException("Failed to write ligand PDBQT");
        }
    }

    private void writeBranch(
            Residue ligand,
            TorsionBranch branch,
            Map<Integer, Integer> serials) {
        int parentSerial = serials.get(branch.getParentIdx());
        int childSerial = serials.get(branch.getChildIdx());
        println("BRANCH " + parentSerial + " " + childSerial);
        for (int atomIndex : branch.getMovingAtoms()) {
            print(formatter.format(
                    ligand, ligand.getAtoms().get(atomIndex), serials.get(atomIndex)));
        }
        for (TorsionBranch child : branch.getChildren()) {
            writeBranch(ligand, child, serials);
        }
        println("ENDBRANCH " + parentSerial + " " + childSerial);
    }

    private Map<Integer, Integer> serials(TorsionTree tree) {
        Map<Integer, Integer> serials = new HashMap<>();
        int serial = 1;
        for (int atom : tree.getRootAtoms()) {
            serials.put(atom, serial++);
        }
        for (TorsionBranch branch : tree.flattenBranches()) {
            for (int atom : branch.getMovingAtoms()) {
                serials.put(atom, serial++);
            }
        }
        return Map.copyOf(serials);
    }

    private void validate(
            Residue ligand,
            TorsionTree tree,
            int torsionalDegreesOfFreedom) {
        List<Atom> atoms = ligand.getAtoms();
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("Cannot write an empty ligand");
        }
        Set<Integer> covered = new HashSet<>();
        Set<Integer> availableParents = new HashSet<>();
        for (int index : tree.getRootAtoms()) {
            validateAtomIndex(index, atoms.size(), covered);
            availableParents.add(index);
        }
        int branchCount = 0;
        var queue = new ArrayDeque<TorsionBranch>(tree.getRootBranches());
        while (!queue.isEmpty()) {
            TorsionBranch branch = queue.removeFirst();
            branchCount++;
            if (!availableParents.contains(branch.getParentIdx())
                    || !branch.getMovingAtoms().contains(branch.getChildIdx())) {
                throw new IllegalArgumentException("Invalid ligand PDBQT branch endpoints");
            }
            for (int index : branch.getMovingAtoms()) {
                validateAtomIndex(index, atoms.size(), covered);
                availableParents.add(index);
            }
            queue.addAll(branch.getChildren());
        }
        if (covered.size() != atoms.size()) {
            throw new IllegalArgumentException("Ligand PDBQT tree has incomplete atom coverage");
        }
        if (torsionalDegreesOfFreedom < 0 || torsionalDegreesOfFreedom != branchCount) {
            throw new IllegalArgumentException(
                    "Ligand TORSDOF does not match branch count");
        }
        for (int index = 0; index < atoms.size(); index++) {
            validateAtom(atoms.get(index), index);
        }
    }

    private void validateAtomIndex(int index, int atomCount, Set<Integer> covered) {
        if (index < 0 || index >= atomCount || !covered.add(index)) {
            throw new IllegalArgumentException(
                    "Invalid or duplicate ligand atom index in PDBQT tree: " + index);
        }
    }

    private void validateAtom(Atom atom, int index) {
        if (atom.getPosition() == null
                || !Double.isFinite(atom.getPosition().x())
                || !Double.isFinite(atom.getPosition().y())
                || !Double.isFinite(atom.getPosition().z())) {
            throw new IllegalArgumentException(
                    "Non-finite ligand coordinates at atom index " + index);
        }
        if (!Double.isFinite(atom.getCharge())) {
            throw new IllegalArgumentException(
                    "Non-finite ligand charge at atom index " + index);
        }
        if (!LEGAL_TYPES.contains(atom.getAutoDockType())) {
            throw new IllegalArgumentException(
                    "Missing or illegal ligand AutoDock4 type at atom index " + index);
        }
    }
}
