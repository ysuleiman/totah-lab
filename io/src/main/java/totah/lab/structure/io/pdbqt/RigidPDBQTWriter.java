package totah.lab.structure.io.pdbqt;

import totah.lab.protein.Atom;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.Objects;

public class RigidPDBQTWriter extends PrintWriter {

    private int atomSerial = 1;
    private final PdbqtAtomLineFormatter formatter = new PdbqtAtomLineFormatter();
    public RigidPDBQTWriter(Writer writer){
        this(writer, false);
    }
    public RigidPDBQTWriter(Writer writer, boolean autoFlush){
        super(writer, autoFlush);
    }

    public void writeStructure(Structure structure) {
        String previousChain = null;
        for (Residue residue : structure.getResidues()) {
            if (previousChain != null &&
                    !previousChain.equals(residue.getChain())) {
                println("TER");
            }
            for (Atom atom : residue.getAtoms()) {
                print(formatAtomLine(residue, atom));
            }
            previousChain = residue.getChain();
        }
        println("END");
        flush();
    }

    public void write(List<Residue> residues){
        Objects.requireNonNull(residues);
        this.atomSerial = 1;
        if(residues.isEmpty()){
            return;
        }
        for (Residue residue : residues) {
            for(int i=0;i<residue.getAtoms().size();i++) {
                Atom atom = residue.getAtoms().get(i);
                String text = formatAtomLine(residue, atom);
                this.write(text);
            }
        }
    }

    public void write(Residue residue){
        Objects.requireNonNull(residue);

        for(int i=0;i<residue.getAtoms().size();i++) {
            Atom atom = residue.getAtoms().get(i);
            String text = formatAtomLine(residue, atom);
            this.write(text);
        }
    }

    public void write(Residue residue, Atom atom){
        Objects.requireNonNull(residue);
        Objects.requireNonNull(atom);
        String text = formatAtomLine(residue, atom);
        this.write(text);
    }

    private String formatAtomLine(Residue residue, Atom atom) {
        String adType = atom.getAutoDockType() != null
                ? atom.getAutoDockType()
                : atom.getElement().getSymbol();
        Atom formattedAtom = atom.getAutoDockType() == null
                ? atom.toBuilder().autoDockType(adType).build()
                : atom;
        return formatter.format(residue, formattedAtom, atomSerial++);
    }

}
