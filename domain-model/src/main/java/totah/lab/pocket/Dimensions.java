package totah.lab.pocket;

/**
 * Immutable value representation of a 3D bounding box size vector in Ångströms.
 */
public record Dimensions(double widthX, double heightY, double depthZ) {

    /**
     * Calculates the true volume of the bounding box space envelope.
     */
    public double getBoundingVolume() {
        return widthX * heightY * depthZ;
    }
}
