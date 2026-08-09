package totah.lab.hermes.file.pdbqt.internal;

import totah.lab.hermes.file.pdbqt.AtomRecordType;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtFormatException;

/** Parses the fixed-column ATOM/HETATM record shared by readers and validators. */
public final class PdbqtAtomParser {

    public PdbqtAtom parse(String line, int lineNumber) throws PdbqtFormatException {
        try {
            String record = field(line, 0, 6).trim();
            int serial = Integer.parseInt(field(line, 6, 11).trim());
            String atomName = field(line, 12, 16).trim();
            String residueName = field(line, 17, 20).trim();
            String chainId = field(line, 21, 22).trim();
            String residueNumberText = field(line, 22, 26).trim();
            Integer residueNumber = residueNumberText.isEmpty()
                    ? null
                    : Integer.parseInt(residueNumberText);
            String insertionCodeText = field(line, 26, 27).trim();
            Character insertionCode = insertionCodeText.isEmpty()
                    ? null
                    : insertionCodeText.charAt(0);
            double x = Double.parseDouble(field(line, 30, 38).trim());
            double y = Double.parseDouble(field(line, 38, 46).trim());
            double z = Double.parseDouble(field(line, 46, 54).trim());
            Double occupancy = optionalDouble(field(line, 54, 60).trim());
            Double temperatureFactor = optionalDouble(field(line, 60, 66).trim());
            Double charge = optionalDouble(field(line, 70, 76).trim());
            String autodockType = field(line, 77, line.length()).trim();

            if (charge == null || autodockType.isEmpty()) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length >= 2) {
                    if (charge == null) {
                        charge = optionalDouble(tokens[tokens.length - 2]);
                    }
                    if (autodockType.isEmpty()) {
                        autodockType = tokens[tokens.length - 1];
                    }
                }
            }
            if (charge == null) {
                throw error(lineNumber, "Missing PDBQT partial charge");
            }
            if (autodockType == null || autodockType.isBlank()) {
                throw error(lineNumber, "Missing AutoDock atom type");
            }
            return new PdbqtAtom(
                    "HETATM".equals(record) ? AtomRecordType.HETATM : AtomRecordType.ATOM,
                    serial, atomName, residueName, chainId, residueNumber, insertionCode,
                    x, y, z, occupancy, temperatureFactor, charge, autodockType);
        } catch (PdbqtFormatException e) {
            throw e;
        } catch (RuntimeException e) {
            throw error(lineNumber, "Could not parse atom record: " + line, e);
        }
    }

    private static String field(String value, int start, int end) {
        if (start >= value.length()) {
            return "";
        }
        return value.substring(start, Math.min(end, value.length()));
    }

    private static Double optionalDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static PdbqtFormatException error(int lineNumber, String message) {
        return new PdbqtFormatException("PDBQT line " + lineNumber + ": " + message);
    }

    private static PdbqtFormatException error(
            int lineNumber, String message, Throwable cause) {
        return new PdbqtFormatException(
                "PDBQT line " + lineNumber + ": " + message, cause);
    }
}
