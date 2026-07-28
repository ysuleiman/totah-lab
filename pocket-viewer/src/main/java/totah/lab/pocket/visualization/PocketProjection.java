package totah.lab.pocket.visualization;

import totah.lab.protein.Point3D;

import java.util.Objects;

public final class PocketProjection {

    private PocketProjection() {
    }

    /**
     * Projects a 3D point onto a 2D slice plane.
     *
     * x and y are coordinates within the plane.
     * distanceFromPlane is the signed perpendicular distance.
     */
    public static ProjectedPoint project(
            Point3D point,
            SlicePlane plane) {

        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(plane, "plane");

        double relativeX =
                point.x() - plane.origin().x();

        double relativeY =
                point.y() - plane.origin().y();

        double relativeZ =
                point.z() - plane.origin().z();

        double x = dot(
                relativeX,
                relativeY,
                relativeZ,
                plane.axisX());

        double y = dot(
                relativeX,
                relativeY,
                relativeZ,
                plane.axisY());

        double distance = dot(
                relativeX,
                relativeY,
                relativeZ,
                plane.normal());

        return new ProjectedPoint(x, y, distance);
    }

    /**
     * Calculates the radius of the circle formed when a sphere intersects
     * a plane.
     */
    public static double sectionRadius(
            double sphereRadius,
            double distanceFromPlane) {

        if (sphereRadius <= 0.0) {
            return 0.0;
        }

        double squared =
                sphereRadius * sphereRadius
                        - distanceFromPlane * distanceFromPlane;

        return squared <= 0.0
                ? 0.0
                : Math.sqrt(squared);
    }

    /**
     * Cross-section radius for a slab rather than an infinitely thin plane.
     *
     * Atoms lying inside the slab receive their full radius. Atoms outside
     * the slab can still intersect its nearest face.
     */
    public static double slabSectionRadius(
            double sphereRadius,
            double distanceFromCenterPlane,
            double slabThickness) {

        double halfThickness =
                Math.max(0.0, slabThickness) / 2.0;

        double effectiveDistance =
                Math.max(
                        0.0,
                        Math.abs(distanceFromCenterPlane)
                                - halfThickness);

        return sectionRadius(
                sphereRadius,
                effectiveDistance);
    }

    public static Point3D pointOnPlane(
            SlicePlane plane,
            double planeX,
            double planeY,
            double normalOffset) {

        double x =
                plane.origin().x()
                        + plane.axisX().x() * planeX
                        + plane.axisY().x() * planeY
                        + plane.normal().x() * normalOffset;

        double y =
                plane.origin().y()
                        + plane.axisX().y() * planeX
                        + plane.axisY().y() * planeY
                        + plane.normal().y() * normalOffset;

        double z =
                plane.origin().z()
                        + plane.axisX().z() * planeX
                        + plane.axisY().z() * planeY
                        + plane.normal().z() * normalOffset;

        return new Point3D(x, y, z);
    }

    private static double dot(
            double x,
            double y,
            double z,
            PocketPca.Vector3D vector) {

        return x * vector.x()
                + y * vector.y()
                + z * vector.z();
    }

    public record SlicePlane(
            Point3D origin,
            PocketPca.Vector3D axisX,
            PocketPca.Vector3D axisY,
            PocketPca.Vector3D normal) {

        public SlicePlane {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(axisX, "axisX");
            Objects.requireNonNull(axisY, "axisY");
            Objects.requireNonNull(normal, "normal");

            axisX = PocketPca.normalize(axisX);
            axisY = PocketPca.normalize(axisY);
            normal = PocketPca.normalize(normal);
        }

        public SlicePlane moved(double distance) {
            Point3D movedOrigin = new Point3D(
                    origin.x() + normal.x() * distance,
                    origin.y() + normal.y() * distance,
                    origin.z() + normal.z() * distance);

            return new SlicePlane(
                    movedOrigin,
                    axisX,
                    axisY,
                    normal);
        }
    }

    public record ProjectedPoint(
            double x,
            double y,
            double distanceFromPlane) {
    }
}