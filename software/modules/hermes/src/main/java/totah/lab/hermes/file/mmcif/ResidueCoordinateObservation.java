package totah.lab.hermes.file.mmcif;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;
import java.util.Optional;

/** Source-observed representative coordinates of one polymer residue. */
public record ResidueCoordinateObservation(
        String authAsymId,
        int authSequenceId,
        String insertionCode,
        String residueName,
        Point3D ca,
        Optional<Point3D> sideChainCentroid) {
    public ResidueCoordinateObservation {
        Objects.requireNonNull(authAsymId);
        Objects.requireNonNull(insertionCode);
        Objects.requireNonNull(residueName);
        Objects.requireNonNull(ca);
        Objects.requireNonNull(sideChainCentroid);
    }
}
