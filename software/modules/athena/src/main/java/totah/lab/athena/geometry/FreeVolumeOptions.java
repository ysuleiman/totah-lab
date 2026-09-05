package totah.lab.athena.geometry;

import java.util.Objects;

/**
 * Parameters for {@link GridVolume#localFreeVolume}. The defaults
 * reproduce the historical one-off analyses
 * ({@code analysis/dcmb/dcmb_tsl_interference/analyze_interference.py}
 * local_cavity_volume, {@code analysis/mettl7-closure/stage4/
 * analyze_dcmb_campaign.py} accessible_volume,
 * {@code analysis/dcmb/displacement_field_analysis.py}
 * ligand_volume): a 0.5 A grid, a +/-3.0 A padding around the
 * reference atom set, and a 2.0 A clearance cutoff.
 */
public record FreeVolumeOptions(
        double spacingAngstroms,
        double paddingAngstroms,
        double clearanceAngstroms,
        String provenance) {

    public FreeVolumeOptions {
        requireFinitePositive(spacingAngstroms, "spacingAngstroms");
        requireFinitePositive(paddingAngstroms, "paddingAngstroms");
        requireFinitePositive(clearanceAngstroms, "clearanceAngstroms");
        Objects.requireNonNull(provenance, "provenance");
        provenance = provenance.trim();
        if (provenance.isEmpty()) {
            throw new IllegalArgumentException("provenance must not be blank");
        }
    }

    public static FreeVolumeOptions defaults() {
        return new FreeVolumeOptions(
                0.5,
                3.0,
                2.0,
                "analysis/dcmb/dcmb_tsl_interference/analyze_interference.py"
                        + " local_cavity_volume: 0.5 A grid, +/-3.0 A ligand"
                        + " padding, 2.0 A protein clearance"
        );
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and positive");
        }
    }
}
