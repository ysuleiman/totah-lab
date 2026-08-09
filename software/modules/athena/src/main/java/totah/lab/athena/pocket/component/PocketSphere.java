package totah.lab.athena.pocket.component;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

public record PocketSphere(Point3D center, double radius) {
    public PocketSphere {
        Objects.requireNonNull(center);
        if (!Double.isFinite(radius) || radius <= 0) {
            throw new IllegalArgumentException("radius must be finite and positive");
        }
    }
}
