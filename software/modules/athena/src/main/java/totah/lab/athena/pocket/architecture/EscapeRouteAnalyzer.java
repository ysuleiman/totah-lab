package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Pocket escape/connectivity analysis on a clearance grid, ported
 * from the validated stage8_11 structural-design script
 * ({@code analysis/mettl7-closure/stage8_11_design/run_structural_design.py}).
 *
 * <p>The clearance field is the stage8_11 definition: at each grid
 * point, the minimum over occupancy spheres of
 * {@code distance(center) - radius} (+infinity with no spheres). The
 * grid spans the region-point bounds plus
 * {@code options.regionMarginAngstroms()}, snapped outward to the
 * grid spacing as in {@code build_grid()}.</p>
 *
 * <p>The solvent-accessible destination is the stage8_11 criterion:
 * cells on the outer faces of the grid box
 * ({@code boundary_indices()}). A voxel is passable when its
 * clearance is at least {@code options.probeRadiusAngstroms()}.</p>
 *
 * <p>Connectivity choices: the flood fill from the explicit origin
 * and the connected-component labeling are 26-connected (the task
 * requirement and the stage8_11 volume-component connectivity);
 * stage8_11's {@code reachable()} itself used 6-connectivity seeded
 * from the boundary. The widest path is an exact port of
 * {@code widest_path()}: 6-connected maximum-bottleneck Dijkstra to
 * the first boundary cell popped, equal capacities (within 1e-12)
 * broken by fewer steps. Unlike the Python, which raises when no
 * exterior path exists, the path is always reported; the
 * classification records whether a probe-clear route exists.</p>
 *
 * <p>The origin maps to the nearest grid cell (exact half-cell ties
 * resolve to the lower index). This is simpler than the stage8_11
 * {@code target_index()}, which picked the highest-clearance point
 * within 2.0 A of a target coordinate.</p>
 *
 * <p>All measurements are O(cells x spheres); no spatial index is
 * used, mirroring the validated Python implementation. Output is
 * deterministic.</p>
 */
public final class EscapeRouteAnalyzer {

    private static final double CAPACITY_TOLERANCE = 1.0e-12;

    private final EscapeRouteOptions options;

    public EscapeRouteAnalyzer() {
        this(EscapeRouteOptions.defaults());
    }

    public EscapeRouteAnalyzer(EscapeRouteOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * @param regionPoints points defining the pocket region; the grid
     *        covers their bounds plus the configured margin. Must be
     *        non-empty.
     * @param occupancy obstructing atoms as spheres; may be empty
     * @param origin explicit escape origin (e.g. a ligand centroid);
     *        never inferred
     */
    public EscapeRouteAnalysis analyze(
            List<Point3D> regionPoints,
            List<OccupancySphere> occupancy,
            Point3D origin
    ) {
        Objects.requireNonNull(regionPoints, "regionPoints");
        Objects.requireNonNull(occupancy, "occupancy");
        Objects.requireNonNull(origin, "origin");
        if (regionPoints.isEmpty()) {
            throw new IllegalArgumentException(
                    "regionPoints must not be empty");
        }

        double spacing = options.spacingAngstroms();
        double probe = options.probeRadiusAngstroms();
        double margin = options.regionMarginAngstroms();

        double[] min = {
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY
        };
        double[] max = {
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };
        for (Point3D point : regionPoints) {
            min[0] = Math.min(min[0], point.x());
            min[1] = Math.min(min[1], point.y());
            min[2] = Math.min(min[2], point.z());
            max[0] = Math.max(max[0], point.x());
            max[1] = Math.max(max[1], point.y());
            max[2] = Math.max(max[2], point.z());
        }
        double[] low = new double[3];
        double[] high = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            low[axis] = Math.floor((min[axis] - margin) / spacing) * spacing;
            high[axis] = Math.ceil((max[axis] + margin) / spacing) * spacing;
        }

        int nx = axisCount(low[0], high[0], spacing);
        int ny = axisCount(low[1], high[1], spacing);
        int nz = axisCount(low[2], high[2], spacing);
        int cellCount = nx * ny * nz;

        double[] clearance = clearanceField(low, spacing, nx, ny, nz, occupancy);

        boolean[] boundary = boundaryMask(nx, ny, nz);

        int originIndex = nearestCell(low, spacing, nx, ny, nz, origin);
        double originClearance = clearance[originIndex];

        boolean[] passable = new boolean[cellCount];
        for (int index = 0; index < cellCount; index++) {
            passable[index] = clearance[index] >= probe;
        }

        boolean[] reachable = new boolean[cellCount];
        int reachableCount = 0;
        boolean destinationReached = false;
        if (passable[originIndex]) {
            Deque<Integer> queue = new ArrayDeque<>();
            reachable[originIndex] = true;
            queue.add(originIndex);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                reachableCount++;
                if (boundary[current]) {
                    destinationReached = true;
                }
                for (int next : neighbors26(current, nx, ny, nz)) {
                    if (passable[next] && !reachable[next]) {
                        reachable[next] = true;
                        queue.addLast(next);
                    }
                }
            }
        }

        EscapeRouteClassification classification;
        if (!passable[originIndex]) {
            classification = EscapeRouteClassification.ORIGIN_OCCUPIED;
        } else if (destinationReached) {
            classification = EscapeRouteClassification.ESCAPE_ROUTE_EXISTS;
        } else {
            classification = EscapeRouteClassification.NO_ESCAPE_ROUTE;
        }

        WidestPath path =
                widestPath(clearance, boundary, nx, ny, nz, originIndex);
        int bottleneckIndex = path.cells()[0];
        List<Point3D> pathPoints = new ArrayList<>(path.cells().length);
        for (int index : path.cells()) {
            pathPoints.add(coordinate(low, spacing, ny, nz, index));
            if (clearance[index] < clearance[bottleneckIndex]) {
                bottleneckIndex = index;
            }
        }
        double bottleneck = path.bottleneckClearanceAngstroms();

        List<EscapeRouteComponent> components =
                components(passable, low, spacing, nx, ny, nz);

        double voxelVolume = spacing * spacing * spacing;
        return new EscapeRouteAnalysis(
                classification,
                coordinate(low, spacing, ny, nz, originIndex),
                originClearance,
                reachableCount,
                reachableCount * voxelVolume,
                bottleneck,
                coordinate(low, spacing, ny, nz, bottleneckIndex),
                pathPoints,
                components,
                options
        );
    }

    private static double[] clearanceField(
            double[] low,
            double spacing,
            int nx,
            int ny,
            int nz,
            List<OccupancySphere> occupancy
    ) {
        double[] clearance = new double[nx * ny * nz];
        Arrays.fill(clearance, Double.POSITIVE_INFINITY);
        if (occupancy.isEmpty()) {
            return clearance;
        }
        for (int ix = 0; ix < nx; ix++) {
            double x = low[0] + ix * spacing;
            for (int iy = 0; iy < ny; iy++) {
                double y = low[1] + iy * spacing;
                for (int iz = 0; iz < nz; iz++) {
                    double z = low[2] + iz * spacing;
                    double best = Double.POSITIVE_INFINITY;
                    for (OccupancySphere sphere : occupancy) {
                        double dx = sphere.center().x() - x;
                        double dy = sphere.center().y() - y;
                        double dz = sphere.center().z() - z;
                        double surface = Math.sqrt(dx * dx + dy * dy + dz * dz)
                                - sphere.radiusAngstroms();
                        if (surface < best) {
                            best = surface;
                        }
                    }
                    clearance[(ix * ny + iy) * nz + iz] = best;
                }
            }
        }
        return clearance;
    }

    private record WidestPath(
            int[] cells,
            double bottleneckClearanceAngstroms) {
    }

    /**
     * Maximum-bottleneck path to the first exterior boundary cell
     * popped; exact port of the stage8_11 {@code widest_path()},
     * including the 1e-12 capacity tolerance, the shortest-path
     * tie-break, and 6-connectivity. The bottleneck clearance is the
     * capacity of the final cell, i.e. the minimum clearance along
     * the path.
     */
    private static WidestPath widestPath(
            double[] clearance,
            boolean[] boundary,
            int nx,
            int ny,
            int nz,
            int start
    ) {
        int cellCount = nx * ny * nz;
        double[] capacity = new double[cellCount];
        Arrays.fill(capacity, Double.NEGATIVE_INFINITY);
        int[] steps = new int[cellCount];
        Arrays.fill(steps, Integer.MAX_VALUE);
        int[] parent = new int[cellCount];
        Arrays.fill(parent, -1);
        capacity[start] = clearance[start];
        steps[start] = 0;

        PriorityQueue<long[]> queue =
                new PriorityQueue<>(new HeapEntryOrder());
        queue.add(new long[]{Double.doubleToLongBits(-capacity[start]), 0, start});
        int end = -1;
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            double value = -Double.longBitsToDouble(entry[0]);
            int distance = (int) entry[1];
            int current = (int) entry[2];
            if (value < capacity[current] - CAPACITY_TOLERANCE
                    || distance != steps[current]) {
                continue;
            }
            if (boundary[current]) {
                end = current;
                break;
            }
            for (int next : neighbors6(current, nx, ny, nz)) {
                double proposed = Math.min(value, clearance[next]);
                int proposedSteps = distance + 1;
                if (proposed > capacity[next] + CAPACITY_TOLERANCE
                        || (Math.abs(proposed - capacity[next])
                                <= CAPACITY_TOLERANCE
                        && proposedSteps < steps[next])) {
                    capacity[next] = proposed;
                    steps[next] = proposedSteps;
                    parent[next] = current;
                    queue.add(new long[]{
                            Double.doubleToLongBits(-proposed),
                            proposedSteps,
                            next
                    });
                }
            }
        }
        if (end < 0) {
            throw new IllegalStateException(
                    "Grid has no boundary cell reachable from the origin");
        }

        List<Integer> reversed = new ArrayList<>();
        int current = end;
        while (current >= 0) {
            reversed.add(current);
            if (current == start) {
                break;
            }
            current = parent[current];
        }
        if (reversed.get(reversed.size() - 1) != start) {
            throw new IllegalStateException("Broken path parent chain");
        }
        int[] path = new int[reversed.size()];
        for (int index = 0; index < reversed.size(); index++) {
            path[index] = reversed.get(reversed.size() - 1 - index);
        }
        return new WidestPath(path, capacity[end]);
    }

    /**
     * 26-connected components of the passable mask, sorted by
     * descending size with the lowest flat index breaking ties
     * (stage8_11 {@code components()} ordering); labels are 1-based
     * in that order.
     */
    private static List<EscapeRouteComponent> components(
            boolean[] passable,
            double[] low,
            double spacing,
            int nx,
            int ny,
            int nz
    ) {
        boolean[] visited = new boolean[passable.length];
        List<int[]> groups = new ArrayList<>();
        for (int seed = 0; seed < passable.length; seed++) {
            if (!passable[seed] || visited[seed]) {
                continue;
            }
            List<Integer> members = new ArrayList<>();
            Deque<Integer> queue = new ArrayDeque<>();
            visited[seed] = true;
            queue.add(seed);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                members.add(current);
                for (int next : neighbors26(current, nx, ny, nz)) {
                    if (passable[next] && !visited[next]) {
                        visited[next] = true;
                        queue.addLast(next);
                    }
                }
            }
            int[] group = new int[members.size()];
            for (int index = 0; index < members.size(); index++) {
                group[index] = members.get(index);
            }
            groups.add(group);
        }
        groups.sort((left, right) -> {
            if (left.length != right.length) {
                return Integer.compare(right.length, left.length);
            }
            int leftMin = Integer.MAX_VALUE;
            for (int index : left) {
                leftMin = Math.min(leftMin, index);
            }
            int rightMin = Integer.MAX_VALUE;
            for (int index : right) {
                rightMin = Math.min(rightMin, index);
            }
            return Integer.compare(leftMin, rightMin);
        });

        double voxelVolume = spacing * spacing * spacing;
        List<EscapeRouteComponent> components = new ArrayList<>();
        for (int label = 0; label < groups.size(); label++) {
            int[] group = groups.get(label);
            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            for (int index : group) {
                Point3D point = coordinate(low, spacing, ny, nz, index);
                x += point.x();
                y += point.y();
                z += point.z();
            }
            components.add(new EscapeRouteComponent(
                    label + 1,
                    group.length,
                    group.length * voxelVolume,
                    new Point3D(
                            x / group.length,
                            y / group.length,
                            z / group.length)
            ));
        }
        return components;
    }

    private static boolean[] boundaryMask(int nx, int ny, int nz) {
        boolean[] boundary = new boolean[nx * ny * nz];
        for (int ix = 0; ix < nx; ix++) {
            for (int iy = 0; iy < ny; iy++) {
                for (int iz = 0; iz < nz; iz++) {
                    if (ix == 0 || ix == nx - 1
                            || iy == 0 || iy == ny - 1
                            || iz == 0 || iz == nz - 1) {
                        boundary[(ix * ny + iy) * nz + iz] = true;
                    }
                }
            }
        }
        return boundary;
    }

    /** Nearest grid cell; exact half-cell ties resolve downward. */
    private static int nearestCell(
            double[] low,
            double spacing,
            int nx,
            int ny,
            int nz,
            Point3D origin
    ) {
        int ix = nearestAxisIndex(low[0], spacing, nx, origin.x());
        int iy = nearestAxisIndex(low[1], spacing, ny, origin.y());
        int iz = nearestAxisIndex(low[2], spacing, nz, origin.z());
        return (ix * ny + iy) * nz + iz;
    }

    private static int nearestAxisIndex(
            double low,
            double spacing,
            int count,
            double value
    ) {
        int index = (int) Math.ceil((value - low) / spacing - 0.5);
        return Math.max(0, Math.min(count - 1, index));
    }

    private static Point3D coordinate(
            double[] low,
            double spacing,
            int ny,
            int nz,
            int index
    ) {
        int ix = index / (ny * nz);
        int remainder = index % (ny * nz);
        int iy = remainder / nz;
        int iz = remainder % nz;
        return new Point3D(
                low[0] + ix * spacing,
                low[1] + iy * spacing,
                low[2] + iz * spacing
        );
    }

    private static List<Integer> neighbors6(
            int index,
            int nx,
            int ny,
            int nz
    ) {
        List<Integer> neighbors = new ArrayList<>(6);
        int ix = index / (ny * nz);
        int remainder = index % (ny * nz);
        int iy = remainder / nz;
        int iz = remainder % nz;
        if (ix > 0) {
            neighbors.add(index - ny * nz);
        }
        if (ix + 1 < nx) {
            neighbors.add(index + ny * nz);
        }
        if (iy > 0) {
            neighbors.add(index - nz);
        }
        if (iy + 1 < ny) {
            neighbors.add(index + nz);
        }
        if (iz > 0) {
            neighbors.add(index - 1);
        }
        if (iz + 1 < nz) {
            neighbors.add(index + 1);
        }
        return neighbors;
    }

    private static List<Integer> neighbors26(
            int index,
            int nx,
            int ny,
            int nz
    ) {
        List<Integer> neighbors = new ArrayList<>(26);
        int ix = index / (ny * nz);
        int remainder = index % (ny * nz);
        int iy = remainder / nz;
        int iz = remainder % nz;
        for (int dx = -1; dx <= 1; dx++) {
            int px = ix + dx;
            if (px < 0 || px >= nx) {
                continue;
            }
            for (int dy = -1; dy <= 1; dy++) {
                int py = iy + dy;
                if (py < 0 || py >= ny) {
                    continue;
                }
                for (int dz = -1; dz <= 1; dz++) {
                    int pz = iz + dz;
                    if (pz < 0 || pz >= nz || (dx == 0 && dy == 0 && dz == 0)) {
                        continue;
                    }
                    neighbors.add((px * ny + py) * nz + pz);
                }
            }
        }
        return neighbors;
    }

    /**
     * Number of grid points on one axis, replicating numpy
     * {@code arange(low, high + spacing / 2, spacing)}.
     */
    private static int axisCount(double low, double high, double spacing) {
        double limit = high + spacing / 2.0;
        int count = 0;
        while (low + count * spacing < limit) {
            count++;
        }
        return count;
    }

    /**
     * Heap ordering identical to the Python tuple
     * {@code (-capacity, steps, index)}.
     */
    private static final class HeapEntryOrder implements Comparator<long[]> {
        @Override
        public int compare(long[] left, long[] right) {
            int byCapacity = Double.compare(
                    Double.longBitsToDouble(left[0]),
                    Double.longBitsToDouble(right[0])
            );
            if (byCapacity != 0) {
                return byCapacity;
            }
            if (left[1] != right[1]) {
                return Long.compare(left[1], right[1]);
            }
            return Long.compare(left[2], right[2]);
        }
    }
}
