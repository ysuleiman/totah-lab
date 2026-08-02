package totah.lab.hephaestus.ligand.topology;

import totah.lab.gaia.geometry.Point3D;

public record CcdAtomCoordinates(
        int atomIndex,
        Point3D modelPosition,
        Point3D idealPosition) {

    public CcdAtomCoordinates {
        if (atomIndex < 0) {
            throw new IllegalArgumentException("atomIndex must not be negative.");
        }
    }
}
