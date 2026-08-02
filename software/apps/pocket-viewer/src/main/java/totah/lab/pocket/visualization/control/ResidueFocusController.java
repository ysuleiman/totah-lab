package totah.lab.pocket.visualization.control;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;

import java.util.Objects;

/**
 * Provides a temporary, representation-independent visual confirmation when
 * residue search succeeds.
 */
public final class ResidueFocusController {
    private static final float PULSE_DURATION_SECONDS = 4.0f;
    private static final float PULSES_PER_SECOND = 2.2f;

    private final Geometry marker;
    private float elapsed = PULSE_DURATION_SECONDS;

    public ResidueFocusController(
            Node sceneRoot,
            AssetManager assetManager) {
        Objects.requireNonNull(sceneRoot, "sceneRoot");
        marker = new Geometry(
                "residue-search-result",
                new Sphere(18, 28, 0.75f));
        Material material = new Material(
                Objects.requireNonNull(assetManager, "assetManager"),
                "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor(
                "Color",
                new ColorRGBA(1.0f, 0.15f, 0.05f, 1.0f));
        marker.setMaterial(material);
        marker.setCullHint(Spatial.CullHint.Always);
        sceneRoot.attachChild(marker);
    }

    public void focus(Vector3f position) {
        marker.setLocalTranslation(position);
        marker.setCullHint(Spatial.CullHint.Never);
        elapsed = 0.0f;
    }

    public void update(float timePerFrame) {
        if (elapsed >= PULSE_DURATION_SECONDS) {
            marker.setCullHint(Spatial.CullHint.Always);
            return;
        }
        elapsed += timePerFrame;
        double wave = 0.5 + 0.5 * Math.sin(
                elapsed * Math.PI * 2.0 * PULSES_PER_SECOND);
        float scale = (float) (0.45 + wave * 1.15);
        marker.setLocalScale(scale);
    }
}
