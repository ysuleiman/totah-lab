package totah.lab.pocket.visualization.surface;

import totah.lab.protein.Point3D;

import java.util.List;

public record TriangleMesh(List<Point3D> vertices, int[] indices) {
    public TriangleMesh {
        vertices = List.copyOf(vertices);
        indices = indices.clone();
        if (indices.length % 3 != 0) {
            throw new IllegalArgumentException(
                    "Triangle indices must be a multiple of three");
        }
    }

    @Override
    public int[] indices() {
        return indices.clone();
    }

    public int triangleCount() {
        return indices.length / 3;
    }
}
