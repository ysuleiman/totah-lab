package totah.lab.athena.pocket.compare.residue;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;

import java.util.Objects;

/**
 * Calculates a representative point for a residue.
 *
 * Rules:
 *
 * <ul>
 *     <li>Glycine uses the CA atom.</li>
 *     <li>All other residues use the centroid of side-chain heavy atoms.</li>
 *     <li>If no side-chain heavy atoms exist, fall back to CA.</li>
 * </ul>
 */
public final class ResidueCentroidCalculator {

    public Point3D calculate(Residue residue) {
        Objects.requireNonNull(residue, "residue");

        if (isGlycine(residue)) {
            return alphaCarbon(residue).getPosition();
        }

        Point3D sideChain = sideChainCentroid(residue);

        if (sideChain != null) {
            return sideChain;
        }

        return alphaCarbon(residue).getPosition();
    }

    private static boolean isGlycine(Residue residue) {
        return "GLY".equalsIgnoreCase(residue.getName());
    }

    private static Point3D sideChainCentroid(Residue residue) {

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        int count = 0;

        for (Atom atom : residue.getAtoms()) {

            if (atom == null) {
                continue;
            }

            if (!atom.isHeavyAtom()) {
                continue;
            }

            if (isBackbone(atom)) {
                continue;
            }

            Point3D p = atom.getPosition();

            if (p == null) {
                continue;
            }

            x += p.x();
            y += p.y();
            z += p.z();

            count++;
        }

        if (count == 0) {
            return null;
        }

        return new Point3D(
                x / count,
                y / count,
                z / count
        );
    }

    private static Atom alphaCarbon(Residue residue) {

        return residue.findAtom("CA")
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Residue has no alpha carbon: "
                                        + residue.getName()
                                        + residue.getNumber()));
    }

    private static boolean isBackbone(Atom atom) {

        String name = atom.getName();

        return "N".equals(name)
                || "CA".equals(name)
                || "C".equals(name)
                || "O".equals(name)
                || "OXT".equals(name);
    }
}
