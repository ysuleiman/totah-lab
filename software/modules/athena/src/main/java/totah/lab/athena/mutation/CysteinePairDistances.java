package totah.lab.athena.mutation;

/**
 * Distances between corresponding atoms of two cysteines, in angstroms.
 *
 * <p>Also used to express per-component deltas (mutant minus parent) in
 * {@link CysteineGeometryChange}; delta components may be negative.</p>
 *
 * @param sgSgDistance distance between the two SG (gamma sulfur) atoms
 * @param caCaDistance distance between the two CA (alpha carbon) atoms
 * @param cbCbDistance distance between the two CB (beta carbon) atoms
 */
public record CysteinePairDistances(
        double sgSgDistance,
        double caCaDistance,
        double cbCbDistance) {

    public CysteinePairDistances {
        requireFinite(sgSgDistance, "sgSgDistance");
        requireFinite(caCaDistance, "caCaDistance");
        requireFinite(cbCbDistance, "cbCbDistance");
    }

    /**
     * Returns the component-wise difference {@code mutant - parent}.
     */
    static CysteinePairDistances delta(
            CysteinePairDistances parent,
            CysteinePairDistances mutant) {

        return new CysteinePairDistances(
                mutant.sgSgDistance() - parent.sgSgDistance(),
                mutant.caCaDistance() - parent.caCaDistance(),
                mutant.cbCbDistance() - parent.cbCbDistance());
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite: " + value);
        }
    }
}
