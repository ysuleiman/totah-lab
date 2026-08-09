package totah.lab.web.docking;

import totah.lab.euclid.spatial.SimpleKDTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Residue-level contact roll-up between a docked pose and the pocket
 * atoms of one pocket: every pose-atom/pocket-atom pair within
 * {@value #CUTOFF_ANGSTROM} Å is a contact; per canonical residue the
 * pair count and the minimum distance are kept. This matches the
 * semantics of the historical pose_residue_contact materialized view
 * (count of contacting atom pairs, min pair distance).
 */
public final class PoseContactCalculator {

    public static final double CUTOFF_ANGSTROM = 4.0;

    /** One pocket atom with its canonical residue id. */
    public record PocketAtomPoint(double x, double y, double z,
                                  long residueId) {
    }

    /** Contact roll-up for one residue, sorted output by residue id. */
    public record ResidueContact(long residueId, int atomContactCount,
                                 double minDistance) {
    }

    private PoseContactCalculator() {
    }

    /**
     * @param poseAtomCoordinates pose atom coordinates (x, y, z)
     * @param pocketAtoms pocket atoms of the pocket the run docked into
     */
    public static List<ResidueContact> compute(
            List<double[]> poseAtomCoordinates,
            List<PocketAtomPoint> pocketAtoms
    ) {
        if (poseAtomCoordinates.isEmpty() || pocketAtoms.isEmpty()) {
            return List.of();
        }

        SimpleKDTree<Long> tree = new SimpleKDTree<>(3);
        List<double[]> points = new ArrayList<>(pocketAtoms.size());
        List<Long> residueIds = new ArrayList<>(pocketAtoms.size());
        for (PocketAtomPoint atom : pocketAtoms) {
            points.add(new double[]{atom.x(), atom.y(), atom.z()});
            residueIds.add(atom.residueId());
        }
        tree.build(points, residueIds);

        Map<Long, int[]> counts = new TreeMap<>();
        Map<Long, Double> minima = new TreeMap<>();
        for (double[] poseAtom : poseAtomCoordinates) {
            for (SimpleKDTree.Result<Long> hit :
                    tree.rangeSearch(poseAtom, CUTOFF_ANGSTROM)) {
                counts.computeIfAbsent(hit.value(), key -> new int[1])[0]++;
                minima.merge(hit.value(), hit.distance(), Math::min);
            }
        }

        List<ResidueContact> contacts = new ArrayList<>(counts.size());
        counts.forEach((residueId, count) -> contacts.add(
                new ResidueContact(residueId, count[0],
                        minima.get(residueId))));
        return List.copyOf(contacts);
    }
}
