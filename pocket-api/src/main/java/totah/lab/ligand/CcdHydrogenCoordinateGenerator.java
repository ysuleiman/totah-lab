package totah.lab.ligand;

import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;
import totah.lab.protein.Point3D;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CcdHydrogenCoordinateGenerator {

    private static final double EPSILON = 1.0e-10;

    public Point3D generate(
            CcdLigandGraphResult graphResult,
            MissingLigandHydrogen hydrogen) {
        Objects.requireNonNull(graphResult, "graphResult is null");
        Objects.requireNonNull(hydrogen, "hydrogen is null");

        MolecularGraph graph = graphResult.graph();
        Map<Integer, CcdAtomCoordinates> coordinates = new HashMap<>();
        for (CcdAtomCoordinates coordinate : graphResult.depositedCcdCoordinates()) {
            coordinates.put(coordinate.atomIndex(), coordinate);
        }

        List<Integer> candidates = nearbyHeavyAtoms(graph, hydrogen.parentAtomIndex());
        Point3D generated = generateWithCoordinateSet(
                graph, coordinates, candidates, hydrogen, true);
        if (generated == null) {
            generated = generateWithCoordinateSet(
                    graph, coordinates, candidates, hydrogen, false);
        }
        if (generated == null) {
            throw new IllegalArgumentException(
                    "Cannot construct a non-collinear CCD coordinate frame for hydrogen "
                            + hydrogen.ccdAtomId());
        }
        return generated;
    }

    private Point3D generateWithCoordinateSet(
            MolecularGraph graph,
            Map<Integer, CcdAtomCoordinates> coordinates,
            List<Integer> candidates,
            MissingLigandHydrogen hydrogen,
            boolean ideal) {
        Point3D sourceOrigin = ccdPosition(
                coordinates.get(hydrogen.parentAtomIndex()), ideal);
        Point3D sourceHydrogen = ideal
                ? hydrogen.idealPosition()
                : hydrogen.modelPosition();
        if (sourceOrigin == null || sourceHydrogen == null) {
            return null;
        }
        Point3D targetOrigin = graph.atoms().get(
                hydrogen.parentAtomIndex()).getPosition();
        for (int first = 0; first < candidates.size(); first++) {
            for (int second = first + 1; second < candidates.size(); second++) {
                int firstIndex = candidates.get(first);
                int secondIndex = candidates.get(second);
                Point3D sourceFirst = ccdPosition(coordinates.get(firstIndex), ideal);
                Point3D sourceSecond = ccdPosition(coordinates.get(secondIndex), ideal);
                if (sourceFirst == null || sourceSecond == null) {
                    continue;
                }
                Point3D targetFirst = graph.atoms().get(firstIndex).getPosition();
                Point3D targetSecond = graph.atoms().get(secondIndex).getPosition();
                Frame sourceFrame = frame(sourceOrigin, sourceFirst, sourceSecond);
                Frame targetFrame = frame(targetOrigin, targetFirst, targetSecond);
                if (sourceFrame == null || targetFrame == null) {
                    continue;
                }
                Point3D local = sourceFrame.toLocal(sourceHydrogen);
                return targetFrame.toWorld(local);
            }
        }
        return null;
    }

    private List<Integer> nearbyHeavyAtoms(MolecularGraph graph, int parentIndex) {
        List<List<Integer>> adjacency = adjacency(graph);
        boolean[] visited = new boolean[graph.atoms().size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(parentIndex);
        visited[parentIndex] = true;
        List<Integer> candidates = new ArrayList<>();
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            List<Integer> neighbors = new ArrayList<>(adjacency.get(current));
            neighbors.sort(Comparator.naturalOrder());
            for (int neighbor : neighbors) {
                if (visited[neighbor]) {
                    continue;
                }
                visited[neighbor] = true;
                queue.addLast(neighbor);
                if (!isHydrogen(graph, neighbor)) {
                    candidates.add(neighbor);
                }
            }
        }
        return candidates;
    }

    private List<List<Integer>> adjacency(MolecularGraph graph) {
        List<List<Integer>> adjacency = new ArrayList<>(graph.atoms().size());
        for (int index = 0; index < graph.atoms().size(); index++) {
            adjacency.add(new ArrayList<>());
        }
        for (ChemicalBond bond : graph.bonds()) {
            adjacency.get(bond.atomIndexA()).add(bond.atomIndexB());
            adjacency.get(bond.atomIndexB()).add(bond.atomIndexA());
        }
        return adjacency;
    }

    private boolean isHydrogen(MolecularGraph graph, int atomIndex) {
        return graph.atoms().get(atomIndex).getElement() != null
                && "H".equalsIgnoreCase(
                        graph.atoms().get(atomIndex).getElement().getSymbol());
    }

    private Point3D ccdPosition(CcdAtomCoordinates coordinates, boolean ideal) {
        if (coordinates == null) {
            return null;
        }
        return ideal ? coordinates.idealPosition() : coordinates.modelPosition();
    }

    private Frame frame(Point3D origin, Point3D first, Point3D second) {
        Point3D axisX = normalize(first.subtract(origin));
        if (axisX == null) {
            return null;
        }
        Point3D secondVector = second.subtract(origin);
        Point3D orthogonal = secondVector.subtract(axisX.scale(dot(secondVector, axisX)));
        Point3D axisY = normalize(orthogonal);
        if (axisY == null) {
            return null;
        }
        Point3D axisZ = cross(axisX, axisY);
        return new Frame(origin, axisX, axisY, axisZ);
    }

    private Point3D normalize(Point3D vector) {
        double length = Math.sqrt(dot(vector, vector));
        return length > EPSILON ? vector.scale(1.0 / length) : null;
    }

    private double dot(Point3D first, Point3D second) {
        return first.x() * second.x()
                + first.y() * second.y()
                + first.z() * second.z();
    }

    private Point3D cross(Point3D first, Point3D second) {
        return new Point3D(
                first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }

    private record Frame(
            Point3D origin,
            Point3D axisX,
            Point3D axisY,
            Point3D axisZ) {

        Point3D toLocal(Point3D point) {
            Point3D vector = point.subtract(origin);
            return new Point3D(
                    dot(vector, axisX),
                    dot(vector, axisY),
                    dot(vector, axisZ));
        }

        Point3D toWorld(Point3D local) {
            return origin
                    .add(axisX.scale(local.x()))
                    .add(axisY.scale(local.y()))
                    .add(axisZ.scale(local.z()));
        }

        private double dot(Point3D first, Point3D second) {
            return first.x() * second.x()
                    + first.y() * second.y()
                    + first.z() * second.z();
        }
    }
}
