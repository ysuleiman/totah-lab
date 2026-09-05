package totah.lab.athena.pocket.architecture;

import java.util.Objects;

/**
 * Parameters for {@link EscapeRouteAnalyzer}. The defaults reproduce
 * the stage8_11 structural-design protocol
 * ({@code analysis/mettl7-closure/stage8_11_design/PROTOCOL.json}):
 * a 0.5 A grid, a 1.7 A ligand heavy-atom probe, and an 8.0 A
 * exterior margin around the pocket-region points.
 */
public record EscapeRouteOptions(
        double spacingAngstroms,
        double probeRadiusAngstroms,
        double regionMarginAngstroms,
        String provenance) {

    public EscapeRouteOptions {
        requireFinitePositive(spacingAngstroms, "spacingAngstroms");
        requireFinitePositive(probeRadiusAngstroms, "probeRadiusAngstroms");
        requireFinitePositive(regionMarginAngstroms, "regionMarginAngstroms");
        Objects.requireNonNull(provenance, "provenance");
        provenance = provenance.trim();
        if (provenance.isEmpty()) {
            throw new IllegalArgumentException("provenance must not be blank");
        }
    }

    public static EscapeRouteOptions defaults() {
        return new EscapeRouteOptions(
                0.5,
                1.7,
                8.0,
                "analysis/mettl7-closure/stage8_11_design/PROTOCOL.json:"
                        + " grid.spacing_A=0.5,"
                        + " clearance.ligand_heavy_atom_probe_A=1.7,"
                        + " grid domain = region bounds + 8.0 A exterior"
                        + " margin"
        );
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and positive");
        }
    }
}
