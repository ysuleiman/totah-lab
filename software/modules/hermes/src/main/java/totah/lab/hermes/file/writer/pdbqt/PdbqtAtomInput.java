package totah.lab.hermes.file.writer.pdbqt;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

public record PdbqtAtomInput(
        int canonicalAtomIndex,
        int atomSerial,
        String atomName,
        String residueName,
        String chainId,
        int residueNumber,
        Character insertionCode,
        Point3D coordinates,
        double occupancy,
        double bFactor,
        double charge,
        String ad4Type) {
    public PdbqtAtomInput {
        if (canonicalAtomIndex < 0 || atomSerial < 1) throw new IllegalArgumentException("Invalid atom index or serial.");
        Objects.requireNonNull(atomName, "atomName"); Objects.requireNonNull(residueName, "residueName");
        Objects.requireNonNull(chainId, "chainId"); Objects.requireNonNull(coordinates, "coordinates");
        Objects.requireNonNull(ad4Type, "ad4Type");
        if (!Double.isFinite(charge)) throw new IllegalArgumentException("charge must be finite.");
    }
}
