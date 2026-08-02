package totah.lab.athena.pocket.geometry;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

public interface PocketGeometryStrategy {

    BoundingBox bounds(Structure structure, Pocket pocket);

    Point3D centroid(Structure structure, Pocket pocket);

    PocketGeometryBasis basis();
}
