package totah.lab.protein;

public record Point3D(double x, double y, double z) {

    public double distance(Point3D other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public Point3D midpoint(Point3D other) {
        return new Point3D(
                (x + other.x) / 2.0,
                (y + other.y) / 2.0,
                (z + other.z) / 2.0
        );
    }

    public Point3D add(Point3D other) {
        return new Point3D(x + other.x, y + other.y, z + other.z);
    }

    public Point3D subtract(Point3D other) {
        return new Point3D(x - other.x, y - other.y, z - other.z);
    }

    public Point3D scale(double s) {
        return new Point3D(x * s, y * s, z * s);
    }

    @Override
    public String toString() {
        return String.format("(%.3f, %.3f, %.3f)", x, y, z);
    }
}