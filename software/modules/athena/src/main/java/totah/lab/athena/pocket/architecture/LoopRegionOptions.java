package totah.lab.athena.pocket.architecture;

import totah.lab.athena.ligand.contact.DefaultContactAnalyzer;

/**
 * Thresholds for {@link LoopRegionAnalyzer}. All values are
 * calibration-pending geometric conventions, documented on
 * {@link LoopRegionAnalysis}.
 *
 * <p>Defaults: residue range 225-236 (the METTL7 loop region under
 * investigation); contact cutoff reuses the contact analyzer's
 * direct-contact convention
 * ({@link DefaultContactAnalyzer#DEFAULT_CONTACT_CUTOFF_ANGSTROMS});
 * burial proxy radius 8 A; sphere-locality cutoff 6 A; free-volume
 * probe 1.4 A; toward/away significance 1.0 A.
 */
public record LoopRegionOptions(
        int rangeStart,
        int rangeEnd,
        double contactCutoffAngstroms,
        double burialRadiusAngstroms,
        double sphereLocalityCutoffAngstroms,
        double probeRadiusAngstroms,
        double towardLoopSignificanceAngstroms
) {

    public LoopRegionOptions {
        if (rangeStart < 0 || rangeEnd < rangeStart) {
            throw new IllegalArgumentException(
                    "Range must satisfy 0 <= rangeStart <= rangeEnd"
            );
        }

        if (!Double.isFinite(contactCutoffAngstroms)
                || contactCutoffAngstroms <= 0.0
                || !Double.isFinite(burialRadiusAngstroms)
                || burialRadiusAngstroms <= 0.0
                || !Double.isFinite(sphereLocalityCutoffAngstroms)
                || sphereLocalityCutoffAngstroms <= 0.0
                || !Double.isFinite(probeRadiusAngstroms)
                || probeRadiusAngstroms <= 0.0
                || !Double.isFinite(towardLoopSignificanceAngstroms)
                || towardLoopSignificanceAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "Thresholds must be finite and positive "
                            + "(significance non-negative)"
            );
        }
    }

    public static LoopRegionOptions defaults() {
        return new LoopRegionOptions(
                225,
                236,
                DefaultContactAnalyzer.DEFAULT_CONTACT_CUTOFF_ANGSTROMS,
                8.0,
                6.0,
                1.4,
                1.0
        );
    }
}
