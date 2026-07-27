package totah.lab.topology;

import totah.lab.protein.Point3D;

public class ZMatrixMath {

    /**
     * Calculates the 3D position of an atom (D) attached to an existing atom (A),
     * using the positions of two other connected anchor atoms (B and C).
     *
     * @param posA The center atom to attach atom D to (e.g., Nitrogen)
     * @param posB Atom directly connected to A (e.g., C-Alpha)
     * @param posC Atom connected to B (e.g., Carbonyl C of previous residue)
     * @param bondLength Distance from A to D (in Angstroms)
     * @param bondAngle Angle D-A-B (in radians)
     * @param dihedral Dihedral angle D-A-B-C (in radians)
     * @return The absolute 3D position of atom D
     */
    public static Point3D calculatePosition(Point3D posA, Point3D posB, Point3D posC,
                                            double bondLength, double bondAngle, double dihedral) {
        Point3D vBA = posA.subtract(posB);
        double distBA = magnitude(vBA);
        if (distBA < 1e-6) return posA;

        Point3D u = vBA.scale(1.0 / distBA);
        Point3D vCB = posB.subtract(posC);

        Point3D vPlaneNormal = crossProduct(u, vCB);
        double normalMag = magnitude(vPlaneNormal);

        Point3D w;
        if (normalMag < 1e-5) {
            Point3D fallbackAxis = (Math.abs(u.x()) < 0.9) ? new Point3D(1, 0, 0) : new Point3D(0, 1, 0);
            Point3D rawW = crossProduct(u, fallbackAxis);
            double rawMag = magnitude(rawW);
            w = (rawMag < 1e-12) ? new Point3D(0, 0, 1) : rawW.scale(1.0 / rawMag);
        } else {
            w = vPlaneNormal.scale(1.0 / normalMag);
        }

        Point3D v = crossProduct(w, u);

        // Standard dihedral convention: delta = 0 builds cis/eclipsed (D on the
        // same side of the A-B axis as C), delta = 180 deg builds trans/anti.
        // In the built (v,w) frame the reference atom C sits at azimuth PI,
        // hence the + PI shift below.
        double effectiveDihedral = dihedral + Math.PI;
        double x = -bondLength * Math.cos(bondAngle);
        double y =  bondLength * Math.sin(bondAngle) * Math.cos(effectiveDihedral);
        double z =  bondLength * Math.sin(bondAngle) * Math.sin(effectiveDihedral);


        Point3D displacement = u.scale(x).add(v.scale(y)).add(w.scale(z));
        return posA.add(displacement);
    }

    private static double magnitude(Point3D v) {
        return Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
    }

    private static Point3D crossProduct(Point3D a, Point3D b) {
        return new Point3D(
                a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x()
        );
    }
}
