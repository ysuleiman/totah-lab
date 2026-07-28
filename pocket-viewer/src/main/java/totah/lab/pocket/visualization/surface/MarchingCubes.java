package totah.lab.pocket.visualization.surface;

import totah.lab.protein.Point3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Isosurface extraction using a six-tetrahedra decomposition per grid cube.
 * This avoids ambiguous cube cases while producing the same continuous
 * triangle surface expected from marching cubes.
 */
public final class MarchingCubes {
    private static final int[][] CUBE_CORNERS = {
            {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0},
            {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}
    };
    private static final int[][] TETRAHEDRA = {
            {0, 5, 1, 6}, {0, 1, 2, 6}, {0, 2, 3, 6},
            {0, 3, 7, 6}, {0, 7, 4, 6}, {0, 4, 5, 6}
    };

    private MarchingCubes() {
    }

    public static TriangleMesh extract(PocketField field, double isoValue) {
        List<Point3D> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        Map<VertexKey, Integer> vertexIndex = new HashMap<>();
        Point3D[] points = new Point3D[8];
        double[] values = new double[8];

        for (int z = 0; z < field.sizeZ() - 1; z++) {
            for (int y = 0; y < field.sizeY() - 1; y++) {
                for (int x = 0; x < field.sizeX() - 1; x++) {
                    for (int corner = 0; corner < 8; corner++) {
                        int[] offset = CUBE_CORNERS[corner];
                        int px = x + offset[0];
                        int py = y + offset[1];
                        int pz = z + offset[2];
                        points[corner] = field.point(px, py, pz);
                        values[corner] = field.value(px, py, pz);
                    }
                    for (int[] tetrahedron : TETRAHEDRA) {
                        polygonise(
                                tetrahedron, points, values, isoValue,
                                vertices, indices, vertexIndex);
                    }
                }
            }
        }

        int[] indexArray = indices.stream()
                .mapToInt(Integer::intValue)
                .toArray();
        return new TriangleMesh(vertices, indexArray);
    }

    private static void polygonise(
            int[] tetrahedron,
            Point3D[] points,
            double[] values,
            double isoValue,
            List<Point3D> vertices,
            List<Integer> indices,
            Map<VertexKey, Integer> vertexIndex) {
        int[] inside = new int[4];
        int[] outside = new int[4];
        int insideCount = 0;
        int outsideCount = 0;
        for (int vertex : tetrahedron) {
            if (values[vertex] >= isoValue) {
                inside[insideCount++] = vertex;
            } else {
                outside[outsideCount++] = vertex;
            }
        }
        if (insideCount == 0 || insideCount == 4) {
            return;
        }

        if (insideCount == 1) {
            Point3D a = interpolate(
                    inside[0], outside[0], points, values, isoValue);
            Point3D b = interpolate(
                    inside[0], outside[1], points, values, isoValue);
            Point3D c = interpolate(
                    inside[0], outside[2], points, values, isoValue);
            addTriangle(a, b, c, vertices, indices, vertexIndex);
            return;
        }
        if (insideCount == 3) {
            Point3D a = interpolate(
                    outside[0], inside[0], points, values, isoValue);
            Point3D b = interpolate(
                    outside[0], inside[1], points, values, isoValue);
            Point3D c = interpolate(
                    outside[0], inside[2], points, values, isoValue);
            addTriangle(a, c, b, vertices, indices, vertexIndex);
            return;
        }

        Point3D a = interpolate(
                inside[0], outside[0], points, values, isoValue);
        Point3D b = interpolate(
                inside[0], outside[1], points, values, isoValue);
        Point3D c = interpolate(
                inside[1], outside[0], points, values, isoValue);
        Point3D d = interpolate(
                inside[1], outside[1], points, values, isoValue);
        addTriangle(a, b, c, vertices, indices, vertexIndex);
        addTriangle(b, d, c, vertices, indices, vertexIndex);
    }

    private static Point3D interpolate(
            int first,
            int second,
            Point3D[] points,
            double[] values,
            double isoValue) {
        double denominator = values[second] - values[first];
        double fraction = Math.abs(denominator) < 1.0e-12
                ? 0.5
                : (isoValue - values[first]) / denominator;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        Point3D a = points[first];
        Point3D b = points[second];
        return new Point3D(
                a.x() + fraction * (b.x() - a.x()),
                a.y() + fraction * (b.y() - a.y()),
                a.z() + fraction * (b.z() - a.z()));
    }

    private static void addTriangle(
            Point3D a,
            Point3D b,
            Point3D c,
            List<Point3D> vertices,
            List<Integer> indices,
            Map<VertexKey, Integer> vertexIndex) {
        indices.add(indexOf(a, vertices, vertexIndex));
        indices.add(indexOf(b, vertices, vertexIndex));
        indices.add(indexOf(c, vertices, vertexIndex));
    }

    private static int indexOf(
            Point3D point,
            List<Point3D> vertices,
            Map<VertexKey, Integer> vertexIndex) {
        VertexKey key = VertexKey.from(point);
        Integer existing = vertexIndex.get(key);
        if (existing != null) {
            return existing;
        }
        int index = vertices.size();
        vertices.add(point);
        vertexIndex.put(key, index);
        return index;
    }

    private record VertexKey(long x, long y, long z) {
        private static final double SCALE = 1_000_000.0;

        static VertexKey from(Point3D point) {
            return new VertexKey(
                    Math.round(point.x() * SCALE),
                    Math.round(point.y() * SCALE),
                    Math.round(point.z() * SCALE));
        }
    }
}
