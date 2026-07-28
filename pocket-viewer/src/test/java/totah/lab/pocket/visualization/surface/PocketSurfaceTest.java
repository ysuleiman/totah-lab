package totah.lab.pocket.visualization.surface;

import org.junit.jupiter.api.Test;
import totah.lab.pocket.Sphere;
import totah.lab.protein.Point3D;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PocketSurfaceTest {
    @Test
    void fieldIsPositiveInsideSphereAndNegativeOutside() {
        PocketField field = PocketFieldBuilder.fromAlphaSpheres(
                List.of(new Sphere(1, 0, 0, 0, 2.0)),
                0.5);

        assertThat(nearestValue(field, new Point3D(0, 0, 0)))
                .isPositive();
        assertThat(field.value(0, 0, 0)).isNegative();
    }

    @Test
    void extractsOneDeduplicatedClosedSurface() {
        PocketField field = PocketFieldBuilder.fromAlphaSpheres(
                List.of(new Sphere(1, 0, 0, 0, 2.0)),
                0.4);

        TriangleMesh mesh = MarchingCubes.extract(field, 0.0);

        assertThat(mesh.vertices()).isNotEmpty();
        assertThat(mesh.triangleCount()).isGreaterThan(100);
        for (int index : mesh.indices()) {
            assertThat(index)
                    .isBetween(0, mesh.vertices().size() - 1);
        }
        assertThat(mesh.vertices()).allSatisfy(point ->
                assertThat(distanceFromOrigin(point))
                        .isBetween(1.85, 2.05));
        assertThat(mesh.vertices().size())
                .isLessThan(mesh.indices().length);
    }

    @Test
    void rejectsInvalidFieldInputs() {
        assertThatThrownBy(() ->
                PocketFieldBuilder.fromAlphaSpheres(List.of(), 0.4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                PocketFieldBuilder.fromAlphaSpheres(
                        List.of(new Sphere(1, 0, 0, 0, 1.0)), 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static double nearestValue(
            PocketField field,
            Point3D target) {
        int x = (int) Math.round(
                (target.x() - field.origin().x()) / field.spacing());
        int y = (int) Math.round(
                (target.y() - field.origin().y()) / field.spacing());
        int z = (int) Math.round(
                (target.z() - field.origin().z()) / field.spacing());
        return field.value(x, y, z);
    }

    private static double distanceFromOrigin(Point3D point) {
        return Math.sqrt(
                point.x() * point.x()
                        + point.y() * point.y()
                        + point.z() * point.z());
    }
}
