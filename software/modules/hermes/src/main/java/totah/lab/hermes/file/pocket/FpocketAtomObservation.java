package totah.lab.hermes.file.pocket;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/** Atom emitted in an fpocket pocket atom file. */
public record FpocketAtomObservation(String sourceAtomId, String groupPdb,
        String atomName,
        String element, String authAsymId, int residueNumber,
        String insertionCode, String residueName, Point3D position) {
    public FpocketAtomObservation {
        Objects.requireNonNull(sourceAtomId);
        Objects.requireNonNull(groupPdb);
        Objects.requireNonNull(atomName);
        Objects.requireNonNull(element);
        Objects.requireNonNull(authAsymId);
        Objects.requireNonNull(residueName);
        Objects.requireNonNull(position);
    }

    /** Backward-compatible constructor for callers providing polymer atoms. */
    public FpocketAtomObservation(String sourceAtomId, String atomName,
            String element, String authAsymId, int residueNumber,
            String insertionCode, String residueName, Point3D position) {
        this(sourceAtomId, "ATOM", atomName, element, authAsymId,
                residueNumber, insertionCode, residueName, position);
    }
}
