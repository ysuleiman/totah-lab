package totah.lab.structure.io.pdbqt;

import totah.lab.docking.torsion.TorsionBranch;
import totah.lab.docking.torsion.TorsionTree;
import totah.lab.protein.Atom;
import totah.lab.protein.Residue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FlexPDBQTWriter extends PrintWriter {

    private int atomSerial;
    public FlexPDBQTWriter(Writer writer){
        this(writer, false);
    }
    public FlexPDBQTWriter(Writer writer, boolean autoFlush){
        super(writer, autoFlush);
    }

    public void write(Map<Residue, TorsionTree> flexTrees) throws IOException {
        for (Map.Entry<Residue, TorsionTree> entry : flexTrees.entrySet()) {
            Residue residue = entry.getKey();
            TorsionTree tree = entry.getValue();
            List<Atom> atoms = residue.getAtoms();

            Map<Integer, Integer> serials = new HashMap<>();
            int next = 1;
            for (int i : tree.getRootAtoms()) {
                serials.put(i, next++);
            }
            for (TorsionBranch branch : tree.flattenBranches()) {   // ← real method name
                for (int i : branch.getMovingAtoms()) {
                    serials.putIfAbsent(i, next++);
                }
            }
            this.println("BEGIN_RES " + residue.getName() + " "
                    + residue.getChain() + " " + residue.getNumber());
            this.println("ROOT");
            for (int i : tree.getRootAtoms()) {
                this.println(formatAtomLine(atoms.get(i), residue, serials.get(i)));
            }
            this.println("ENDROOT");
            for (TorsionBranch branch : tree.getRootBranches()) {   // ← real method name, only roots
                writeBranch(branch, residue, atoms, serials);
            }
            this.println("END_RES " + residue.getName() + " "
                    + residue.getChain() + " " + residue.getNumber());
            this.println();
        }
    }

    private void writeBranch(TorsionBranch branch, Residue residue,
                             List<Atom> atoms, Map<Integer, Integer> serials) throws IOException {
        int p = serials.get(branch.getParentIdx());
        int c = serials.get(branch.getChildIdx());
        this.println("BRANCH " + p + " " + c);
        this.println(formatAtomLine(atoms.get(branch.getChildIdx()), residue, c));

        for (int i : branch.getMovingAtoms()) {
            if (i == branch.getChildIdx()) continue;
            this.println(formatAtomLine(atoms.get(i), residue, serials.get(i)));
        }
        for (TorsionBranch child : branch.getChildren()) {
            writeBranch(child, residue, atoms, serials);
        }
        this.println("ENDBRANCH " + p + " " + c);
    }

    private String formatAtomLine(Atom atom, Residue residue, int atomIndex) {
        String ad4Type = atom.getAutoDockType();
        if (ad4Type == null) {
            if (atom.getElement() == null || atom.getElement().getSymbol() == null) {
                throw new IllegalStateException("Atom '" + atom.getName() + "' in residue "
                        + residue.getName() + " " + residue.getChain() + ":" + residue.getNumber()
                        + " has neither an AutoDock type nor an element - cannot write PDBQT");
            }
            ad4Type = atom.getElement().getSymbol();
        }

        return String.format(Locale.US,
                "ATOM  %5d %-4s %3s %1s%4d    %8.3f%8.3f%8.3f%6.2f%6.2f    %+7.4f %-2s",
                atomIndex,
                atom.getName(),
                residue.getName(),
                residue.getChain(),
                residue.getNumber(),
                atom.getPosition().x(),
                atom.getPosition().y(),
                atom.getPosition().z(),
                atom.getOccupancy(),
                atom.getBFactor(),
                atom.getCharge(),
                ad4Type
        );
    }
}
