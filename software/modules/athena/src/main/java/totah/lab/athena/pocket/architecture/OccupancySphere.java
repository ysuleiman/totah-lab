package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/**
 * One obstructing atom for {@link EscapeRouteAnalyzer}, expressed as
 * a center plus van der Waals radius so the analyzer stays
 * atom-agnostic. The stage8_11 protocol radii
 * ({@code analysis/mettl7-closure/stage8_11_design/PROTOCOL.json})
 * are C 1.7, N 1.55, O 1.52, S 1.8, P 1.8, F 1.47, CL 1.75,
 * BR 1.85, I 1.98 A, defaulting to 1.7 A for any other element.
 */
public record OccupancySphere(
        Point3D center,
        double radiusAngstroms) {

    public OccupancySphere {
        Objects.requireNonNull(center, "center");
        if (!Double.isFinite(radiusAngstroms) || radiusAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "radiusAngstroms must be finite and non-negative");
        }
    }
}
