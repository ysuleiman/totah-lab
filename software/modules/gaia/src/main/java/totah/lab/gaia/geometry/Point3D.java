package totah.lab.gaia.geometry;

import java.util.Objects;

public record Point3D(
        double x,
        double y,
        double z) implements Tuple3D {

    public Point3D {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    public double distance(Point3D other) {
        return vectorTo(other).magnitude();
    }

    public double distanceSquared(Point3D other) {
        Objects.requireNonNull(other, "other");

        double dx = other.x() - x;
        double dy = other.y() - y;
        double dz = other.z() - z;

        return dx * dx + dy * dy + dz * dz;
    }

    public Point3D midpoint(Point3D other) {
        Objects.requireNonNull(other, "other");

        return new Point3D(
                (x + other.x()) / 2.0,
                (y + other.y()) / 2.0,
                (z + other.z()) / 2.0
        );
    }

    public Point3D add(Vector3D vector) {
        Objects.requireNonNull(vector, "vector");

        return new Point3D(
                x + vector.x(),
                y + vector.y(),
                z + vector.z()
        );
    }

    public Point3D subtract(Vector3D vector) {
        Objects.requireNonNull(vector, "vector");

        return new Point3D(
                x - vector.x(),
                y - vector.y(),
                z - vector.z()
        );
    }

    /**
     * Returns the vector pointing from this point to {@code other}.
     */
    public Vector3D vectorTo(Point3D other) {
        Objects.requireNonNull(other, "other");

        return new Vector3D(
                other.x() - x,
                other.y() - y,
                other.z() - z
        );
    }

    /**
     * Returns the vector pointing from {@code other} to this point.
     */
    public Vector3D vectorFrom(Point3D other) {
        Objects.requireNonNull(other, "other");

        return new Vector3D(
                x - other.x(),
                y - other.y(),
                z - other.z()
        );
    }

    @Override
    public String toString() {
        return String.format(
                "(%.3f, %.3f, %.3f)",
                x,
                y,
                z
        );
    }

    private static void requireFinite(
            double value,
            String fieldName) {

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite.");
        }
    }
}