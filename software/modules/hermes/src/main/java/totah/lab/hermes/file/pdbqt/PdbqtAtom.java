package totah.lab.hermes.file.pdbqt;

import totah.lab.gaia.geometry.Point3D;

public record PdbqtAtom(
        AtomRecordType recordType,
        int serial,
        String atomName,
        String residueName,
        String chainId,
        Integer residueNumber,
        Character insertionCode,
        double x,
        double y,
        double z,
        Double occupancy,
        Double temperatureFactor,
        double partialCharge,
        String autodockType
) {

    /**
     * The atom position as a gaia point.
     */
    public Point3D position() {
        return new Point3D(x, y, z);
    }

    /**
     * The chemical element behind the AutoDock atom type (A is
     * aromatic carbon; NA/OA/SA map to N/O/S; H/HD/HS are hydrogen;
     * everything else is the type itself, e.g. Cl, Br, F, I).
     */
    public String element() {
        return switch (autodockType) {
            case "A" -> "C";
            case "NA" -> "N";
            case "OA" -> "O";
            case "SA" -> "S";
            case "H", "HD", "HS" -> "H";
            default -> autodockType;
        };
    }

    public boolean hydrogen() {
        return "H".equals(element());
    }
}
