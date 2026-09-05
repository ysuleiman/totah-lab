package totah.lab.athena.geometry;

import java.util.Objects;

/**
 * Parameters for {@link GridVolume#envelopeVolume} and
 * {@link GridVolume#sharedEnvelopeVolume}. The defaults reproduce the
 * historical one-off analyses
 * ({@code analysis/dcmb/dcmb_tsl_interference/analyze_interference.py}
 * shared_volume and its frozen copy in {@code analysis/mettl7-closure/
 * stage4/analyze_dcmb_campaign.py}): a 0.5 A grid and a 1.7 A
 * per-atom envelope radius (heavy-atom van der Waals envelope).
 */
public record EnvelopeOptions(
        double spacingAngstroms,
        double envelopeRadiusAngstroms,
        String provenance) {

    public EnvelopeOptions {
        requireFinitePositive(spacingAngstroms, "spacingAngstroms");
        requireFinitePositive(
                envelopeRadiusAngstroms, "envelopeRadiusAngstroms");
        Objects.requireNonNull(provenance, "provenance");
        provenance = provenance.trim();
        if (provenance.isEmpty()) {
            throw new IllegalArgumentException("provenance must not be blank");
        }
    }

    public static EnvelopeOptions defaults() {
        return new EnvelopeOptions(
                0.5,
                1.7,
                "analysis/dcmb/dcmb_tsl_interference/analyze_interference.py"
                        + " shared_volume: 0.5 A grid, 1.7 A heavy-atom"
                        + " envelope radius"
        );
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and positive");
        }
    }
}
