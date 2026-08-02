package totah.lab.gaia.geometry;


import java.util.Objects;

import java.util.Objects;

public record BoundingBox(
        Point3D min,
        Point3D max) {

    public static final BoundingBox EMPTY =
            new BoundingBox(
                    new Point3D(0.0, 0.0, 0.0),
                    new Point3D(0.0, 0.0, 0.0));

    public BoundingBox {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");

        if (min.x() > max.x()
                || min.y() > max.y()
                || min.z() > max.z()) {
            throw new IllegalArgumentException(
                    "Minimum coordinates must not exceed maximum coordinates.");
        }
    }

    public Point3D center() {
        return min.midpoint(max);
    }

    public double width() {
        return max.x() - min.x();
    }

    public double height() {
        return max.y() - min.y();
    }

    public double depth() {
        return max.z() - min.z();
    }

    public double volume() {
        if (isEmpty()) {
            return 0.0;
        }

        return width() * height() * depth();
    }

    public boolean isEmpty() {
        return width() <= 0.0
                || height() <= 0.0
                || depth() <= 0.0;
    }

    public boolean contains(Point3D point) {
        Objects.requireNonNull(point, "point");

        if (isEmpty()) {
            return false;
        }

        return point.x() >= min.x()
                && point.x() <= max.x()
                && point.y() >= min.y()
                && point.y() <= max.y()
                && point.z() >= min.z()
                && point.z() <= max.z();
    }

    public boolean intersects(BoundingBox other) {
        Objects.requireNonNull(other, "other");

        if (isEmpty() || other.isEmpty()) {
            return false;
        }

        return min.x() < other.max.x()
                && max.x() > other.min.x()
                && min.y() < other.max.y()
                && max.y() > other.min.y()
                && min.z() < other.max.z()
                && max.z() > other.min.z();
    }

    public BoundingBox intersection(BoundingBox other) {
        Objects.requireNonNull(other, "other");

        if (!intersects(other)) {
            return EMPTY;
        }

        return new BoundingBox(
                new Point3D(
                        Math.max(min.x(), other.min.x()),
                        Math.max(min.y(), other.min.y()),
                        Math.max(min.z(), other.min.z())),
                new Point3D(
                        Math.min(max.x(), other.max.x()),
                        Math.min(max.y(), other.max.y()),
                        Math.min(max.z(), other.max.z())));
    }

    public double intersectionVolume(BoundingBox other) {
        Objects.requireNonNull(other, "other");

        return intersection(other).volume();
    }

    public double intersectionOverUnion(BoundingBox other) {
        Objects.requireNonNull(other, "other");

        double intersectionVolume = intersectionVolume(other);
        double unionVolume =
                volume() + other.volume() - intersectionVolume;

        if (unionVolume <= 0.0) {
            return 0.0;
        }

        return intersectionVolume / unionVolume;
    }

    public BoundingBox expand(double margin) {
        if (!Double.isFinite(margin)) {
            throw new IllegalArgumentException("margin must be finite.");
        }

        if (margin < 0.0) {
            throw new IllegalArgumentException(
                    "margin must be non-negative.");
        }

        if (isEmpty()) {
            return EMPTY;
        }

        return new BoundingBox(
                new Point3D(
                        min.x() - margin,
                        min.y() - margin,
                        min.z() - margin),
                new Point3D(
                        max.x() + margin,
                        max.y() + margin,
                        max.z() + margin));
    }

    public BoundingBox union(BoundingBox other) {
        Objects.requireNonNull(other, "other");

        if (isEmpty()) {
            return other;
        }

        if (other.isEmpty()) {
            return this;
        }

        return new BoundingBox(
                new Point3D(
                        Math.min(min.x(), other.min.x()),
                        Math.min(min.y(), other.min.y()),
                        Math.min(min.z(), other.min.z())),
                new Point3D(
                        Math.max(max.x(), other.max.x()),
                        Math.max(max.y(), other.max.y()),
                        Math.max(max.z(), other.max.z())));
    }
}
