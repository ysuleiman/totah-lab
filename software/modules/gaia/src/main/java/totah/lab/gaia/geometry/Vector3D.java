package totah.lab.gaia.geometry;

import java.util.Objects;

public record Vector3D(
        double x,
        double y,
        double z) implements Tuple3D {

    public static final Vector3D ZERO = new Vector3D(0.0, 0.0, 0.0);
    private static final double ZERO_TOLERANCE = 1.0e-12;

    public Vector3D {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    public Vector3D add(Vector3D other) {
        Objects.requireNonNull(other, "other");

        return new Vector3D(
                x + other.x(),
                y + other.y(),
                z + other.z()
        );
    }

    public Vector3D subtract(Vector3D other) {
        Objects.requireNonNull(other, "other");

        return new Vector3D(
                x - other.x(),
                y - other.y(),
                z - other.z()
        );
    }

    public Vector3D scale(double scalar) {
        requireFinite(scalar, "scalar");

        return new Vector3D(
                x * scalar,
                y * scalar,
                z * scalar
        );
    }

    public Vector3D multiply(double scalar) {
        return scale(scalar);
    }

    public Vector3D divide(double divisor) {
        requireFinite(divisor, "divisor");
        if (divisor == 0.0) {
            throw new IllegalArgumentException("divisor must not be zero.");
        }
        return scale(1.0 / divisor);
    }

    public Vector3D negate() {
        return scale(-1.0);
    }

    public double dot(Vector3D other) {
        Objects.requireNonNull(other, "other");

        return x * other.x()
                + y * other.y()
                + z * other.z();
    }

    public Vector3D cross(Vector3D other) {
        Objects.requireNonNull(other, "other");

        return new Vector3D(
                y * other.z() - z * other.y(),
                z * other.x() - x * other.z(),
                x * other.y() - y * other.x()
        );
    }

    public double magnitudeSquared() {
        return x * x + y * y + z * z;
    }

    public double magnitude() {
        return Math.sqrt(magnitudeSquared());
    }

    public Vector3D normalize() {
        double magnitude = magnitude();

        if (magnitude < 1.0e-12) {
            throw new IllegalStateException(
                    "Cannot normalize a zero-length vector.");
        }

        return scale(1.0 / magnitude);
    }

    public double angle(Vector3D other) {
        Objects.requireNonNull(other, "other");
        double denominator = magnitude() * other.magnitude();
        if (denominator < ZERO_TOLERANCE) {
            throw new IllegalStateException(
                    "Cannot calculate an angle using a zero-length vector.");
        }
        double cosine = Math.max(-1.0, Math.min(1.0, dot(other) / denominator));
        return Math.acos(cosine);
    }

    public double distance(Vector3D other) {
        return subtract(Objects.requireNonNull(other, "other")).magnitude();
    }

    public boolean isZero() {
        return magnitudeSquared() < ZERO_TOLERANCE * ZERO_TOLERANCE;
    }

    public static Vector3D between(Point3D first, Point3D second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return first.vectorTo(second);
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
