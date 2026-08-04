package totah.lab.athena.pocket.compare;


import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KabschRigidPointAlignerTest {

    private static final double TOLERANCE = 1.0e-8;

    private final KabschRigidPointAligner aligner =
            new KabschRigidPointAligner();

    @Test
    void recoversRotationAndTranslation() {
        List<Point3D> source = List.of(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(1.0, 0.0, 0.0),
                new Point3D(0.0, 1.0, 0.0),
                new Point3D(0.0, 0.0, 1.0)
        );

        /*
         * Rotate 90 degrees around Z:
         *
         * (x, y, z) -> (-y, x, z)
         *
         * then translate by (10, 20, 30).
         */
        List<Point3D> target = List.of(
                new Point3D(10.0, 20.0, 30.0),
                new Point3D(10.0, 21.0, 30.0),
                new Point3D(9.0, 20.0, 30.0),
                new Point3D(10.0, 20.0, 31.0)
        );

        RigidTransform transform =
                aligner.align(source, target);

        for (int index = 0; index < source.size(); index++) {
            Point3D actual =
                    transform.apply(source.get(index));

            Point3D expected =
                    target.get(index);

            assertEquals(expected.x(), actual.x(), TOLERANCE);
            assertEquals(expected.y(), actual.y(), TOLERANCE);
            assertEquals(expected.z(), actual.z(), TOLERANCE);
        }
    }

    @Test
    void identityInputProducesIdentityMapping() {
        List<Point3D> points = List.of(
                new Point3D(-2.0, 1.0, 0.0),
                new Point3D(3.0, -1.0, 2.0),
                new Point3D(0.0, 4.0, -3.0),
                new Point3D(1.0, 2.0, 5.0)
        );

        RigidTransform transform =
                aligner.align(points, points);

        for (Point3D point : points) {
            Point3D actual = transform.apply(point);

            assertEquals(point.x(), actual.x(), TOLERANCE);
            assertEquals(point.y(), actual.y(), TOLERANCE);
            assertEquals(point.z(), actual.z(), TOLERANCE);
        }
    }
}
