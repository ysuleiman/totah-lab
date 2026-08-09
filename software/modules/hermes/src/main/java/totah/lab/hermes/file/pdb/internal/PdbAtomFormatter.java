package totah.lab.hermes.file.pdb.internal;

import totah.lab.gaia.geometry.Point3D;

import java.util.Locale;

/**
 * Fixed-column PDB ATOM record formatter: record name, serial, atom
 * name (4-character field; names shorter than four characters start in
 * column 14, the single-letter-element convention), residue name,
 * chain, residue number, insertion code, coordinates, occupancy,
 * B-factor and the right-justified element symbol in columns 77-78.
 * No charge or AutoDock columns.
 */
public final class PdbAtomFormatter {

    public String format(int serial, String atomName, String residueName,
            String chainId, int residueNumber, Character insertionCode,
            Point3D position, double occupancy, double bFactor,
            String elementSymbol) {
        return format("ATOM", serial, atomName, residueName, chainId,
                residueNumber, insertionCode, position, occupancy, bFactor,
                elementSymbol);
    }

    public String format(String recordName, int serial, String atomName,
            String residueName, String chainId, int residueNumber,
            Character insertionCode, Point3D position, double occupancy,
            double bFactor, String elementSymbol) {
        if (!"ATOM".equals(recordName) && !"HETATM".equals(recordName)) {
            throw new IllegalArgumentException(
                    "PDB atom record must be ATOM or HETATM: "
                            + recordName);
        }
        StringBuilder line = new StringBuilder(80);
        left(line, recordName, 6);
        integer(line, serial, 5);
        line.append(' ');
        line.append(atomName(atomName, elementSymbol));
        line.append(' ');
        left(line, residueName, 3);
        line.append(' ');
        left(line, chainId, 1);
        integer(line, residueNumber, 4);
        line.append(insertionCode == null ? ' ' : insertionCode);
        line.append("   ");
        decimal(line, position.x(), 8);
        decimal(line, position.y(), 8);
        decimal(line, position.z(), 8);
        decimal(line, occupancy, 6);
        decimal(line, bFactor, 6);
        line.append("          ");
        right(line, elementSymbol == null ? "" : elementSymbol, 2);
        line.append(System.lineSeparator());
        return line.toString();
    }

    /**
     * Atom-name placement in columns 13-16, element-aware: four-
     * character names fill the field; names of atoms with a two-letter
     * element symbol are left-justified from column 13; all other
     * names start in column 14 (the single-letter-element convention).
     */
    private String atomName(String value, String elementSymbol) {
        if (value == null) return "    ";
        if (value.length() >= 4) return value.substring(0, 4);
        if (elementSymbol != null && elementSymbol.length() == 2) {
            return value + " ".repeat(4 - value.length());
        }
        return switch (value.length()) {
            case 1 -> " " + value + "  ";
            case 2 -> " " + value + " ";
            case 3 -> " " + value;
            default -> throw new IllegalArgumentException(
                    "Atom name must not be blank");
        };
    }

    private void decimal(StringBuilder out, double value, int width) {
        String formatted = switch (width) {
            case 6 -> String.format(Locale.US, "%.2f", value);
            case 8 -> String.format(Locale.US, "%.3f", value);
            default -> throw new IllegalArgumentException(
                    "Unsupported decimal width: " + width);
        };
        right(out, formatted, width);
    }

    private void left(StringBuilder out, String value, int width) {
        value = value == null ? "" : value;
        int length = Math.min(value.length(), width);
        out.append(value, 0, length);
        out.append(" ".repeat(width - length));
    }

    private void right(StringBuilder out, String value, int width) {
        value = value == null ? "" : value;
        if (value.length() > width) {
            throw new IllegalArgumentException(
                    "Value does not fit PDB field of width " + width
                            + ": " + value);
        }
        out.append(" ".repeat(width - value.length()));
        out.append(value);
    }

    private void integer(StringBuilder out, int value, int width) {
        right(out, Integer.toString(value), width);
    }
}
