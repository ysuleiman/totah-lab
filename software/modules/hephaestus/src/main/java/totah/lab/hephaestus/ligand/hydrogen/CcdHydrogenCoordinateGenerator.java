package totah.lab.hephaestus.ligand.hydrogen;

import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.hephaestus.ligand.topology.CcdAtomCoordinates;
import totah.lab.hephaestus.ligand.topology.LigandTopology;
import totah.lab.hephaestus.ligand.topology.MissingLigandHydrogen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CcdHydrogenCoordinateGenerator {
    private static final double EPSILON = 1.0e-10;

    public Point3D generate(
            List<Atom> atoms, LigandTopology topology, MissingLigandHydrogen hydrogen) {
        Map<Integer, CcdAtomCoordinates> coordinates = new HashMap<>();
        topology.ccdCoordinates().forEach(value -> coordinates.put(value.atomIndex(), value));
        List<Integer> candidates = nearbyHeavyAtoms(atoms, topology.bonds(),
                hydrogen.parentAtomIndex());
        Point3D result = generate(atoms, coordinates, candidates, hydrogen, true);
        if (result == null) {
            result = generate(atoms, coordinates, candidates, hydrogen, false);
        }
        if (result == null) {
            throw new IllegalArgumentException(
                    "Cannot construct CCD coordinate frame for hydrogen " + hydrogen.atomName());
        }
        return result;
    }

    private Point3D generate(
            List<Atom> atoms,
            Map<Integer, CcdAtomCoordinates> coordinates,
            List<Integer> candidates,
            MissingLigandHydrogen hydrogen,
            boolean ideal) {
        Point3D sourceOrigin = ccdPosition(coordinates.get(hydrogen.parentAtomIndex()), ideal);
        Point3D sourceHydrogen = ideal ? hydrogen.idealPosition() : hydrogen.modelPosition();
        if (sourceOrigin == null || sourceHydrogen == null) {
            return null;
        }
        Point3D targetOrigin = atoms.get(hydrogen.parentAtomIndex()).getPosition();
        for (int first = 0; first < candidates.size(); first++) {
            for (int second = first + 1; second < candidates.size(); second++) {
                int firstIndex = candidates.get(first);
                int secondIndex = candidates.get(second);
                Point3D sourceFirst = ccdPosition(coordinates.get(firstIndex), ideal);
                Point3D sourceSecond = ccdPosition(coordinates.get(secondIndex), ideal);
                if (sourceFirst == null || sourceSecond == null) {
                    continue;
                }
                Frame sourceFrame = frame(sourceOrigin, sourceFirst, sourceSecond);
                Frame targetFrame = frame(targetOrigin, atoms.get(firstIndex).getPosition(),
                        atoms.get(secondIndex).getPosition());
                if (sourceFrame != null && targetFrame != null) {
                    return targetFrame.toWorld(sourceFrame.toLocal(sourceHydrogen));
                }
            }
        }
        return null;
    }

    private List<Integer> nearbyHeavyAtoms(
            List<Atom> atoms, List<ChemicalBond> bonds, int parentIndex) {
        List<List<Integer>> adjacency = new ArrayList<>();
        for (int index = 0; index < atoms.size(); index++) {
            adjacency.add(new ArrayList<>());
        }
        for (ChemicalBond bond : bonds) {
            adjacency.get(bond.atomIndexA()).add(bond.atomIndexB());
            adjacency.get(bond.atomIndexB()).add(bond.atomIndexA());
        }
        boolean[] visited = new boolean[atoms.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(parentIndex);
        visited[parentIndex] = true;
        List<Integer> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            List<Integer> neighbors = new ArrayList<>(adjacency.get(current));
            neighbors.sort(Comparator.naturalOrder());
            for (int neighbor : neighbors) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.addLast(neighbor);
                    if (!atoms.get(neighbor).isHydrogen()) {
                        result.add(neighbor);
                    }
                }
            }
        }
        return result;
    }

    private Point3D ccdPosition(CcdAtomCoordinates coordinates, boolean ideal) {
        return coordinates == null ? null
                : ideal ? coordinates.idealPosition() : coordinates.modelPosition();
    }

    private Frame frame(Point3D origin, Point3D first, Point3D second) {
        Vector3D x = normalize(origin.vectorTo(first));
        if (x == null) return null;
        Vector3D vector = origin.vectorTo(second);
        Vector3D y = normalize(vector.subtract(x.scale(vector.dot(x))));
        return y == null ? null : new Frame(origin, x, y, cross(x, y));
    }

    private Vector3D normalize(Vector3D vector) {
        double length = vector.magnitude();
        return length > EPSILON ? vector.scale(1.0 / length) : null;
    }

    private Vector3D cross(Vector3D a, Vector3D b) {
        return a.cross(b);
    }

    private record Frame(Point3D origin, Vector3D x, Vector3D y, Vector3D z) {
        Point3D toLocal(Point3D point) {
            Vector3D vector = origin.vectorTo(point);
            return new Point3D(vector.dot(x), vector.dot(y), vector.dot(z));
        }
        Point3D toWorld(Point3D local) {
            return origin.add(x.scale(local.x())).add(y.scale(local.y())).add(z.scale(local.z()));
        }
    }
}
