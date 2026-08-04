package totah.lab.athena.pocket.compare.residue;

import totah.lab.gaia.geometry.RigidTransform;

import java.util.List;
import java.util.Objects;

/**
 * Applies a rigid transform to pocket residue points, producing new
 * points with transformed positions while preserving residue
 * references, chemistry classes and input order.
 */
public final class PocketResiduePointTransformer {

    public List<PocketResiduePoint> transform(
            List<PocketResiduePoint> points,
            RigidTransform transform
    ) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(transform, "transform");

        return points
                .stream()
                .map(point -> new PocketResiduePoint(
                        point.reference(),
                        transform.apply(point.position()),
                        point.chemistry()
                ))
                .toList();
    }
}
