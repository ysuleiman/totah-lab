package totah.lab.protein.hydrogenation;

import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.topology.SpatialClashChecker;
import totah.lab.topology.ZMatrixMath;

import java.util.List;

/**
 * Pure geometry: atom factories, methyl/methylene/ammonium builders,
 * aromatic H placement, and clash-guarded insertion.
 */
public final class HydrogenationGeometry {

    private HydrogenationGeometry() {}

    // Bond lengths (Å)
    public static final double BOND_C_H_SP3 = 1.09;
    public static final double BOND_C_H_SP2 = 1.08;
    public static final double BOND_N_H_SP3 = 1.01;
    public static final double BOND_N_H_SP2 = 1.00;
    public static final double BOND_O_H = 0.96;
    public static final double BOND_S_H = 1.34;
    public static final double BOND_C_OXT = 1.25;

    // Angles (degrees)
    public static final double ANGLE_SP3 = 109.5;
    public static final double ANGLE_SP2 = 120.0;
    public static final double ANGLE_N_H_SP2 = 119.8;
    public static final double ANGLE_O_H = 108.5;

    // -------------------- atom factories --------------------

    public static Atom hAtom(String name, Point3D pos, double bFactor) {
        return Atom.builder()
                .name(name).position(pos).charge(0.0).occupancy(1.0).bFactor(bFactor)
                .element(Element.builder()
                        .symbol("H").atomicNumber(1).atomicMass(1.008)
                        .covalentRadius(0.31).vdwRadius(1.20).build())
                .build();
    }

    public static Atom oxtAtom(String name, Point3D pos, double bFactor) {
        return Atom.builder()
                .name(name).position(pos).charge(0.0).occupancy(1.0).bFactor(bFactor)
                .element(Element.builder()
                        .symbol("O").atomicNumber(8).atomicMass(15.999)
                        .covalentRadius(0.73).vdwRadius(1.52).build())
                .build();
    }

    public static void tryAdd(List<Atom> atoms, Atom h, SpatialClashChecker checker, double cutoff) {
        tryAdd(atoms, h, null, checker, cutoff);
    }

    /**
     * Clash-guarded insertion. The parent heavy atom the new atom is bonded to
     * is excluded from the clash check — otherwise a perfect N-H at 1.01 Å is
     * rejected as a "clash" with its own nitrogen whenever cutoff >= bond length.
     */
    public static void tryAdd(List<Atom> atoms, Atom h, Atom parent, SpatialClashChecker checker, double cutoff) {
        if (h == null || h.getPosition() == null) return;
        if (!checker.hasClash(h.getPosition(), cutoff, parent)) {
            atoms.add(h);
            checker.addAtom(h);
        } else {
            System.err.println("[ReceptorHydrogenation] Dropped " + h.getName()
                    + " at " + h.getPosition() + " (clash within " + cutoff + " Å)");
        }
    }

    static Point3D tetrahedralFourthPosition(Atom center, Atom a1, Atom a2, Atom a3, double bondLength) {
        if (center == null || a1 == null || a2 == null || a3 == null) return null;
        Point3D centerPos = center.getPosition();
        Point3D sum = normalize(a1.getPosition().subtract(centerPos))
                .add(normalize(a2.getPosition().subtract(centerPos)))
                .add(normalize(a3.getPosition().subtract(centerPos)));
        Point3D direction = normalize(sum).scale(-1.0);
        if (direction.x() == 0.0 && direction.y() == 0.0 && direction.z() == 0.0) return null;
        return centerPos.add(direction.scale(bondLength));
    }

    // -------------------- aliphatic geometry --------------------

    public static void addMethyl(Atom center, Atom a1, Atom a2, String prefix,
                                 List<Atom> atoms, SpatialClashChecker c, double cutoff) {
        if (center == null || a1 == null || a2 == null) return;
        double[] dihedrals = {Math.toRadians(60), Math.toRadians(180), Math.toRadians(-60)};
        String[] names = {prefix + "1", prefix + "2", prefix + "3"};
        for (int i = 0; i < 3; i++) {
            Point3D h = ZMatrixMath.calculatePosition(
                    center.getPosition(), a1.getPosition(), a2.getPosition(),
                    BOND_C_H_SP3, Math.toRadians(ANGLE_SP3), dihedrals[i]);
            tryAdd(atoms, hAtom(names[i], h, center.getBFactor()), center, c, cutoff);
        }
    }

    public static void addMethylene(Atom center, Atom a1, Atom a2, String prefix,
                                    List<Atom> atoms, SpatialClashChecker c, double cutoff) {
        if (center == null || a1 == null || a2 == null) return;
        double[] dihedrals = {Math.toRadians(120), Math.toRadians(-120)};
        String[] names = {prefix + "2", prefix + "3"};
        for (int i = 0; i < 2; i++) {
            Point3D h = ZMatrixMath.calculatePosition(
                    center.getPosition(), a1.getPosition(), a2.getPosition(),
                    BOND_C_H_SP3, Math.toRadians(ANGLE_SP3), dihedrals[i]);
            tryAdd(atoms, hAtom(names[i], h, center.getBFactor()), center, c, cutoff);
        }
    }

    public static void addPlanarNH2(Atom center, Atom a1, Atom a2, String prefix,
                                    List<Atom> atoms, SpatialClashChecker c, double cutoff) {
        if (center == null || a1 == null || a2 == null) return;
        double[] dihedrals = {Math.toRadians(0), Math.toRadians(180)};
        String[] names = {prefix + "1", prefix + "2"};
        for (int i = 0; i < 2; i++) {
            Point3D h = ZMatrixMath.calculatePosition(
                    center.getPosition(), a1.getPosition(), a2.getPosition(),
                    BOND_N_H_SP2, Math.toRadians(ANGLE_SP2), dihedrals[i]);
            tryAdd(atoms, hAtom(names[i], h, center.getBFactor()), center, c, cutoff);
        }
    }

    public static void addAmideNH2(Atom center, Atom a1, Atom a2, String prefix,
                                   List<Atom> atoms, SpatialClashChecker c, double cutoff) {
        addPlanarNH2(center, a1, a2, prefix, atoms, c, cutoff);
    }

    public static void addAmmonium(Atom center, Atom a1, Atom a2, String prefix,
                                   List<Atom> atoms, SpatialClashChecker c, double cutoff) {
        if (center == null || a1 == null || a2 == null) return;
        double[] dihedrals = {Math.toRadians(60), Math.toRadians(180), Math.toRadians(-60)};
        String[] names = {prefix + "1", prefix + "2", prefix + "3"};
        for (int i = 0; i < 3; i++) {
            Point3D h = ZMatrixMath.calculatePosition(
                    center.getPosition(), a1.getPosition(), a2.getPosition(),
                    BOND_N_H_SP3, Math.toRadians(ANGLE_SP3), dihedrals[i]);
            tryAdd(atoms, hAtom(names[i], h, center.getBFactor()), center, c, cutoff);
        }
    }

    public static void addSecondaryAmmonium(Atom center, Atom a1, Atom a2, String prefix,
                                            List<Atom> atoms, SpatialClashChecker c, double cutoff) {
        if (center == null || a1 == null || a2 == null) return;
        double[] dihedrals = {Math.toRadians(120), Math.toRadians(-120)};
        String[] names = {prefix + "1", prefix + "2"};
        for (int i = 0; i < 2; i++) {
            Point3D h = ZMatrixMath.calculatePosition(
                    center.getPosition(), a1.getPosition(), a2.getPosition(),
                    BOND_N_H_SP3, Math.toRadians(ANGLE_SP3), dihedrals[i]);
            tryAdd(atoms, hAtom(names[i], h, center.getBFactor()), center, c, cutoff);
        }
    }

    // -------------------- aromatic geometry --------------------

    public static Atom aromaticH(String name, Atom carbon, Atom n1, Atom n2) {
        if (carbon == null || n1 == null || n2 == null) return null;
        Point3D c = carbon.getPosition();
        Point3D v1 = normalize(sub(n1.getPosition(), c));
        Point3D v2 = normalize(sub(n2.getPosition(), c));
        Point3D bisector = normalize(new Point3D(v1.x() + v2.x(), v1.y() + v2.y(), v1.z() + v2.z()));
        Point3D hPos = new Point3D(
                c.x() - bisector.x() * BOND_C_H_SP2,
                c.y() - bisector.y() * BOND_C_H_SP2,
                c.z() - bisector.z() * BOND_C_H_SP2);
        return hAtom(name, hPos, carbon.getBFactor());
    }

    public static Atom aromaticH5Ring(String name, Atom carbon, Atom n1, Atom n2) {
        return aromaticH(name, carbon, n1, n2);
    }

    public static void addAromaticRing(Residue r, List<Atom> atoms,
                                       SpatialClashChecker c, double cutoff) {
        Atom cb = r.getAtom("CB");
        Atom ca = r.getAtom("CA");
        Atom cg = r.getAtom("CG");
        if (cb != null && ca != null && cg != null) {
            addMethylene(cb, ca, cg, "HB", atoms, c, cutoff);
        }

        String[][] ringAtoms = {
                {"CD1", "CG", "CE1", "HD1"},
                {"CD2", "CG", "CE2", "HD2"},
                {"CE1", "CD1", "CZ", "HE1"},
                {"CE2", "CD2", "CZ", "HE2"},
                {"CZ", "CE1", "CE2", "HZ"}
        };
        for (String[] q : ringAtoms) {
            Atom carbon = r.getAtom(q[0]);
            Atom a1 = r.getAtom(q[1]);
            Atom a2 = r.getAtom(q[2]);
            if (carbon != null && a1 != null && a2 != null) {
                tryAdd(atoms, aromaticH(q[3], carbon, a1, a2), carbon, c, cutoff);
            }
        }
    }

    // -------------------- vector math --------------------

    private static Point3D sub(Point3D a, Point3D b) {
        return new Point3D(a.x() - b.x(), a.y() - b.y(), a.z() - b.z());
    }

    private static Point3D normalize(Point3D v) {
        double len = Math.sqrt(v.x()*v.x() + v.y()*v.y() + v.z()*v.z());
        if (len < 1e-12) return new Point3D(0, 0, 0);
        return new Point3D(v.x()/len, v.y()/len, v.z()/len);
    }
}
