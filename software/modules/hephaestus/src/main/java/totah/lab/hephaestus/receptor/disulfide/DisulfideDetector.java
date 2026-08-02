package totah.lab.hephaestus.receptor.disulfide;



import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;

import java.util.*;
import java.lang.reflect.Method;

/**
 * Standalone, stateless disulfide bond detector.
 */
public final class DisulfideDetector {

    private DisulfideDetector() {}

    public static Set<Residue> findDisulfideBonds(List<Residue> residues, double cutoff) {
        Set<Residue> disulfideCys = new HashSet<>();
        List<Residue> cysList = new ArrayList<>();
        Map<Residue, Integer> indexMap = new HashMap<>();

        for (int i = 0; i < residues.size(); i++) {
            indexMap.put(residues.get(i), i);
            Residue r = residues.get(i);
            if ("CYS".equals(r.getName()) && r.findAtom("SG").isPresent()) {
                cysList.add(r);
            }
        }

        List<SgPair> candidates = new ArrayList<>();
        for (int i = 0; i < cysList.size(); i++) {
            Residue c1 = cysList.get(i);
            Atom sg1 = c1.findAtom("SG").orElseThrow();
            for (int j = i + 1; j < cysList.size(); j++) {
                Residue c2 = cysList.get(j);
                Atom sg2 = c2.findAtom("SG").orElseThrow();
                if (!altLocsCompatible(sg1, sg2)) continue;
                double d = distance(sg1.getPosition(), sg2.getPosition());
                if (d <= cutoff) {
                    candidates.add(new SgPair(c1, c2, d, indexMap.get(c1), indexMap.get(c2)));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(p -> p.dist));
        Set<Residue> used = new HashSet<>();
        for (SgPair p : candidates) {
            if (used.contains(p.cys1) || used.contains(p.cys2)) continue;
            disulfideCys.add(p.cys1);
            disulfideCys.add(p.cys2);
            used.add(p.cys1);
            used.add(p.cys2);
            System.out.println("  Disulfide: index " + p.idx1 + " <-> index " + p.idx2 +
                    " (SG-SG: " + String.format("%.2f", p.dist) + " Å)");
        }
        return disulfideCys;
    }

    private static boolean altLocsCompatible(Atom a1, Atom a2) {
        String al1 = getAltLoc(a1);
        String al2 = getAltLoc(a2);
        if (al1 == null || al2 == null) return true;
        if (al1.isBlank() || al2.isBlank()) return true;
        return al1.equals(al2);
    }

    private static String getAltLoc(Atom atom) {
        try {
            Method m = atom.getClass().getMethod("getAltLoc");
            Object r = m.invoke(atom);
            return r != null ? r.toString() : " ";
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static double distance(Point3D a, Point3D b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private record SgPair(Residue cys1, Residue cys2, double dist, int idx1, int idx2) {}
}
