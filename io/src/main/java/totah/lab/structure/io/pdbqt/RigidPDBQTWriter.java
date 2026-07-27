package totah.lab.structure.io.pdbqt;

import totah.lab.protein.Atom;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class RigidPDBQTWriter extends PrintWriter {

    private int atomSerial = 1;
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
        StringBuilder sb = new StringBuilder(90);
        appendLeft(sb, "ATOM", 6);                 // 1-6
        appendInt(sb, atomSerial++, 5);            // 7-11
        sb.append(' ');                            // 12
        appendLeft(sb, formatAtomName(atom.getName()), 4);         // 13-16
        sb.append(' ');                            // 17
        appendLeft(sb, residue.getName(), 3);      // 18-20
        sb.append(' ');                            // 21
        appendLeft(sb, residue.getChain(), 1);     // 22
        appendInt(sb, residue.getNumber(), 4);     // 23-26
        sb.append("    ");                         // 27-30
        appendFloat(sb, atom.getPosition().x(), 8, 3);
        appendFloat(sb, atom.getPosition().y(), 8, 3);
        appendFloat(sb, atom.getPosition().z(), 8, 3);
        appendFloat(sb, atom.getOccupancy(), 6, 2);
        appendFloat(sb, atom.getBFactor(), 6, 2);
        sb.append("    ");
        appendSignedFloat(sb, atom.getCharge(), 7, 4);
        sb.append(' ');
        String adType = atom.getAutoDockType() != null
                ? atom.getAutoDockType()
                : atom.getElement().getSymbol();
        appendRight(sb, adType, 2);
        sb.append(System.lineSeparator());
        return sb.toString();
    }

    private String formatAtomName(String name) {
        if (name == null) {
            return "    ";
        }
        if (name.length() == 1) {
            return " " + name + "  ";
        }
        if (name.length() == 2) {
            return " " + name + " ";
        }
        if (name.length() == 3) {
            return name + " ";
        }
        return name.substring(0, 4);
    }

    private void appendLeft(StringBuilder sb, String value, int width) {
        value = value == null ? "" : value;
        int len = Math.min(value.length(), width);
        sb.append(value, 0, len);
        sb.append(" ".repeat(width - len));
    }
    private static void appendRight(StringBuilder sb, String value, int width) {
        value = value == null ? "" : value;
        int len = Math.min(value.length(), width);
        sb.append(" ".repeat(width - len));
        sb.append(value, value.length() - len, value.length());
    }
    private static void appendInt(StringBuilder sb, int value, int width) {
        appendRight(sb, Integer.toString(value), width);
    }

    private void appendFloat(StringBuilder sb, double value, int width,
                             int precision) {
        String s = String.format(Locale.US,
                "%." + precision + "f", value);
        appendRight(sb, s, width);
    }

    private void appendSignedFloat(StringBuilder sb, double value, int width, int precision) {
        String s = String.format(Locale.US,
                "%+." + precision + "f",
                value);
        appendRight(sb, s, width);
    }

}
