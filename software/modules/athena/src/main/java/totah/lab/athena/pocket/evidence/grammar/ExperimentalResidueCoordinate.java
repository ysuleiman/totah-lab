package totah.lab.athena.pocket.evidence.grammar;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;
import java.util.Optional;

/** Experimental representative coordinates for one mapped target residue. */
public record ExperimentalResidueCoordinate(
        Point3D ca,
        Optional<Point3D> sideChainCentroid) {
    public ExperimentalResidueCoordinate {
        Objects.requireNonNull(ca);
        Objects.requireNonNull(sideChainCentroid);
    }
}
