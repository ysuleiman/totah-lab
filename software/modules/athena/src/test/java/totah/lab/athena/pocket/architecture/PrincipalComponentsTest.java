package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class PrincipalComponentsTest {

    @Test
    void axisAlignedEllipsoidRecoversAxesAndEigenvalues() {
        List<Point3D> points = List.of(
                new Point3D(6, 0, 0),
                new Point3D(-6, 0, 0),
                new Point3D(0, 2, 0),
                new Point3D(0, -2, 0),
                new Point3D(0, 0, 1),
                new Point3D(0, 0, -1)
        );

        PrincipalComponents pca = PrincipalComponents.of(points);

        assertThat(pca.centroid()).isEqualTo(new Point3D(0, 0, 0));
        assertThat(pca.eigenvalues().get(0))
                .isCloseTo(12.0, offset(1.0e-9));
        assertThat(pca.eigenvalues().get(1))
                .isCloseTo(4.0 / 3.0, offset(1.0e-9));
        assertThat(pca.eigenvalues().get(2))
                .isCloseTo(1.0 / 3.0, offset(1.0e-9));

        assertVectorParallel(pca.axes().get(0), new Vector3D(1, 0, 0));
        assertVectorParallel(pca.axes().get(1), new Vector3D(0, 1, 0));
        assertVectorParallel(pca.axes().get(2), new Vector3D(0, 0, 1));
    }

    @Test
    void projectionsAreRelativeToTheCentroid() {
        List<Point3D> points = List.of(
                new Point3D(16, 10, 10),
                new Point3D(4, 10, 10),
                new Point3D(10, 12, 10),
                new Point3D(10, 8, 10),
                new Point3D(10, 10, 11),
                new Point3D(10, 10, 9)
        );

        PrincipalComponents pca = PrincipalComponents.of(points);

        assertThat(pca.centroid())
                .isEqualTo(new Point3D(10, 10, 10));
        assertThat(pca.projection(new Point3D(16, 10, 10), 0))
                .isCloseTo(6.0, offset(1.0e-9));
    }

    @Test
    void offsetIsTheDisplacementDotNormalizedPocketAxis() {
        PrincipalComponents pca = PrincipalComponents.of(List.of(
                new Point3D(6, 0, 0),
                new Point3D(-6, 0, 0),
                new Point3D(0, 2, 0),
                new Point3D(0, -2, 0),
                new Point3D(0, 0, 1),
                new Point3D(0, 0, -1)
        ));

        Point3D reference = new Point3D(10, 10, 10);
        Point3D point = new Point3D(12, 13, 10);

        assertThat(pca.offsetAlong(point, reference, 0))
                .isCloseTo(2.0, offset(1.0e-9));
        assertThat(pca.offsetAlong(point, reference, 1))
                .isCloseTo(3.0, offset(1.0e-9));
    }

    private static void assertVectorParallel(
            Vector3D actual,
            Vector3D expected
    ) {
        assertThat(Math.abs(actual.normalize().dot(expected)))
                .isCloseTo(1.0, offset(1.0e-9));
    }
}
