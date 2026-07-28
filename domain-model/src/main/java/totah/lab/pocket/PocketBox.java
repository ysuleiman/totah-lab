package totah.lab.pocket;

import totah.lab.protein.Point3D;

import java.util.Objects;

public final class PocketBox {
        private final Point3D min;
        private final Point3D max;

        public PocketBox(Point3D min, Point3D max) {
            this.min = Objects.requireNonNull(min, "min");
            this.max = Objects.requireNonNull(max, "max");

            if (max.x() < min.x()
                    || max.y() < min.y()
                    || max.z() < min.z()) {
                throw new IllegalArgumentException(
                        "Maximum coordinates must be greater than or equal to minimum coordinates.");
            }
        }

        public Point3D getMin() {
            return min;
        }

        public Point3D getMax() {
            return max;
        }

        public Point3D getCenter() {
            return new Point3D(
                    (min.x() + max.x()) / 2.0,
                    (min.y() + max.y()) / 2.0,
                    (min.z() + max.z()) / 2.0
            );
        }

        public double getSizeX() {
            return max.x() - min.x();
        }

        public double getSizeY() {
            return max.y() - min.y();
        }

        public double getSizeZ() {
            return max.z() - min.z();
        }

        public double getVolume() {
            return getSizeX() * getSizeY() * getSizeZ();
        }

        public boolean contains(Point3D point) {
            Objects.requireNonNull(point, "point");

            return point.x() >= min.x()
                    && point.x() <= max.x()
                    && point.y() >= min.y()
                    && point.y() <= max.y()
                    && point.z() >= min.z()
                    && point.z() <= max.z();
        }

        @Override
        public String toString() {
            return "PocketBox{" +
                    "min=" + min +
                    ", max=" + max +
                    ", center=" + getCenter() +
                    ", sizeX=" + getSizeX() +
                    ", sizeY=" + getSizeY() +
                    ", sizeZ=" + getSizeZ() +
                    '}';
        }
    }
