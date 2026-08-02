package totah.lab.gaia.pocket;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

public record AlphaSphere(
        long id,
        Point3D center,
        double radius) {

    public AlphaSphere {
        Objects.requireNonNull(center, "center");
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException(
                    "Alpha-sphere radius must be finite and positive.");
        }
    }
}
