package totah.lab.hephaestus.receptor.hydrogen;


import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;

import java.util.*;

public class SpatialClashChecker {
    private final double gridSize;
    private final Map<String, List<Atom>> grid = new HashMap<>();

    public SpatialClashChecker(double gridSize) {
        this.gridSize = gridSize;
    }

    private String getGridKey(Point3D pos) {
        int x = (int) Math.floor(pos.x() / gridSize);
        int y = (int) Math.floor(pos.y() / gridSize);
        int z = (int) Math.floor(pos.z() / gridSize);
        return x + "," + y + "," + z;
    }

    public void addAtom(Atom atom) {
        String key = getGridKey(atom.getPosition());
        grid.computeIfAbsent(key, k -> new ArrayList<>()).add(atom);
    }

    public void addAll(Collection<Atom> atoms) {
        atoms.forEach(this::addAtom);
    }

    /**
     * Checks if a target candidate position overlaps too closely with existing elements
     * @param position Calculated point vector
     * @param minDistance Cutoff threshold (e.g., 1.0 Å to 1.2 Å)
     */
    public boolean hasClash(Point3D position, double minDistance) {
        return hasClash(position, minDistance, null);
    }

    /**
     * As {@link #hasClash(Point3D, double)}, but ignores one specific atom
     * (e.g., the parent heavy atom a freshly built hydrogen is bonded to —
     * an N-H bond of 1.01 Å must not count as a clash with its own nitrogen).
     */
    public boolean hasClash(Point3D position, double minDistance, Atom exclude) {
        int centerX = (int) Math.floor(position.x() / gridSize);
        int centerY = (int) Math.floor(position.y() / gridSize);
        int centerZ = (int) Math.floor(position.z() / gridSize);

        // A competitor minDistance away can sit ceil(minDistance/gridSize) voxels out;
        // scanning only +/-1 voxel misses clashes when minDistance > gridSize.
        int shells = Math.max(1, (int) Math.ceil(minDistance / gridSize));

        // Scan neighboring voxels
        for (int dx = -shells; dx <= shells; dx++) {
            for (int dy = -shells; dy <= shells; dy++) {
                for (int dz = -shells; dz <= shells; dz++) {
                    String key = (centerX + dx) + "," + (centerY + dy) + "," + (centerZ + dz);
                    List<Atom> bucket = grid.get(key);
                    if (bucket == null) continue;

                    for (Atom competitor : bucket) {
                        if (competitor == exclude) continue;
                        if (position.distance(competitor.getPosition()) < minDistance) {
                            return true; // Steric collision hit!
                        }
                    }
                }
            }
        }
        return false;
    }
}
