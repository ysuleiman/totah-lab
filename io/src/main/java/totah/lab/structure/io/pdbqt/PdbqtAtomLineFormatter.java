package totah.lab.structure.io.pdbqt;

import totah.lab.protein.Atom;
import totah.lab.protein.Residue;

import java.util.Locale;

final class PdbqtAtomLineFormatter {

    String format(Residue residue, Atom atom, int serial) {
        StringBuilder sb = new StringBuilder(90);
        appendLeft(sb, "ATOM", 6);
        appendInt(sb, serial, 5);
        sb.append(' ');
        appendLeft(sb, formatAtomName(atom.getName()), 4);
        sb.append(' ');
        appendLeft(sb, residue.getName(), 3);
        sb.append(' ');
        appendLeft(sb, residue.getChain(), 1);
        appendInt(sb, residue.getNumber(), 4);
        sb.append("    ");
        appendFloat(sb, atom.getPosition().x(), 8, 3);
        appendFloat(sb, atom.getPosition().y(), 8, 3);
        appendFloat(sb, atom.getPosition().z(), 8, 3);
        appendFloat(sb, atom.getOccupancy(), 6, 2);
        appendFloat(sb, atom.getBFactor(), 6, 2);
        sb.append("    ");
        appendSignedFloat(sb, atom.getCharge(), 7, 4);
        sb.append(' ');
        appendRight(sb, atom.getAutoDockType(), 2);
        sb.append(System.lineSeparator());
        return sb.toString();
    }

    private String formatAtomName(String name) {
        if (name == null) return "    ";
        if (name.length() == 1) return " " + name + "  ";
        if (name.length() == 2) return " " + name + " ";
        if (name.length() == 3) return name + " ";
        return name.substring(0, 4);
    }

    private void appendLeft(StringBuilder sb, String value, int width) {
        value = value == null ? "" : value;
        int length = Math.min(value.length(), width);
        sb.append(value, 0, length);
        sb.append(" ".repeat(width - length));
    }

    private void appendRight(StringBuilder sb, String value, int width) {
        value = value == null ? "" : value;
        int length = Math.min(value.length(), width);
        sb.append(" ".repeat(width - length));
        sb.append(value, value.length() - length, value.length());
    }

    private void appendInt(StringBuilder sb, int value, int width) {
        appendRight(sb, Integer.toString(value), width);
    }

    private void appendFloat(StringBuilder sb, double value, int width, int precision) {
        appendRight(sb, String.format(Locale.US, "%." + precision + "f", value), width);
    }

    private void appendSignedFloat(
            StringBuilder sb, double value, int width, int precision) {
        appendRight(sb, String.format(Locale.US, "%+." + precision + "f", value), width);
    }
}
