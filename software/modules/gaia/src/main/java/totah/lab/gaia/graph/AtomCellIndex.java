package totah.lab.gaia.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable uniform-cell index for neutral atom-pair distance queries. */
final class AtomCellIndex {

    private static final double CELL_SIZE_ANGSTROMS = 4.0;

    private final List<IndexedAtom> atoms;
    private final Map<Cell, List<IndexedAtom>> cells;

    AtomCellIndex(List<IndexedAtom> atoms) {
        this.atoms = List.copyOf(atoms);
        Map<Cell, List<IndexedAtom>> mutableCells = new HashMap<>();
        for (IndexedAtom atom : this.atoms) {
            mutableCells.computeIfAbsent(
                            cellOf(atom),
                            ignored -> new ArrayList<>())
                    .add(atom);
        }
        Map<Cell, List<IndexedAtom>> immutableCells = new HashMap<>();
        mutableCells.forEach((cell, entries) ->
                immutableCells.put(cell, List.copyOf(entries)));
        this.cells = Map.copyOf(immutableCells);
    }

    List<IndexedAtomPair> pairsWithin(double cutoffAngstroms) {
        double cutoffSquared = cutoffAngstroms * cutoffAngstroms;
        int reach = (int) Math.ceil(
                cutoffAngstroms / CELL_SIZE_ANGSTROMS);
        List<IndexedAtomPair> result = new ArrayList<>();

        for (IndexedAtom first : atoms) {
            Cell origin = cellOf(first);
            for (List<IndexedAtom> candidates : candidateCells(
                    origin,
                    reach)) {
                for (IndexedAtom second : candidates) {
                    if (second.ordinal() <= first.ordinal()
                            || first.residueId().equals(
                                    second.residueId())) {
                        continue;
                    }
                    double distanceSquared = first.position()
                            .distanceSquared(second.position());
                    if (distanceSquared <= cutoffSquared) {
                        result.add(new IndexedAtomPair(
                                first,
                                second,
                                Math.sqrt(distanceSquared)));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private List<List<IndexedAtom>> candidateCells(
            Cell origin,
            int reach) {

        long width = 2L * reach + 1L;
        long neighborhoodVolume = width > 2_000_000L
                ? Long.MAX_VALUE
                : width * width * width;
        if (neighborhoodVolume >= cells.size()) {
            return cells.entrySet().stream()
                    .filter(entry -> entry.getKey().within(origin, reach))
                    .map(Map.Entry::getValue)
                    .toList();
        }

        List<List<IndexedAtom>> candidates = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    List<IndexedAtom> cell = cells.get(new Cell(
                            origin.x() + dx,
                            origin.y() + dy,
                            origin.z() + dz));
                    if (cell != null) {
                        candidates.add(cell);
                    }
                }
            }
        }
        return candidates;
    }

    private static Cell cellOf(IndexedAtom atom) {
        return new Cell(
                coordinate(atom.position().x()),
                coordinate(atom.position().y()),
                coordinate(atom.position().z()));
    }

    private static int coordinate(double value) {
        return (int) Math.floor(value / CELL_SIZE_ANGSTROMS);
    }

    private record Cell(int x, int y, int z) {

        boolean within(Cell other, int reach) {
            return Math.abs((long) x - other.x) <= reach
                    && Math.abs((long) y - other.y) <= reach
                    && Math.abs((long) z - other.z) <= reach;
        }
    }
}
