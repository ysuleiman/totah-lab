package totah.lab.hermes.file.pdbqt;

import totah.lab.gaia.geometry.Point3D;

/**
 * Connects a canonical prepared-structure atom index to the shared
 * PDBQT format atom used by both readers and writers. The index is
 * mapping metadata and is never serialized.
 */
public record PdbqtAtomReference(
        int canonicalAtomIndex,
        PdbqtAtom atom) {

    public PdbqtAtomReference {
        if (canonicalAtomIndex < 0) {
            throw new IllegalArgumentException("canonicalAtomIndex must not be negative.");
        }
        java.util.Objects.requireNonNull(atom, "atom");
    }

    public PdbqtAtomReference(
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
        this(canonicalAtomIndex, new PdbqtAtom(
                AtomRecordType.ATOM,
                atomSerial,
                atomName,
                residueName,
                chainId,
                residueNumber,
                insertionCode,
                coordinates.x(),
                coordinates.y(),
                coordinates.z(),
                occupancy,
                bFactor,
                charge,
                ad4Type));
    }

    public int atomSerial() { return atom.serial(); }
    public String atomName() { return atom.atomName(); }
    public String residueName() { return atom.residueName(); }
    public String chainId() { return atom.chainId(); }
    public int residueNumber() { return atom.residueNumber(); }
    public Character insertionCode() { return atom.insertionCode(); }
    public Point3D coordinates() { return atom.position(); }
    public double occupancy() { return atom.occupancy(); }
    public double bFactor() { return atom.temperatureFactor(); }
    public double charge() { return atom.partialCharge(); }
    public String ad4Type() { return atom.autodockType(); }
}
