package totah.lab.pocket.visualization.scene;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import totah.lab.protein.Point3D;
import totah.lab.pocket.visualization.surface.TriangleMesh;

import java.util.List;
import java.util.Objects;

public final class PocketMeshBuilder {
    private PocketMeshBuilder() {
    }

    public static Geometry createGeometry(
            TriangleMesh surface,
            Point3D sceneCenter,
            AssetManager assetManager,
            ColorRGBA color) {
        Objects.requireNonNull(surface, "surface");
        List<Point3D> vertices = surface.vertices();
        int[] indices = surface.indices();
        Vector3f[] positions = new Vector3f[vertices.size()];
        Vector3f[] normals = new Vector3f[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            Point3D point = vertices.get(i);
            positions[i] = new Vector3f(
                    (float) (point.x() - sceneCenter.x()),
                    (float) (point.y() - sceneCenter.y()),
                    (float) (point.z() - sceneCenter.z()));
            normals[i] = new Vector3f();
        }

        for (int i = 0; i < indices.length; i += 3) {
            int first = indices[i];
            int second = indices[i + 1];
            int third = indices[i + 2];
            Vector3f normal = positions[second].subtract(positions[first])
                    .cross(positions[third].subtract(positions[first]));
            if (normal.lengthSquared() > 1.0e-10f) {
                normals[first].addLocal(normal);
                normals[second].addLocal(normal);
                normals[third].addLocal(normal);
            }
        }
        for (Vector3f normal : normals) {
            if (normal.lengthSquared() > 1.0e-10f) {
                normal.normalizeLocal();
            } else {
                normal.set(Vector3f.UNIT_Y);
            }
        }

        Mesh mesh = new Mesh();
        mesh.setBuffer(
                VertexBuffer.Type.Position,
                3,
                BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(
                VertexBuffer.Type.Normal,
                3,
                BufferUtils.createFloatBuffer(normals));
        mesh.setBuffer(
                VertexBuffer.Type.Index,
                3,
                BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        mesh.updateCounts();

        Geometry geometry = new Geometry("Pocket surface", mesh);
        Material material = new Material(
                assetManager,
                "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", color);
        material.setColor("Ambient", color.mult(0.7f));
        material.setColor("Specular", ColorRGBA.White.mult(0.35f));
        material.setFloat("Shininess", 24.0f);
        material.getAdditionalRenderState().setBlendMode(
                RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        material.getAdditionalRenderState().setFaceCullMode(
                RenderState.FaceCullMode.Off);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        geometry.setMaterial(material);
        geometry.setUserData("selectionLabel", "Pocket boundary");
        return geometry;
    }
}
