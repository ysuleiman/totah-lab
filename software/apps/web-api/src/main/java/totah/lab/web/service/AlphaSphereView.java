package totah.lab.web.service;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/**
 * One persisted fpocket alpha sphere of a pocket, for the inspection UI.
 * {@code index} is the parser order ({@code sphere_index} column); center
 * and radius are exactly the values parsed from the pocket's
 * {@code pocketN_vert.pqr} file — radii are never fabricated.
 */
public record AlphaSphereView(
        int index,
        Point3D center,
        double radius
) {
    public AlphaSphereView {
        Objects.requireNonNull(center, "center");
    }
}
