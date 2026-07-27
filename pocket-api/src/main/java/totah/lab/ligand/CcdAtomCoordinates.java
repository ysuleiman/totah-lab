package totah.lab.ligand;

import totah.lab.protein.Point3D;

public record CcdAtomCoordinates(
        int atomIndex,
        Point3D modelPosition,
        Point3D idealPosition) {

    public CcdAtomCoordinates {
        if (atomIndex < 0) {
            throw new IllegalArgumentException("atomIndex must be non-negative");
        }
    }
}
