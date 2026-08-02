package totah.lab.pocket.visualization.surface;

import totah.lab.gaia.geometry.Point3D;

import java.util.Arrays;
import java.util.Objects;

/**
 * Regular scalar grid used to derive a pocket boundary.
 */
public final class PocketField {
    private final Point3D origin;
    private final double spacing;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final double[] values;

    PocketField(
            Point3D origin,
            double spacing,
            int sizeX,
            int sizeY,
            int sizeZ,
            double[] values) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.spacing = spacing;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.values = Arrays.copyOf(values, values.length);
    }

    public Point3D origin() {
        return origin;
    }

    public double spacing() {
        return spacing;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public double value(int x, int y, int z) {
        return values[index(x, y, z)];
    }

    public Point3D point(int x, int y, int z) {
        return new Point3D(
                origin.x() + x * spacing,
                origin.y() + y * spacing,
                origin.z() + z * spacing);
    }

    private int index(int x, int y, int z) {
        if (x < 0 || x >= sizeX
                || y < 0 || y >= sizeY
                || z < 0 || z >= sizeZ) {
            throw new IndexOutOfBoundsException(
                    "Grid coordinate outside pocket field");
        }
        return (z * sizeY + y) * sizeX + x;
    }
}
