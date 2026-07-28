package totah.lab.pocket.visualization.control;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Projects residue positions into GUI coordinates so labels remain readable
 * and screen-sized while the molecular scene rotates and zooms.
 */
public final class ResidueLabelController {
    private final Camera camera;
    private final Point3D sceneCenter;
    private final Map<BitmapText, Vector3f> labels = new LinkedHashMap<>();
    private boolean enabled;

    public ResidueLabelController(
            Camera camera,
            Node guiNode,
            BitmapFont font,
            List<Residue> residues,
            Point3D sceneCenter) {
        this.camera = Objects.requireNonNull(camera, "camera");
        this.sceneCenter = Objects.requireNonNull(
                sceneCenter, "sceneCenter");
        for (Residue residue : residues) {
            Point3D position = residue.getAlphaCarbonPosition();
            if (position == null) {
                continue;
            }
            BitmapText label = new BitmapText(font);
            label.setText("%s %s%d".formatted(
                    residue.getChain(),
                    residue.getName(),
                    residue.getNumber()));
            label.setColor(new ColorRGBA(1.0f, 0.92f, 0.35f, 1.0f));
            label.setSize(16.0f);
            label.setCullHint(Spatial.CullHint.Always);
            guiNode.attachChild(label);
            labels.put(label, relative(position));
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            labels.keySet().forEach(label ->
                    label.setCullHint(Spatial.CullHint.Always));
        }
    }

    public void setSize(float size) {
        labels.keySet().forEach(label -> label.setSize(size));
    }

    public void update() {
        if (!enabled) {
            return;
        }
        labels.forEach((label, worldPosition) -> {
            Vector3f screen = camera.getScreenCoordinates(worldPosition);
            boolean visible = screen.z >= 0.0f && screen.z <= 1.0f;
            label.setCullHint(visible
                    ? Spatial.CullHint.Never
                    : Spatial.CullHint.Always);
            if (visible) {
                label.setLocalTranslation(
                        screen.x - label.getLineWidth() * 0.5f,
                        screen.y + label.getLineHeight() * 0.5f,
                        1.0f);
            }
        });
    }

    public void dispose() {
        labels.keySet().forEach(Spatial::removeFromParent);
        labels.clear();
    }

    private Vector3f relative(Point3D point) {
        return new Vector3f(
                (float) (point.x() - sceneCenter.x()),
                (float) (point.y() - sceneCenter.y()),
                (float) (point.z() - sceneCenter.z()));
    }
}
