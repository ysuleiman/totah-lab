package totah.lab.athena.pocket.compare;


import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.List;

public interface RigidPointAligner {

    /**
     * Finds the rigid transform mapping source points onto target points.
     */
    RigidTransform align(
            List<Point3D> source,
            List<Point3D> target
    );
}
