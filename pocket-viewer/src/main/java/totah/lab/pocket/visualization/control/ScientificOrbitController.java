package totah.lab.pocket.visualization.control;

import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;

import java.util.Objects;

/**
 * Direct, inertia-free orbit camera for inspecting a stationary 3D structure.
 */
public final class ScientificOrbitController {
    private static final String ORBIT = "scientific-orbit";
    private static final String ZOOM_IN = "scientific-zoom-in";
    private static final String ZOOM_OUT = "scientific-zoom-out";
    private static final float RADIANS_PER_PIXEL = 0.0075f;

    private final Camera camera;
    private final Spatial target;
    private final InputManager input;
    private final Vector2f previousCursor = new Vector2f();
    private final Vector3f previousTarget = new Vector3f(
            Float.NaN, Float.NaN, Float.NaN);
    private float yaw;
    private float pitch = 0.35f;
    private float distance = 28.0f;
    private boolean orbiting;

    public ScientificOrbitController(
            Camera camera,
            Spatial target,
            InputManager input) {
        this.camera = Objects.requireNonNull(camera, "camera");
        this.target = Objects.requireNonNull(target, "target");
        this.input = Objects.requireNonNull(input, "input");
        registerInput();
        updateCamera();
    }

    public void update() {
        Vector3f targetPosition = target.getWorldTranslation();
        if (!targetPosition.equals(previousTarget)) {
            updateCamera();
        }
        if (!orbiting) {
            return;
        }
        Vector2f cursor = input.getCursorPosition();
        float deltaX = cursor.x - previousCursor.x;
        float deltaY = cursor.y - previousCursor.y;
        previousCursor.set(cursor);
        if (deltaX == 0.0f && deltaY == 0.0f) {
            return;
        }
        yaw -= deltaX * RADIANS_PER_PIXEL;
        pitch = FastMath.clamp(
                pitch + deltaY * RADIANS_PER_PIXEL,
                -FastMath.HALF_PI + 0.02f,
                FastMath.HALF_PI - 0.02f);
        updateCamera();
    }

    private void registerInput() {
        input.addMapping(
                ORBIT,
                new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        input.addMapping(
                ZOOM_IN,
                new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        input.addMapping(
                ZOOM_OUT,
                new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        input.addListener(
                (ActionListener) (name, pressed, timePerFrame) -> {
                    if (ORBIT.equals(name)) {
                        orbiting = pressed;
                        previousCursor.set(input.getCursorPosition());
                    }
                },
                ORBIT);
        input.addListener(
                (AnalogListener) (name, value, timePerFrame) -> {
                    float factor = Math.max(0.82f, 1.0f - value * 8.0f);
                    if (ZOOM_IN.equals(name)) {
                        distance *= factor;
                    } else if (ZOOM_OUT.equals(name)) {
                        distance /= factor;
                    }
                    distance = FastMath.clamp(distance, 3.0f, 100.0f);
                    updateCamera();
                },
                ZOOM_IN,
                ZOOM_OUT);
    }

    private void updateCamera() {
        Vector3f center = target.getWorldTranslation();
        previousTarget.set(center);
        float horizontalDistance = distance * FastMath.cos(pitch);
        Vector3f offset = new Vector3f(
                horizontalDistance * FastMath.sin(yaw),
                distance * FastMath.sin(pitch),
                horizontalDistance * FastMath.cos(yaw));
        camera.setLocation(center.add(offset));
        camera.lookAt(center, Vector3f.UNIT_Y);
    }
}
