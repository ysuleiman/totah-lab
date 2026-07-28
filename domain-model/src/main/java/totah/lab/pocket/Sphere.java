package totah.lab.pocket;

import totah.lab.protein.Point3D;

public record Sphere (long id, double x, double y, double z, double radius){
    public Point3D getPoint() {
        return new Point3D(x, y, z);
    }
}
