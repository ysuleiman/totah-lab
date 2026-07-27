package totah.lab.pocket;

public class PocketBox {
    private double sizeX;
    private double sizeY;
    private double sizeZ;
    private double[] center;

    public PocketBox(double[] center, double sizeX, double sizeY, double sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        center = center;
    }
    public double getSizeX() {
        return sizeX;
    }
    public double getSizeY() {
        return sizeY;
    }
    public double getSizeZ() {
        return sizeZ;
    }
}
