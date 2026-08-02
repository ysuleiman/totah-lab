package totah.lab.gaia.geometry;

import java.util.Objects;

/**
 * Utilities for converting molecular internal coordinates
 * into Cartesian coordinates.
 */
public final class ZMatrixMath {

    private static final double VECTOR_EPSILON = 1.0e-10;
    private static final double COLLINEAR_EPSILON = 1.0e-8;

    private ZMatrixMath() {
    }

    /**
     * Calculates the Cartesian position of atom D from three existing atoms.
     *
     * <pre>
     * D -- A -- B -- C
     * </pre>
     *
     * @param positionA atom to which D is attached
     * @param positionB atom bonded to A
     * @param positionC atom defining the reference plane with A and B
     * @param bondLength D-A distance in angstroms
     * @param bondAngle D-A-B angle in radians
     * @param dihedral D-A-B-C dihedral angle in radians
     * @return Cartesian position of atom D
     */
    public static Point3D calculatePosition(
            Point3D positionA,
            Point3D positionB,
            Point3D positionC,
            double bondLength,
            double bondAngle,
            double dihedral) {

        Objects.requireNonNull(positionA, "positionA");
        Objects.requireNonNull(positionB, "positionB");
        Objects.requireNonNull(positionC, "positionC");

        validateBondLength(bondLength);
        validateFinite(bondAngle, "bondAngle");
        validateFinite(dihedral, "dihedral");

        /*
         * Unit vector along B -> A.
         */
        Vector3D axis =
                normalize(
                        positionB.vectorTo(positionA),
                        "positionA and positionB must be distinct");

        /*
         * Vector along C -> B.
         */
        Vector3D reference =
                positionC.vectorTo(positionB);

        Vector3D normal =
                axis.cross(reference);

        Vector3D planeNormal;

        if (normal.magnitude() < COLLINEAR_EPSILON) {
            planeNormal = perpendicularUnitVector(axis);
        } else {
            planeNormal =
                    normalize(
                            normal,
                            "Unable to construct Z-matrix plane normal");
        }

        /*
         * Completes the right-handed orthonormal frame.
         */
        Vector3D inPlane =
                normalize(
                        planeNormal.cross(axis),
                        "Unable to construct Z-matrix in-plane axis");

        /*
         * In this frame, reference atom C lies at azimuth PI.
         */
        double azimuth = dihedral + Math.PI;

        double axial =
                -bondLength * Math.cos(bondAngle);

        double radial =
                bondLength * Math.sin(bondAngle);

        Vector3D displacement =
                axis.scale(axial)
                        .add(
                                inPlane.scale(
                                        radial * Math.cos(azimuth)))
                        .add(
                                planeNormal.scale(
                                        radial * Math.sin(azimuth)));

        return positionA.add(displacement);
    }

    private static Vector3D perpendicularUnitVector(
            Vector3D vector) {

        Vector3D candidateAxis =
                leastAlignedCartesianAxis(vector);

        return normalize(
                vector.cross(candidateAxis),
                "Unable to construct a perpendicular vector");
    }

    private static Vector3D leastAlignedCartesianAxis(
            Vector3D vector) {

        double absX = Math.abs(vector.x());
        double absY = Math.abs(vector.y());
        double absZ = Math.abs(vector.z());

        if (absX <= absY && absX <= absZ) {
            return new Vector3D(1.0, 0.0, 0.0);
        }

        if (absY <= absX && absY <= absZ) {
            return new Vector3D(0.0, 1.0, 0.0);
        }

        return new Vector3D(0.0, 0.0, 1.0);
    }

    private static Vector3D normalize(
            Vector3D vector,
            String errorMessage) {

        double length = vector.magnitude();

        if (!Double.isFinite(length)
                || length < VECTOR_EPSILON) {
            throw new IllegalArgumentException(errorMessage);
        }

        return vector.scale(1.0 / length);
    }

    private static void validateBondLength(
            double bondLength) {

        if (!Double.isFinite(bondLength)
                || bondLength <= 0.0) {
            throw new IllegalArgumentException(
                    "bondLength must be finite and positive.");
        }
    }

    private static void validateFinite(
            double value,
            String fieldName) {

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite.");
        }
    }
}